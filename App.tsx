import React, {useEffect, useRef, useState} from 'react';
import {AppState, Linking, PermissionsAndroid, Platform, Pressable, SafeAreaView, ScrollView, StatusBar, StyleSheet, Switch, Text, TextInput, View} from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import {Camera, useCameraDevice, useCameraFormat} from 'react-native-vision-camera';
import {Sonkkeut, useSonkkeut} from 'react-native-sonkkeut';
import type {GuidanceEvent, ModelDownloadProgress, SpeechModelStatus} from 'react-native-sonkkeut';
import type {Action, MenuItem} from './src/domain';
import {isReadableElement, OrderFlow, parseOrder, screenReading} from './src/domain';
import {clearUsage, DEFAULT_MENU, enqueueUsage, flushUsage, validateBaseUrl} from './src/backend';
import type {Step} from './src/backend';
import {APP_VERSION, BACKEND_URL, DEFAULT_STORE_CODE, MODEL_VERSION} from './src/config';
import {restoreSettings} from './src/settings';
import {useBackendConnection} from './src/useBackendConnection';
import type {ServerConfig} from './src/useBackendConnection';
import {orderProgress, readableRows, targetOverlay, visualGuidance} from './src/guidance';
import {BrandMark, KioskArtwork} from './src/Artwork';
import {colors, styles} from './src/theme';

const labels = {S0: '준비', S1: '화면 탐색', S2: '화면 인식', S3: '주문 입력', S4: '손끝 유도', S5: '결과 확인', S6: '안내 완료', SE: '다시 확인'};
function Button({title, onPress, disabled = false, secondary = false}: {title: string; onPress: () => void; disabled?: boolean; secondary?: boolean}) {
  return <Pressable accessibilityRole="button" accessibilityLabel={title} accessibilityState={{disabled}} disabled={disabled} onPress={onPress} style={({pressed}) => [styles.button, secondary && styles.secondaryButton, disabled && styles.disabled, pressed && styles.pressed]}><Text style={[styles.buttonText, secondary && styles.secondaryText]}>{title}</Text></Pressable>;
}

