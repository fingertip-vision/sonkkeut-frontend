import React, {useEffect, useRef, useState} from 'react';
import {AppState, Linking, PermissionsAndroid, Platform, Pressable, SafeAreaView, ScrollView, StyleSheet, Switch, Text, TextInput, View} from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import {Camera, useCameraDevice, useCameraFormat} from 'react-native-vision-camera';
import {Sonkkeut, useSonkkeut} from 'react-native-sonkkeut';
import type {ModelDownloadProgress, SpeechModelStatus} from 'react-native-sonkkeut';
import type {Action, MenuItem} from './src/domain';
import {isReadableElement, OrderFlow, parseOrder, screenReading} from './src/domain';
import {clearUsage, DEFAULT_MENU, enqueueUsage, flushUsage, health, loadMenu, modelVersion, validateBaseUrl} from './src/backend';
import type {Step} from './src/backend';
import {APP_VERSION, BACKEND_URL, DEFAULT_STORE_CODE, MODEL_VERSION} from './src/config';
import {restoreSettings} from './src/settings';

const labels = {S0: '준비', S1: '화면 탐색', S2: '화면 인식', S3: '주문 입력', S4: '손끝 유도', S5: '결과 확인', S6: '안내 완료', SE: '다시 확인'};
function Button({title, onPress, disabled = false}: {title: string; onPress: () => void; disabled?: boolean}) {
  return <Pressable accessibilityRole="button" accessibilityLabel={title} accessibilityState={{disabled}} disabled={disabled} onPress={onPress} style={[styles.button, disabled && styles.disabled]}><Text style={styles.buttonText}>{title}</Text></Pressable>;
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
  const [connection, setConnection] = useState('기본 시연 메뉴 · 서버 미연결');
  const [statsEnabled, setStatsEnabled] = useState(false);
  const statsEnabledRef = useRef(statsEnabled);
  statsEnabledRef.current = statsEnabled;
  const [lowVision, setLowVision] = useState(true);
  const [preview, setPreview] = useState({width: 1, height: 1});
  const [settings, setSettings] = useState(true);
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
  const connectGeneration = useRef(0);
  const menuSource = useRef<'default' | 'detected' | 'server'>('default');
  const screenSeen = useRef(false);
  const stepScreen = useRef('unknown');
  const device = useCameraDevice('back');
  const format = useCameraFormat(device, [{videoResolution: {width: 1280, height: 720}}, {fps: 15}]);
  const refresh = () => redraw(n => n + 1);
  const ai = useSonkkeut({active: running && permission && !paused && foreground && flow.state !== 'S6',
    onResult: result => {
      if (!running || flow.paused || flow.state === 'S6') {return;}
      if (result.found) {screenSeen.current = true; return;}
      if (!screenSeen.current) {return;}
      screenSeen.current = false; generation.current++; applied.current = undefined;
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
    AsyncStorage.getItem('settings').then(raw => {
      if (!raw) {return;}
      if (connectGeneration.current !== 0) {return;}
      const saved = restoreSettings(JSON.parse(raw));
      setServer(saved.server); setCode(saved.code); setStatsEnabled(saved.statsEnabled);
      statsEnabledRef.current = saved.statsEnabled === true;
      if (saved.statsEnabled === true) {flushUsage().catch(() => {});}
    }).catch(() => {});
    const sub = AppState.addEventListener('change', state => {
      setForeground(state === 'active');
      if (state !== 'active') {generation.current++; flow.paused = true; setPaused(true); setListening(false); Sonkkeut.cancelListening(); Sonkkeut.silence();}
      else if (statsEnabledRef.current) {flushUsage().catch(() => {});}
    });
    return () => sub.remove();
  }, [flow]);

  useEffect(() => {
    if (!running || paused || !foreground) {return;}
    if (flow.message !== announced.current) {
      announced.current = flow.message;
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
      generation.current++; applied.current = undefined; Sonkkeut.clearTarget(); flow.recover(); Sonkkeut.requestKeyframe(); refresh();
    }
  }, [ai.result?.target_missing, running, flow]);

  function record(completed: boolean) {
    if (recorded.current) {return;}
    recorded.current = true;
    if (!statsEnabledRef.current || !server) {return;}
    try {
      const base = validateBaseUrl(server);
      enqueueUsage(base, {store_code: storeCode, app_version: APP_VERSION, model_version: MODEL_VERSION, completed,
        duration_s: Math.min(7200, (Date.now() - startedAt.current) / 1000), steps: steps.current.slice(0, 200),
        event_id: `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}-${Math.random().toString(36).slice(2)}`}).catch(() => {});
    } catch (_) {}
  }
  async function start() {
    connectGeneration.current++;
    if (Platform.OS !== 'android') {flow.enter('SE', '안드로이드 기기에서 실행해 주세요.'); refresh(); return;}
    const camera = await Camera.requestCameraPermission();
    await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.RECORD_AUDIO);
    if (camera !== 'granted') {
      flow.enter('SE', '카메라 권한이 필요합니다. 설정에서 권한을 허용해 주세요.'); Sonkkeut.say(flow.message); refresh(); return;
    }
    if (!ai.ready) {flow.enter('SE', ai.error || '모델을 준비하고 있습니다. 잠시 후 다시 시작해 주세요.'); refresh(); return;}
    setPermission(true); setRunning(true); setSettings(false); setPaused(false); flow.paused = false;
    generation.current++; flow.reset(); applied.current = undefined; screenSeen.current = false; announced.current = ''; setOrder('');
    flow.enter('S1', '휴대폰을 가슴 높이에서 화면 쪽으로 들어 주세요');
    startedAt.current = Date.now(); recorded.current = false; steps.current = []; refresh();
    Sonkkeut.requestKeyframe();
  }
  function submit(text: string) {
    setListening(false); Sonkkeut.cancelListening();
    try {
      Sonkkeut.clearTarget(); applied.current = undefined; generation.current++;
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
  async function connect() {
    const token = ++connectGeneration.current;
    const selectedCode = code.trim().toUpperCase();
    setConnection('서버와 메뉴를 확인하고 있습니다');
    try {
      const base = validateBaseUrl(server);
      const result = selectedCode ? await loadMenu(base, selectedCode) : undefined;
      if (!result?.offline) {await health(base);}
      if (token !== connectGeneration.current) {return;}
      setServer(base);
      if (result) {
        menuSource.current = 'server';
        setMenu(result.menu.items); setStoreCode(result.menu.store_code);
        const aliases: Record<string, string> = {};
        result.menu.items.forEach(item => [item.name, ...item.aliases].forEach(alias => {aliases[alias.replace(/\s/g, '')] = item.name;}));
        Sonkkeut.setMenuAliases(aliases);
        setConnection(`${result.menu.store_name} · 메뉴 ${result.menu.menu_version}${result.offline ? ' · 저장된 메뉴' : ' · 연결됨'}`);
      } else {
        menuSource.current = 'default'; setMenu(DEFAULT_MENU); setStoreCode(undefined); Sonkkeut.setMenuAliases({});
        setConnection('서버 연결 완료 · 기본 시연 메뉴');
      }
      await AsyncStorage.setItem('settings', JSON.stringify({server: base, code: selectedCode, statsEnabled: statsEnabledRef.current}));
      if (!result?.offline) {
        const version = await modelVersion(base);
        if (token !== connectGeneration.current) {return;}
        if (version !== MODEL_VERSION) {setConnection(current => `${current} · 서버 모델 정보 ${version}`);}
        if (statsEnabledRef.current) {await flushUsage();}
      }
    } catch (e) {if (token === connectGeneration.current) {setConnection((e as Error).message + ' · 주문 안내는 오프라인으로 사용할 수 있습니다');}}
  }
  function pause() {
    const next = !paused; setPaused(next); flow.paused = next;
    generation.current++; setListening(false);
    if (next) {Sonkkeut.stop(); Sonkkeut.silence(); Sonkkeut.cancelListening();}
    else {applied.current = undefined; flow.recover(); Sonkkeut.requestKeyframe(); refresh();}
  }
  function end() {
    record(false); generation.current++; Sonkkeut.clearTarget(); Sonkkeut.stop(); Sonkkeut.silence(); Sonkkeut.cancelListening();
    flow.reset(); applied.current = undefined; setListening(false); screenSeen.current = false;
    flow.enter('S0', '손끝길을 시작합니다'); setRunning(false); refresh();
  }
  const target = !paused && flow.state === 'S4' && ai.result?.found ? flow.action?.target : undefined;
  return <SafeAreaView style={styles.root}>
    <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={styles.content}>
      <Text accessibilityRole="header" style={styles.title}>손끝길</Text>
      <Text style={styles.subtitle}>손끝으로 찾는 주문의 길</Text>
      <View style={styles.card}>
        <Text style={styles.state}>{labels[flow.state]}{paused ? ' · 일시 정지' : ''}</Text>
        <Text accessibilityLiveRegion="polite" style={styles.message}>{ai.error || flow.message}</Text>
      </View>
      {running && device && permission && <View style={styles.preview} onLayout={e => setPreview(e.nativeEvent.layout)}>
        <Camera style={StyleSheet.absoluteFill} device={device} format={format} fps={15} pixelFormat="yuv" isActive={ai.ready && !paused && foreground && flow.state !== 'S6'} frameProcessor={ai.frameProcessor}/>
        {lowVision && target && ai.result?.target_image_box && ai.result.frame_size && (() => {
          const [fw, fh] = ai.result.frame_size;
          const [x1, y1, x2, y2] = ai.result.target_image_box;
          const s = Math.max(preview.width / fw, preview.height / fh);
          return <View pointerEvents="none" style={[styles.outline, {left: x1 * s + (preview.width - fw * s) / 2, top: y1 * s + (preview.height - fh * s) / 2, width: (x2 - x1) * s, height: (y2 - y1) * s}]}/>;
        })()}
      </View>}
      {!running && <Button title={ai.ready ? '손끝길 시작' : '모델 준비 중'} onPress={() => {start().catch(e => {flow.enter('SE', String(e)); refresh();});}} disabled={!ai.ready}/>}
      {running && <>
        <View style={styles.row}><Button title="다시 듣기" onPress={() => Sonkkeut.say(flow.message)}/><Button title={paused ? '계속하기' : '일시 정지'} onPress={pause}/></View>
        {flow.state === 'S3' && <>
          <Button title={listening ? '주문을 듣고 처리하고 있습니다' : '자체 모델로 말로 주문하기'} onPress={() => {listen();}} disabled={listening || paused || !speechModel?.ready}/>
          {listening && <Button title="음성 입력 취소" onPress={() => {generation.current++; setListening(false); Sonkkeut.cancelListening();}}/>}
          {!speechModel?.ready && <Text style={styles.small}>아래에서 음성 모델을 받거나 주문 문장을 입력해 주세요.</Text>}
          <TextInput accessibilityLabel="주문 문장" placeholder="따뜻한 아메리카노 두 잔" placeholderTextColor="#acb6c9" value={order} onChangeText={setOrder} style={styles.input}/>
          <Button title="입력한 주문 확인" onPress={() => submit(order)} disabled={paused}/>
          {flow.intent && !flow.confirmed && <Button title="네, 이 주문으로 안내 시작" onPress={() => {flow.confirm(); refresh();}} disabled={paused}/>}
        </>}
        <Button title="화면 읽기" onPress={() => {if (ai.screen) {Sonkkeut.say(screenReading(ai.screen));}}} disabled={!ai.screen || paused}/>
        {flow.state === 'SE' && <>
          <Button title="화면 다시 확인" onPress={() => {Sonkkeut.clearTarget(); applied.current = undefined; flow.recover(); Sonkkeut.requestKeyframe(); refresh();}} disabled={paused}/>
          <Text style={styles.help}>직원분, 키오스크 주문을 도와주세요.</Text>
        </>}
        <Button title="주문 안내 종료" onPress={end}/>
      </>}
      <View style={styles.card}>
        <Text style={styles.body}>음성 주문 모델</Text>
        <Text accessibilityLiveRegion="polite" style={styles.small}>{modelMessage}</Text>
        {modelDownloading && modelProgress && <Text style={styles.small}>{modelProgress.stage === 'downloading' ? (modelProgress.total_bytes > 0 ? `다운로드 ${Math.min(100, Math.floor(modelProgress.bytes / modelProgress.total_bytes * 100))}%` : `다운로드 ${Math.floor(modelProgress.bytes / 1000000)}MB`) : '다운로드 파일 확인·모델 준비 중'}</Text>}
        {!speechModel?.ready && !modelDownloading && <Button title={speechModel?.installed ? '음성 모델 준비 다시 시도' : '자체 음성 모델 받기 (약 485MB)'} onPress={() => {downloadSpeech();}} disabled={listening || modelPreparing || !speechModel}/>}
        {modelDownloading && <Button title="모델 다운로드 중지" onPress={() => Sonkkeut.cancelSpeechModelDownload()}/>}
        <Text style={styles.small}>모델 다운로드에 인터넷과 약 1GB의 여유 공간이 필요합니다. 준비 후 영상과 음성은 기기 안에서 처리합니다.</Text>
        {running && flow.state === 'S3' && <Button title="기기 음성 인식으로 주문하기" onPress={() => {listen('system');}} disabled={listening || paused || modelDownloading || modelPreparing}/>}
      </View>
      <View style={styles.row}><Text style={styles.body}>목표 버튼 강조</Text><Switch accessibilityLabel="저시력 목표 버튼 강조" value={lowVision} onValueChange={setLowVision}/></View>
      <Button title={settings ? '서버 설정 접기' : '서버·매장 설정'} onPress={() => setSettings(!settings)}/>
      {settings && <View style={styles.card}>
        <Text style={styles.body}>{connection}</Text>
        <TextInput accessibilityLabel="백엔드 주소" editable={!running} autoCapitalize="none" autoCorrect={false} value={server} onChangeText={value => {connectGeneration.current++; setServer(value);}} placeholder={BACKEND_URL} placeholderTextColor="#acb6c9" style={styles.input}/>
        <TextInput accessibilityLabel="매장 코드" editable={!running} autoCapitalize="characters" value={code} onChangeText={value => {connectGeneration.current++; setCode(value);}} placeholder="매장 코드 6자리 (선택)" placeholderTextColor="#acb6c9" style={styles.input}/>
        <View style={styles.row}><Text style={styles.body}>익명 통계 전송</Text><Switch accessibilityLabel="익명 통계 전송 동의" value={statsEnabled} onValueChange={value => {
          connectGeneration.current++;
          statsEnabledRef.current = value; setStatsEnabled(value);
          if (!value) {clearUsage().catch(() => {});}
          AsyncStorage.setItem('settings', JSON.stringify({server, code, statsEnabled: value})).catch(() => {});
        }}/></View>
        <Text style={styles.small}>영상·음성·위치·기기 식별자는 보내지 않습니다. 단계별 성공 여부와 소요 시간만 전송합니다.</Text>
        {running && <Text style={styles.small}>서버와 매장 설정은 주문 안내 종료 후 변경할 수 있습니다.</Text>}
        <Button title="서버 연결·메뉴 받기" onPress={() => {connect();}} disabled={running}/>
        <Button title="앱 권한 설정 열기" onPress={() => {Linking.openSettings();}}/>
      </View>}
    </ScrollView>
  </SafeAreaView>;
}
const styles = StyleSheet.create({
  root: {flex: 1, backgroundColor: '#101b2b'}, content: {padding: 20, gap: 14}, title: {color: '#f4cc67', fontSize: 40, fontWeight: '800'},
  subtitle: {color: '#c7d2e6', fontSize: 18}, card: {backgroundColor: '#1e2b41', borderRadius: 18, padding: 18, gap: 12}, state: {color: '#f4cc67', fontSize: 18, fontWeight: '700'},
  message: {color: '#fff', fontSize: 26, lineHeight: 38}, body: {color: '#fff', fontSize: 19, flexShrink: 1}, small: {color: '#c7d2e6', fontSize: 15, lineHeight: 24},
  button: {backgroundColor: '#f4cc67', minHeight: 60, padding: 16, borderRadius: 14, alignItems: 'center', justifyContent: 'center', flexShrink: 1}, buttonText: {color: '#101b2b', fontSize: 20, fontWeight: '700'},
  disabled: {opacity: 0.5}, input: {borderWidth: 2, borderColor: '#93a9c8', borderRadius: 12, color: '#fff', padding: 14, fontSize: 19, minHeight: 58}, row: {flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 12},
  preview: {height: 320, overflow: 'hidden', borderRadius: 16}, outline: {position: 'absolute', borderWidth: 6, borderColor: '#ffe600'}, help: {fontSize: 30, fontWeight: '800', color: '#fff', padding: 12},
});