export default function App() {
  const flow = useRef(new OrderFlow()).current;
  const [, redraw] = useState(0);
  const [running, setRunning] = useState(false);
  const [permission, setPermission] = useState(false);
  const [foreground, setForeground] = useState(true);
  const [paused, setPaused] = useState(false);
  const [order, setOrder] = useState('');
  const [listening, setListening] = useState(false);
  const [speechModel, setSpeechModel] = useState<SpeechModelStatus>();
  const [modelDownloading, setModelDownloading] = useState(false);
  const [modelPreparing, setModelPreparing] = useState(false);
  const [modelProgress, setModelProgress] = useState<ModelDownloadProgress>();
  const [modelMessage, setModelMessage] = useState('자체 음성 모델 상태를 확인하고 있습니다');
  const modelMounted = useRef(true);
  const [server, setServer] = useState(BACKEND_URL);
  const [code, setCode] = useState(DEFAULT_STORE_CODE);
  const [menu, setMenu] = useState<MenuItem[]>(DEFAULT_MENU);
  const [serverConfig, setServerConfig] = useState<ServerConfig>();
  const {connection, reconnect} = useBackendConnection(serverConfig, foreground);
  const connectionMatches = connection.configKey === JSON.stringify([serverConfig?.server, serverConfig?.code]);
  const [guidance, setGuidance] = useState<{action: Action; event: GuidanceEvent}>();
  const [captions, setCaptions] = useState<string[]>([]);
  const [verification, setVerification] = useState<{text: string; success: boolean}>();
  const [reader, setReader] = useState(false);
  const [cameraError, setCameraError] = useState(false);
  const [statsEnabled, setStatsEnabled] = useState(false);
  const statsEnabledRef = useRef(statsEnabled);
  statsEnabledRef.current = statsEnabled;
  const [lowVision, setLowVision] = useState(true);
  const [preview, setPreview] = useState({width: 1, height: 1});
  const [settings, setSettings] = useState(false);
  const [page, setPage] = useState<'home' | 'settings'>('home');
  const [speechSettings, setSpeechSettings] = useState(false);
  const [storeCode, setStoreCode] = useState<string>();
  const startedAt = useRef(Date.now());
  const steps = useRef<Step[]>([]);
  const applied = useRef<Action>();
  const stepStart = useRef(0);
  const reached = useRef<number>();
  const hints = useRef(0);
  const recorded = useRef(false);
  const announced = useRef('');
  const generation = useRef(0);
  const sessionConfig = useRef<{server: string; storeCode?: string}>({server: BACKEND_URL});
  if (!running) {sessionConfig.current = {server: serverConfig?.server ?? BACKEND_URL, storeCode: connectionMatches ? storeCode : undefined};}
  const menuSource = useRef<'default' | 'detected' | 'server'>('default');
  const appliedMenuVersion = useRef<number>();
  const screenSeen = useRef(false);
  const stepScreen = useRef('unknown');
  const device = useCameraDevice('back');
  const format = useCameraFormat(device, [{videoResolution: {width: 1280, height: 720}}, {fps: 15}]);
  const refresh = () => redraw(n => n + 1);
  const caption = (text: string) => setCaptions(current => current[0] === text ? current : [text, ...current].slice(0, 5));
  const ai = useSonkkeut({active: running && permission && !paused && foreground && flow.state !== 'S6',
    onResult: result => {
      if (!running || flow.paused || flow.state === 'S6') {return;}
      if (result.found) {screenSeen.current = true; return;}
      if (!screenSeen.current) {return;}
      screenSeen.current = false; generation.current++; applied.current = undefined; setGuidance(undefined);
      setListening(false); Sonkkeut.cancelListening();
      Sonkkeut.clearTarget(); flow.screen = undefined;
      flow.enter(flow.confirmed ? 'SE' : 'S1', '화면을 놓쳤습니다. 손을 멈추고 휴대폰을 다시 화면 쪽으로 들어 주세요.'); refresh();
    },
    onScreen: screen => {
      if (!running || flow.paused) {return;}
      flow.acceptScreen(screen);
      if (!flow.intent && menuSource.current !== 'server' && screen.screen_type === 'menu') {
        const detected = screen.elements.filter(e => e.kind === 'menu' && e.text && isReadableElement(e))
          .map(e => ({name: e.text!.replace(/[\d,]+\s*원/g, '').trim(), price: e.price, aliases: [], sold_out: false}));
        if (detected.length) {
          menuSource.current = 'detected';
          setMenu(current => [...current.filter(item => !detected.some(found => found.name === item.name)), ...detected]);
        }
      }
      refresh();
    },
    onEvent: event => {
      if (!running || flow.paused) {return;}
      if (flow.state === 'S4' && flow.action && (!event.target_id || event.target_id === flow.action.target.id)) {
        setGuidance({action: flow.action, event});
        if (event.speak) {caption(event.speak);}
      }
      if (event.speak) {hints.current = Math.min(1000, hints.current + 1);}
      if (event.type === 'press' && flow.state === 'S4' && applied.current === flow.action && event.target_id === flow.action?.target.id) {
        reached.current = Math.max(0, Math.min(600, (Date.now() - stepStart.current) / 1000)); flow.press(); refresh();
      }
    },
    onVerdict: verdict => {
      if (!running || flow.paused || flow.state !== 'S5') {return;}
      const target = applied.current?.target;
      steps.current.push({screen_type: stepScreen.current, target_kind: target?.kind ?? 'unknown', result: verdict.result,
        reach_s: reached.current, hints: hints.current, fail_reason: verdict.result === 'success' ? undefined : verdict.reason.slice(0, 60)});
      applied.current = undefined;
      setGuidance(undefined); setVerification({text: `${target?.text || '목표 버튼'} · ${verdict.speak}`, success: verdict.result === 'success'}); caption(verdict.speak);
      flow.verdict(verdict); refresh();
    },
  });
  useEffect(() => {
    if (ai.ready) {Sonkkeut.say('손끝길을 시작합니다. 시작 버튼을 누르고 휴대폰을 화면 쪽으로 들어 주세요.');}
  }, [ai.ready]);

  useEffect(() => {
    const lifecycleGeneration = generation;
    modelMounted.current = true;
    const progress = Sonkkeut.addModelDownloadListener(value => {
      if (modelMounted.current) {setModelProgress(value);}
    });
    Sonkkeut.getSpeechModelStatus().then(async status => {
      if (!modelMounted.current) {return;}
      setSpeechModel(status);
      if (status.installed && !status.ready) {
        setModelPreparing(true);
        setModelMessage('자체 음성 모델을 준비하고 있습니다');
        status = await Sonkkeut.prepareSpeechModel();
        if (!modelMounted.current) {return;}
        setSpeechModel(status);
      }
      setModelMessage(status.ready ? '자체 음성 모델 준비 완료 · 기기 안에서 인식합니다' : '음성 주문을 사용하려면 모델을 한 번 내려받아 주세요');
    }).catch(() => {
      if (modelMounted.current) {setModelMessage('음성 모델을 준비하지 못했습니다. 다시 시도하거나 주문을 입력해 주세요');}
    }).finally(() => {
      if (modelMounted.current) {setModelPreparing(false);}
    });
    return () => {
      modelMounted.current = false; lifecycleGeneration.current++; progress();
      Sonkkeut.cancelListening(); Sonkkeut.cancelSpeechModelDownload();
    };
  }, []);

  useEffect(() => {
    const aliases: Record<string, string> = {};
    menu.forEach(item => [item.name, ...item.aliases].forEach(alias => {aliases[alias.replace(/\s/g, '')] = item.name;}));
    Sonkkeut.setMenuAliases(aliases);
  }, [menu]);

  useEffect(() => {
    let mounted = true;
    AsyncStorage.getItem('settings').then(raw => {
      if (!mounted) {return;}
      const saved = restoreSettings(raw ? JSON.parse(raw) : undefined);
      setServer(saved.server); setCode(saved.code); setStatsEnabled(saved.statsEnabled);
      setServerConfig({server: saved.server, code: saved.code});
      statsEnabledRef.current = saved.statsEnabled === true;
    }).catch(() => {if (mounted) {setServerConfig({server: BACKEND_URL, code: DEFAULT_STORE_CODE});}});
    const sub = AppState.addEventListener('change', state => {
      setForeground(state === 'active');
      if (state !== 'active') {generation.current++; flow.paused = true; setPaused(true); setListening(false); Sonkkeut.cancelListening(); Sonkkeut.silence();}
      else if (statsEnabledRef.current) {flushUsage().catch(() => {});}
    });
    return () => {mounted = false; sub.remove();};
  }, [flow]);

  useEffect(() => {
    if (!serverConfig || running) {return;}
    // A reconnect never changes the menu, aliases or statistics destination mid-order.
    if (connectionMatches && connection.menu) {
      menuSource.current = 'server'; setMenu(connection.menu.items); setStoreCode(connection.menu.store_code);
      appliedMenuVersion.current = connection.menu.menu_version;
    } else {
      menuSource.current = 'default'; setMenu(DEFAULT_MENU); setStoreCode(undefined);
      appliedMenuVersion.current = undefined;
    }
  }, [connection, connectionMatches, running, serverConfig]);

  useEffect(() => {
    if (connection.status === 'online' && statsEnabledRef.current) {flushUsage().catch(() => {});}
  }, [connection.status]);

  useEffect(() => {
    if (!running || paused || !foreground) {return;}
    if (flow.message !== announced.current) {
      announced.current = flow.message;
      caption(flow.message);
      if (flow.state !== 'S5') {Sonkkeut.say(flow.message);}
    }
    const action = flow.action;
    if (flow.state === 'S4' && action && action !== applied.current) {
      applied.current = action; stepStart.current = Date.now(); hints.current = 0; reached.current = undefined;
      stepScreen.current = ['start', 'method', 'menu', 'option', 'cart', 'payment', 'unknown'].includes(flow.screen?.screen_type ?? '') ? flow.screen!.screen_type : 'unknown';
      const token = ++generation.current;
      Sonkkeut.setTarget(action.target.id, action.expect).then(ok => {
        if (token !== generation.current || flow.paused || flow.action !== action) {return;}
        if (!ok) {flow.enter('SE', '화면이 바뀌었습니다. 다시 확인해 주세요.'); applied.current = undefined; refresh();}
      }).catch(() => {
        if (token !== generation.current || flow.paused || flow.action !== action) {return;}
        applied.current = undefined; flow.enter('SE', '목표 버튼을 연결하지 못했습니다.'); refresh();
      });
    }
    if (flow.state === 'SE' || flow.state === 'S6') {Sonkkeut.clearTarget();}
    if (flow.state === 'S6') {record(true);}
  });

  useEffect(() => {
    if (ai.result?.target_missing && running && !flow.paused && flow.state === 'S4') {
      generation.current++; applied.current = undefined; setGuidance(undefined); Sonkkeut.clearTarget(); flow.recover(); Sonkkeut.requestKeyframe(); refresh();
    }
  }, [ai.result?.target_missing, running, flow]);

  function record(completed: boolean) {
    if (recorded.current) {return;}
    recorded.current = true;
    if (!statsEnabledRef.current || !sessionConfig.current.server) {return;}
    try {
      const base = validateBaseUrl(sessionConfig.current.server);
      enqueueUsage(base, {store_code: sessionConfig.current.storeCode, app_version: APP_VERSION, model_version: MODEL_VERSION, completed,
        duration_s: Math.min(7200, (Date.now() - startedAt.current) / 1000), steps: steps.current.slice(0, 200),
        event_id: `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}-${Math.random().toString(36).slice(2)}`}).catch(() => {});
    } catch (_) {}
  }
  async function start() {
    if (Platform.OS !== 'android') {flow.enter('SE', '안드로이드 기기에서 실행해 주세요.'); refresh(); return;}
    const camera = await Camera.requestCameraPermission();
    await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.RECORD_AUDIO);
    if (camera !== 'granted') {
      flow.enter('SE', '카메라 권한이 필요합니다. 설정에서 권한을 허용해 주세요.'); Sonkkeut.say(flow.message); refresh(); return;
    }
    if (!ai.ready) {flow.enter('SE', ai.error || '모델을 준비하고 있습니다. 잠시 후 다시 시작해 주세요.'); refresh(); return;}
    setPermission(true); setRunning(true); setSettings(false); setPaused(false); setCameraError(false); setReader(false); setGuidance(undefined); setVerification(undefined); setCaptions([]); flow.paused = false;
    generation.current++; flow.reset(); applied.current = undefined; screenSeen.current = false; announced.current = ''; setOrder('');
    flow.enter('S1', '휴대폰을 가슴 높이에서 화면 쪽으로 들어 주세요');
    startedAt.current = Date.now(); recorded.current = false; steps.current = []; refresh();
    Sonkkeut.requestKeyframe();
  }
  function submit(text: string) {
    setListening(false); Sonkkeut.cancelListening();
    try {
      Sonkkeut.clearTarget(); applied.current = undefined; generation.current++;
      setGuidance(undefined); setVerification(undefined);
      flow.submit(parseOrder(text, menu)); setOrder(text); refresh();
    } catch (e) {flow.intent = undefined; flow.confirmed = false; flow.remaining = []; flow.enter('S3', (e as Error).message); refresh();}
  }
  async function listen(provider: 'custom' | 'system' = 'custom') {
    const token = ++generation.current;
    setListening(true);
    try {const text = await Sonkkeut.listen(provider); if (token === generation.current && !flow.paused) {submit(text);}}
    catch (e) {if (token === generation.current && !flow.paused) {flow.enter('S3', (e as Error).message || '다시 말씀해 주세요'); refresh();}}
    finally {if (token === generation.current) {setListening(false);}}
  }
  async function downloadSpeech() {
    setModelDownloading(true); setModelProgress(undefined);
    setModelMessage('음성 모델을 내려받고 있습니다');
    try {
      const status = await Sonkkeut.downloadSpeechModel();
      if (modelMounted.current) {setSpeechModel(status); setModelMessage('자체 음성 모델 준비 완료 · 기기 안에서 인식합니다');}
    } catch (e) {
      if (modelMounted.current) {setModelMessage((e as Error).message || '모델 다운로드를 다시 시도해 주세요');}
    } finally {
      if (modelMounted.current) {setModelDownloading(false);}
    }
  }
  async function saveServer() {
    try {
      const base = validateBaseUrl(server);
      const selectedCode = code.trim().toUpperCase();
      if (selectedCode && !/^[A-Z2-9]{6}$/.test(selectedCode)) {throw new Error('매장 코드는 영문·숫자 6자리입니다');}
      await AsyncStorage.setItem('settings', JSON.stringify({server: base, code: selectedCode, statsEnabled: statsEnabledRef.current}));
      setServer(base); setCode(selectedCode); setServerConfig({server: base, code: selectedCode}); setPage('home'); reconnect();
      flow.enter('S0', '설정을 저장했습니다. 서버에 자동으로 연결합니다.'); refresh();
    } catch (e) {flow.enter('SE', (e as Error).message); refresh();}
  }
  function pause() {
    const next = !paused; setPaused(next); flow.paused = next;
    generation.current++; setListening(false); setGuidance(undefined);
    if (next) {Sonkkeut.stop(); Sonkkeut.silence(); Sonkkeut.cancelListening();}
    else {applied.current = undefined; flow.recover(); Sonkkeut.requestKeyframe(); refresh();}
  }
  function end() {
    record(false); generation.current++; Sonkkeut.clearTarget(); Sonkkeut.stop(); Sonkkeut.silence(); Sonkkeut.cancelListening();
    flow.reset(); applied.current = undefined; setListening(false); screenSeen.current = false; setGuidance(undefined); setVerification(undefined); setReader(false); setCameraError(false);
    flow.enter('S0', '손끝길을 시작합니다'); setRunning(false); setPage('home'); refresh();
  }
  const target = !paused && !cameraError && (flow.state === 'S4' || flow.state === 'S5') && ai.result?.found ? flow.action?.target : undefined;
  const overlay = targetOverlay(ai.result?.target_image_box, ai.result?.frame_size, preview);
  const guide = visualGuidance(flow.state, guidance?.action === flow.action ? guidance?.event : undefined, target?.id, paused, ai.result?.found === true);
  const progress = orderProgress(flow.intent, flow.remaining);
  const rows = readableRows(ai.result?.found ? flow.screen : undefined);
  const connectionStatus = connectionMatches ? connection.status : 'checking';
  const online = connectionStatus === 'online';
  function retryCamera() {
    generation.current++; applied.current = undefined; setGuidance(undefined); setCameraError(false); setPaused(false); flow.paused = false;
    flow.recover(); Sonkkeut.requestKeyframe(); refresh();
  }
  function readScreen() {
    setReader(!reader);
    if (ai.result?.found && flow.screen && !paused) {Sonkkeut.say(screenReading(flow.screen));}
  }
  const phase = flow.confirmed ? 2 : flow.state === 'S3' ? 1 : 0;
  return <SafeAreaView style={styles.root}>
    <StatusBar barStyle="dark-content" backgroundColor={colors.background}/>
    <View style={styles.header}>
      <View style={styles.brand}><BrandMark/><View><Text accessibilityRole="header" style={styles.title}>손끝길</Text><Text style={styles.subtitle}>손끝으로 찾는 쉬운 주문</Text></View></View>
      <Pressable accessibilityRole="button" accessibilityLabel={running ? '안내 설정' : page === 'home' ? '서버·매장 설정' : '홈으로'} disabled={!serverConfig} accessibilityState={{disabled: !serverConfig}} style={styles.headerButton} onPress={() => {
        if (running) {setSettings(!settings);} else {setPage(page === 'home' ? 'settings' : 'home'); setSettings(page === 'home');}
      }}><Text style={styles.headerButtonText}>{running ? '설정' : page === 'home' ? '설정' : '홈으로'}</Text></Pressable>
    </View>
    <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={styles.content}>
      <View style={styles.connectionCard}>
        <View style={styles.row}><Text style={[styles.connectionTitle, online && styles.successText]}>{online ? '● 서버 연결됨' : connectionStatus === 'checking' ? '◌ 자동 연결 확인 중' : connectionStatus === 'error' ? '○ 매장 설정 확인 필요' : '○ 오프라인 안내 가능'}</Text><Text style={styles.networkLabel}>{connection.network}</Text></View>
        {(!online || page === 'settings' || running && connection.menu && connection.menu.menu_version !== appliedMenuVersion.current) && <Text accessibilityLiveRegion="polite" style={styles.connectionDetails}>{connectionMatches ? connection.message : '저장된 서버와 매장 메뉴를 확인하고 있습니다'}</Text>}
        {online && <Text accessibilityLiveRegion="polite" style={styles.connectionDetails}>{running ? '매장 메뉴와 연결되어 있어요' : connection.menu?.store_name || '기본 시연 메뉴'}</Text>}
        {connectionMatches && connection.modelVersion && connection.modelVersion !== MODEL_VERSION && <Text style={styles.small}>서버 모델 정보 {connection.modelVersion} · 앱 모델 {MODEL_VERSION}</Text>}
        {running && connection.menu && (connection.menu.store_code !== storeCode || connection.menu.menu_version !== appliedMenuVersion.current) && <Text style={styles.small}>새 매장 메뉴는 안내 종료 후 적용됩니다.</Text>}
        {(connectionStatus === 'retrying' || connectionStatus === 'error') && <Button title="연결 다시 확인" secondary onPress={reconnect}/>}
      </View>
      {!running && page === 'home' && <>
        <View style={styles.hero}>
          <View style={styles.heroRow}><View style={styles.heroCopy}><Text style={styles.eyebrow}>당신의 주문 길잡이</Text><Text style={styles.heroTitle}>더 쉬운 주문,{'\n'}손끝길과 함께.</Text></View><KioskArtwork/></View>
          <Button title={ai.ready ? '손끝길 시작' : '모델 준비 중'} onPress={() => {start().catch(e => {flow.enter('SE', String(e)); refresh();});}} disabled={!ai.ready || !serverConfig}/>
          <Text style={styles.heroNote}>키오스크를 비추면 버튼까지 안내해 드려요.</Text>
        </View>
        {(ai.error || flow.state === 'SE') && <View style={styles.warningCard}><Text accessibilityLiveRegion="polite" style={styles.body}>{ai.error || flow.message}</Text><Button title="앱 권한 설정 열기" secondary onPress={() => {Linking.openSettings();}}/></View>}
        <View style={styles.sectionIntro}><Text accessibilityRole="header" style={styles.sectionTitle}>세 단계면 충분해요</Text><Text style={styles.small}>누른 결과를 확인하며 차근차근 안내합니다.</Text></View>
        <View style={styles.steps}>{['화면\n비추기', '주문\n확인하기', '손끝\n따라가기'].map((label, index) => <View key={label} style={styles.stepTile}><Text style={styles.stepNumber}>0{index + 1}</Text><Text style={styles.stepLabel}>{label}</Text></View>)}</View>
        <View style={styles.card}>
          <View style={styles.row}><View style={styles.storeIcon}><Text accessible={false} style={styles.storeIconText}>⌂</Text></View><View style={styles.flex}><Text style={styles.small}>현재 매장 메뉴</Text><Text accessibilityRole="header" style={styles.storeName}>{menuSource.current === 'server' ? connection.menu?.store_name || '저장된 매장 메뉴' : menuSource.current === 'detected' ? '카메라에서 읽은 메뉴' : '시연용 기본 메뉴 · 실제 화면을 확인해 주세요'}</Text></View><Text style={styles.storeCode}>{storeCode || '시연'}</Text></View>
          {menu.slice(0, 8).map(item => <View key={item.name} style={styles.menuRow}><Text style={styles.body}>{item.name}{item.sold_out ? ' · 품절' : ''}</Text><Text style={styles.menuPrice}>{item.price != null ? `${item.price.toLocaleString()}원` : '금액 확인 필요'}</Text></View>)}
          {menu.length > 8 && <Text style={styles.small}>외 {menu.length - 8}개 메뉴</Text>}
        </View>
        <View style={styles.privacy}><Text accessible={false} style={styles.privacyIcon}>✓</Text><Text style={styles.small}>영상과 자체 음성 인식은 기기 안에서 처리해요.</Text></View>
        <Button title={modelDownloading ? '음성 모델 다운로드 확인' : speechModel?.ready ? '음성 주문 준비 완료' : '말로 주문할 준비하기'} secondary onPress={() => {setPage('settings'); setSpeechSettings(true); setSettings(false);}}/>
      </>}
      {running && <>
        <View style={styles.stageRow}>{['화면 찾기', '주문 확인', '손끝 안내'].map((label, index) => <View key={label} style={[styles.stage, index === phase && styles.stageActive]}><Text style={[styles.stageText, index === phase && styles.stageActiveText]}>{index + 1} · {label}</Text></View>)}</View>
        <View style={styles.guidanceCard}>
          <Text style={styles.eyebrow}>{paused ? '잠시 쉬어 가세요' : labels[flow.state]}</Text>
          <View style={styles.guideHeading}><View style={styles.directionBox}><Text style={styles.direction} accessible={false}>{guide.symbol}</Text></View><View style={styles.guideText}><Text accessibilityLiveRegion="polite" style={styles.guideTitle}>{guide.title}</Text><Text style={styles.small}>{guide.detail}</Text></View></View>
          {target && <Text style={styles.targetName}>목표 버튼 · {target.text || '버튼 이름 확인 중'}</Text>}
          {(!paused || cameraError) && <View style={styles.captionBlock}><Text style={styles.small}>현재 안내</Text><Text accessibilityLiveRegion="polite" style={styles.message}>{listening ? '주문을 듣고 처리하고 있습니다…' : ai.error || (cameraError ? flow.message : !ai.result?.found && ai.result?.hint && ['S1', 'S2'].includes(flow.state) ? ai.result.hint : flow.message)}</Text></View>}
        </View>
        {device && permission && !cameraError && <View style={styles.preview} onLayout={e => setPreview(e.nativeEvent.layout)}>
          <Camera style={StyleSheet.absoluteFill} device={device} format={format} fps={15} pixelFormat="yuv" resizeMode="cover" isActive={ai.ready && !paused && foreground && flow.state !== 'S6'} frameProcessor={ai.frameProcessor} onError={() => {
            generation.current++; applied.current = undefined; setGuidance(undefined); setListening(false); Sonkkeut.cancelListening(); Sonkkeut.clearTarget(); Sonkkeut.silence(); flow.screen = undefined; flow.paused = true; setPaused(true); setCameraError(true);
            flow.enter('SE', '카메라를 열지 못했습니다. 다른 카메라 앱을 닫고 다시 시도해 주세요.'); refresh();
          }}/>
          {!ai.result?.found && <View pointerEvents="none" style={StyleSheet.absoluteFill}>{[styles.cornerTL, styles.cornerTR, styles.cornerBL, styles.cornerBR].map((corner, index) => <View key={index} style={[styles.cameraCorner, corner]}/>)}</View>}
          {lowVision && target && overlay && <View pointerEvents="none" style={[styles.outline, overlay]}/>}
          <View pointerEvents="none" style={styles.previewBadge}><Text style={styles.previewBadgeText}>{paused ? '카메라 일시 정지' : ai.result?.found ? `화면 인식됨${target ? ' · 노란 테두리가 목표' : ''}` : '화면 전체가 보이게 비춰 주세요'}</Text></View>
        </View>}
        {cameraError && <Button title="카메라 다시 열기" onPress={retryCamera}/>}
        {!device && <Text style={styles.body}>사용 가능한 후면 카메라가 없습니다. 앱 권한과 카메라 상태를 확인해 주세요.</Text>}
        {verification && <View style={verification.success ? styles.successCard : styles.warningCard}><Text accessibilityLiveRegion="polite" style={styles.body}>{verification.success ? '✓ 결과 확인' : '다시 확인 필요'} · {verification.text}</Text></View>}
        <View style={styles.row}><View style={styles.flex}><Button title="화면 글자 보기·읽기" secondary onPress={readScreen}/></View><View style={styles.flex}><Button title="안내 다시 듣기" secondary onPress={() => Sonkkeut.say(flow.message)} disabled={paused}/></View></View>
        {reader && <View style={styles.card}>
          <Text accessibilityRole="header" style={styles.sectionTitle}>카메라에서 읽은 화면</Text>
          <Text style={styles.small}>위에서 아래 순서입니다. 읽기 불확실한 글자는 안내 목표로 사용하지 않습니다.</Text>
          {rows.length ? rows.map(row => <View key={row.id} style={styles.readerRow}><Text style={[styles.body, !row.readable && styles.uncertainText]}>{row.text}</Text></View>) : <Text style={styles.body}>읽은 글자가 아직 없어요. 화면 전체를 비춘 뒤 다시 확인해 주세요.</Text>}
          <Button title="화면 글자 닫기" secondary onPress={() => setReader(false)}/>
        </View>}
        {flow.intent && <View style={styles.card}>
          <View style={styles.row}><Text accessibilityRole="header" style={styles.sectionTitle}>{flow.confirmed ? '확인한 주문' : '주문이 맞나요?'}</Text><Text style={styles.small}>{flow.confirmed ? `${progress.completed}/${progress.total}개 담기 확인` : `${progress.total}개`}</Text></View>
          {flow.confirmed && <View accessibilityLabel={`${progress.total}개 중 ${progress.completed}개 담기 확인`} style={styles.progressTrack}><View style={[styles.progressFill, {width: `${progress.total ? progress.completed / progress.total * 100 : 0}%`}]}/></View>}
          {flow.intent.items.map((item, index) => <View key={`${item.menu}-${index}`} style={styles.orderRow}><Text style={styles.body}>{item.menu} · {item.qty}개</Text><Text style={styles.small}>{[item.options.temp === 'hot' ? '따뜻한' : item.options.temp === 'ice' ? '아이스' : '온도 선택 없음', item.options.size, flow.confirmed ? `담기 ${item.qty - (flow.remaining[index] ?? item.qty)}/${item.qty}` : undefined].filter(Boolean).join(' · ')}</Text></View>)}
          <Text style={styles.body}>이용 방법 · {flow.intent.dine || '매장 / 포장 확인 필요'}</Text>
          <Text style={styles.small}>{progress.amount != null ? `기본 메뉴 예상 금액 ${progress.amount.toLocaleString()}원 · 옵션 추가금 별도` : '금액은 키오스크에서 확인해 주세요'}</Text>
          {!flow.confirmed && <Button title="네, 이 주문으로 안내 시작" onPress={() => {flow.confirm(); refresh();}} disabled={paused}/>}
        </View>}
        {!flow.confirmed && flow.state !== 'S3' && <Button title="주문 입력 열기" secondary onPress={() => {flow.enter('S3', '메뉴·수량·온도와 매장 또는 포장을 알려 주세요.'); refresh();}} disabled={paused}/>}
        {flow.state === 'S3' && <View style={styles.card}>
          <Text accessibilityRole="header" style={styles.sectionTitle}>어떤 메뉴를 주문할까요?</Text>
          <Text style={styles.small}>예: 따뜻한 아메리카노 두 잔 포장해주세요</Text>
          <Button title={listening ? '주문을 듣고 처리하고 있습니다' : '자체 모델로 말로 주문하기'} onPress={() => {listen();}} disabled={listening || paused || !speechModel?.ready}/>
          {!speechModel?.ready && <><Text style={styles.small}>음성 모델을 받거나 아래에 주문을 입력해 주세요.</Text><Button title="음성 모델 준비 열기" secondary onPress={() => setSpeechSettings(true)}/></>}
          <Button title="기기 음성 인식으로 주문하기" secondary onPress={() => {listen('system');}} disabled={listening || paused || modelDownloading || modelPreparing}/>
          {listening && <Button title="음성 입력 취소" secondary onPress={() => {generation.current++; setListening(false); Sonkkeut.cancelListening();}}/>}
          <TextInput accessibilityLabel="주문 문장" editable={!paused && !listening} placeholder="따뜻한 아메리카노 두 잔 포장" placeholderTextColor="#677e70" value={order} onChangeText={setOrder} style={styles.input} multiline/>
          <Button title="입력한 주문 확인" onPress={() => submit(order)} disabled={paused || listening || !order.trim()}/>
        </View>}
        {flow.state === 'SE' && !cameraError && <View style={styles.warningCard}>
          <Button title="화면 다시 확인" onPress={() => {Sonkkeut.clearTarget(); applied.current = undefined; setGuidance(undefined); flow.recover(); Sonkkeut.requestKeyframe(); refresh();}} disabled={paused}/>
          <Text style={styles.help}>직원분, 키오스크 주문을 도와주세요.</Text>
          <Text style={styles.small}>이 화면을 직원에게 보여줄 수 있어요.</Text>
        </View>}
        {captions.length > 0 && <View style={styles.card}><Text accessibilityRole="header" style={styles.sectionTitle}>최근 안내 자막</Text>{captions.map((text, index) => <Text key={`${index}-${text}`} style={index === 0 ? styles.body : styles.small}>{index === 0 ? '최근 · ' : '이전 · '}{text}</Text>)}</View>}
      </>}
      {(!running && page === 'settings' || running && (settings || speechSettings)) && <>
      <View style={styles.sectionIntro}><Text accessibilityRole="header" style={styles.sectionTitle}>내게 맞는 설정</Text><Text style={styles.small}>음성 주문과 화면 표시를 편하게 조절하세요.</Text></View>
      <View style={styles.card}>
        <View style={styles.row}><Text style={styles.body}>목표 버튼 크게 강조</Text><Switch accessibilityLabel="저시력 목표 버튼 강조" value={lowVision} trackColor={{false: '#ccd6ce', true: '#94bea6'}} thumbColor={lowVision ? colors.brand : '#fff'} onValueChange={setLowVision}/></View>
        <Text style={styles.small}>음성 안내와 함께 큰 글자·방향 화살표·목표 테두리를 보여줍니다.</Text>
        <Button title={speechSettings ? '음성 모델 설정 접기' : `음성 모델 ${speechModel?.ready ? '준비 완료' : '준비하기'}`} secondary onPress={() => setSpeechSettings(!speechSettings)}/>
        {speechSettings && <>
          <Text accessibilityLiveRegion="polite" style={styles.body}>{modelMessage}</Text>
          {modelDownloading && modelProgress && <Text style={styles.small}>{modelProgress.stage === 'downloading' ? (modelProgress.total_bytes > 0 ? `다운로드 ${Math.min(100, Math.floor(modelProgress.bytes / modelProgress.total_bytes * 100))}%` : `다운로드 ${Math.floor(modelProgress.bytes / 1000000)}MB`) : '다운로드 파일 확인·모델 준비 중'}</Text>}
          {!speechModel?.ready && !modelDownloading && <Button title={speechModel?.installed ? '음성 모델 준비 다시 시도' : '자체 음성 모델 받기 (약 485MB)'} onPress={() => {downloadSpeech();}} disabled={listening || modelPreparing || !speechModel}/>}
          {modelDownloading && <Button title="모델 다운로드 중지" secondary onPress={() => Sonkkeut.cancelSpeechModelDownload()}/>}
          <Text style={styles.small}>첫 다운로드에 인터넷과 약 1GB 여유 공간이 필요합니다. 준비 후 영상과 자체 음성 모델은 기기 안에서 처리합니다.</Text>
        </>}
        <Button title={settings ? '서버·매장 설정 접기' : '서버·매장 설정'} secondary onPress={() => setSettings(!settings)} disabled={!serverConfig}/>
        {settings && <>
          <Text style={styles.small}>기본 배포 서버에는 자동으로 연결됩니다. 다른 매장을 사용하려면 설정을 저장해 주세요.</Text>
          <TextInput accessibilityLabel="백엔드 주소" editable={!running} autoCapitalize="none" autoCorrect={false} value={server} onChangeText={setServer} placeholder={BACKEND_URL} placeholderTextColor="#677e70" style={styles.input}/>
          <TextInput accessibilityLabel="매장 코드" editable={!running} autoCapitalize="characters" autoCorrect={false} value={code} onChangeText={setCode} placeholder="매장 코드 6자리 (선택)" placeholderTextColor="#677e70" style={styles.input}/>
          <Button title="설정 저장·자동 연결" onPress={() => {saveServer();}} disabled={running}/>
          {running && <Text style={styles.small}>매장 설정은 주문 안내 종료 후 변경할 수 있습니다.</Text>}
          <View style={styles.row}><Text style={styles.body}>익명 통계 전송</Text><Switch accessibilityLabel="익명 통계 전송 동의" value={statsEnabled} trackColor={{false: '#ccd6ce', true: '#94bea6'}} thumbColor={statsEnabled ? colors.brand : '#fff'} onValueChange={value => {
            statsEnabledRef.current = value; setStatsEnabled(value);
            if (!value) {clearUsage().catch(() => {});} else if (online) {flushUsage().catch(() => {});}
            AsyncStorage.setItem('settings', JSON.stringify({...serverConfig, statsEnabled: value})).catch(() => {});
          }}/></View>
          <Text style={styles.small}>영상·음성·주문 문장·위치·기기 식별자는 보내지 않습니다. 동의하면 단계별 성공 여부와 소요 시간만 전송합니다.</Text>
          <Button title="앱 권한 설정 열기" secondary onPress={() => {Linking.openSettings();}}/>
        </>}
        <Text style={styles.small}>앱 {APP_VERSION} · AI {MODEL_VERSION}</Text>
      </View>
      </>}
    </ScrollView>
    {!running && <View style={styles.homeNav}>
      <Pressable accessibilityRole="tab" accessibilityLabel="홈 화면" accessibilityState={{selected: page === 'home'}} style={[styles.navItem, page === 'home' && styles.navItemActive]} onPress={() => setPage('home')}><Text style={[styles.navText, page === 'home' && styles.navTextActive]}>홈</Text></Pressable>
      <Pressable accessibilityRole="tab" accessibilityLabel="환경 설정" accessibilityState={{selected: page === 'settings'}} style={[styles.navItem, page === 'settings' && styles.navItemActive]} onPress={() => setPage('settings')}><Text style={[styles.navText, page === 'settings' && styles.navTextActive]}>설정</Text></Pressable>
    </View>}
    {running && <View style={styles.footer}><View style={styles.flex}><Button title={paused ? '계속하기' : '일시 정지'} onPress={pause} disabled={cameraError || flow.state === 'S6'}/></View><View style={styles.flex}><Button title="주문 안내 종료" secondary onPress={end}/></View></View>}
  </SafeAreaView>;
}
