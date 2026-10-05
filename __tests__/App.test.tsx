import React from 'react';
import {Platform, Pressable, ScrollView, Switch, TextInput} from 'react-native';
import renderer, {act} from 'react-test-renderer';
import AsyncStorage from '@react-native-async-storage/async-storage';
import App from '../App';
import type {GuidanceEvent, ScreenStructure, Verdict} from 'react-native-sonkkeut';
import type {Connection, ServerConfig} from '../src/useBackendConnection';

const mockNative = {say: jest.fn(), announce: jest.fn(), configureFeedback: jest.fn(), cancelListening: jest.fn(), silence: jest.fn(), cancelSpeechModelDownload: jest.fn(),
  listen: jest.fn(async () => '아메리카노 한 잔'), finishListening: jest.fn(),
  correctMenuSpeech: jest.fn(async (utterance: string, _scope: string, documents: {name: string; aliases: string[]; sold_out: boolean}[]) => new (require('react-native-sonkkeut/src/menuRag').MenuRagIndex)(documents).correct(utterance)),
  setMenuAliases: jest.fn(), clearTarget: jest.fn(), stop: jest.fn(), requestKeyframe: jest.fn(), setTarget: jest.fn(async () => true),
  getSpeechModelStatus: jest.fn(async () => ({ready: true, installed: true})), addModelDownloadListener: jest.fn(() => () => {})};
let mockCallbacks: {onScreen: (screen: ScreenStructure) => void; onResult: (result: {found: boolean}) => void; onEvent: (event: GuidanceEvent) => void; onVerdict: (verdict: Verdict) => void};
const mockAi = {ready: true, result: {found: true, hint: undefined as string | undefined}, frameProcessor: undefined};
let mockActive: boolean;
let mockWideAvailable = false;
let mockConnection: Connection;
let mockConfig: ServerConfig | undefined;
jest.mock('react-native-sonkkeut', () => ({get MenuRagIndex() {return require('react-native-sonkkeut/src/menuRag').MenuRagIndex;}, get Sonkkeut() {return mockNative;}, useSonkkeut: (options: typeof mockCallbacks & {active: boolean}) => {mockCallbacks = options; mockActive = options.active; return mockAi;}}));
jest.mock('react-native-vision-camera', () => ({Camera: Object.assign(() => null, {requestCameraPermission: async () => 'granted'}), useCameraDevice: (_: string, filter?: unknown) => filter && mockWideAvailable ? {id: 'ultra', physicalDevices: ['ultra-wide-angle-camera'], minZoom: 0.5, neutralZoom: 1} : {id: 'camera', physicalDevices: ['wide-angle-camera'], minZoom: 1, neutralZoom: 1}, useCameraFormat: () => undefined}));
jest.mock('../src/useBackendConnection', () => ({useBackendConnection: (config?: ServerConfig) => {mockConfig = config; return {connection: mockConnection, reconnect: jest.fn()};}}));
jest.mock('@react-native-async-storage/async-storage', () => require('@react-native-async-storage/async-storage/jest/async-storage-mock'));
const oldMenu = {store_code: 'ABCDEF', store_name: '기존 매장', menu_version: 1, categories: [], items: [{name: '아메리카노', price: 4500, aliases: [], sold_out: false}, {name: '기존 매장 전용', price: 1000, aliases: [], sold_out: false}]};
const newMenu = {...oldMenu, menu_version: 2, store_name: '새 매장', items: [{name: '새 메뉴', price: 9999, aliases: [], sold_out: false}]};
const menuScreen: ScreenStructure = {screen_type: 'menu', keyframe_id: 1, cart_count: 0, elements: [{id: 'coffee', kind: 'menu', text: '아메리카노', conf: 0.95, conf_ocr: 0.95, box: [0, 0, 0.4, 0.4]}]};
let root: renderer.ReactTestRenderer;
async function settle() {await act(async () => {for (let i = 0; i < 12; i++) {await Promise.resolve();}});}
function tap(label: string) {
  const button = root.root.findAllByType(Pressable).find(item => item.props.accessibilityLabel === label);
  expect(button).toBeDefined(); expect(button!.props.disabled).not.toBe(true); act(() => {button!.props.onPress();});
}
function text() {return JSON.stringify(root.toJSON());}
beforeEach(async () => {
  jest.clearAllMocks(); await AsyncStorage.clear();
  mockNative.listen.mockReset().mockResolvedValue('아메리카노 한 잔');
  mockWideAvailable = false;
  jest.replaceProperty(Platform, 'OS', 'android');
  mockConnection = {status: 'online', server: 'https://saved.example.com', configKey: JSON.stringify(['https://saved.example.com', 'ABCDEF']), network: 'Wi-Fi', menu: oldMenu, message: '자동 연결 완료'};
  await AsyncStorage.setItem('settings', JSON.stringify({server: 'https://saved.example.com', code: 'ABCDEF', statsEnabled: false}));
  jest.spyOn(require('react-native').PermissionsAndroid, 'request').mockResolvedValue('granted');
  act(() => {root = renderer.create(<App/>);}); await settle();
});

test('speech goes through scoped retrieval and shows raw/corrected text before confirmation', async () => {
  mockNative.listen.mockResolvedValue('아메리 카너 한 찬 포장해 주세요');
  tap('손끝길 시작'); await settle();
  act(() => mockCallbacks.onScreen(menuScreen)); await settle();
  expect(mockNative.correctMenuSpeech).toHaveBeenCalledWith('아메리 카너 한 찬 포장해 주세요', expect.stringContaining('saved.example.com'), oldMenu.items);
  expect(text()).toContain('들은 문장'); expect(text()).toContain('아메리 카너');
  expect(text()).toContain('메뉴와 표현 보정'); expect(text()).toContain('아메리카노');
  expect(text()).toContain('네, 이 주문으로 안내 시작'); expect(mockNative.setTarget).not.toHaveBeenCalled();
});

test('an ambiguous menu requires explicit candidate selection before order confirmation', async () => {
  mockConnection = {...mockConnection, menu: {...oldMenu, menu_version: 2, items: [
    {name: '카페라떼', price: 4500, aliases: ['라떼'], sold_out: false},
    {name: '바닐라라떼', price: 5500, aliases: ['라떼'], sold_out: false},
  ]}};
  act(() => root.update(<App/>)); await settle();
  mockNative.listen.mockResolvedValue('라떼 한 잔');
  tap('손끝길 시작'); await settle();
  act(() => mockCallbacks.onScreen(menuScreen)); await settle();
  expect(text()).toContain('어떤 메뉴인가요'); expect(text()).not.toContain('네, 이 주문으로 안내 시작');
  tap('카페라떼 선택'); await settle();
  expect(text()).toContain('네, 이 주문으로 안내 시작'); expect(mockNative.setTarget).not.toHaveBeenCalled();
});

test('recording completion keeps inference pending, while navigating away rejects a late result', async () => {
  let resolveSpeech: (value: string) => void = () => {};
  mockNative.listen.mockImplementation(() => new Promise(resolve => {resolveSpeech = resolve;}));
  tap('손끝길 시작'); await settle();
  act(() => mockCallbacks.onScreen(menuScreen));
  tap('말하기 완료'); expect(mockNative.finishListening).toHaveBeenCalledTimes(1);
  openMenu(); await act(async () => resolveSpeech('아메리카노 두 잔'));
  await settle(); expect(mockNative.correctMenuSpeech).not.toHaveBeenCalled(); expect(text()).not.toContain('네, 이 주문으로 안내 시작');
});

test('screen detection starts speech once while the same camera stays active', async () => {
  mockNative.listen.mockImplementation(() => new Promise(() => {}));
  tap('손끝길 시작'); await settle();
  const Camera = require('react-native-vision-camera').Camera;
  const camera = root.root.findByType(Camera);
  act(() => mockCallbacks.onScreen(menuScreen)); await settle();
  expect(mockNative.listen).toHaveBeenCalledWith('custom');
  expect(mockNative.configureFeedback).toHaveBeenLastCalledWith(false, true, 1);
  expect(mockActive).toBe(true); expect(root.root.findByType(Camera)).toBe(camera);
  expect(camera.props.isActive).toBe(true); expect(text()).not.toContain('주문 확인');
  act(() => mockCallbacks.onScreen({...menuScreen, keyframe_id: 2})); await settle();
  expect(mockNative.listen).toHaveBeenCalledTimes(1);
  expect(mockNative.stop).not.toHaveBeenCalled();
  tap('음성 입력 취소');
  expect(mockNative.configureFeedback).toHaveBeenLastCalledWith(true, true, 1);
});

test('switching to text ordering rejects late speech and keeps the camera active', async () => {
  let resolveSpeech: (value: string) => void = () => {};
  mockNative.listen.mockImplementation(() => new Promise(resolve => {resolveSpeech = resolve;}));
  tap('손끝길 시작'); await settle();
  act(() => mockCallbacks.onScreen(menuScreen));
  enterOrder();
  await act(async () => resolveSpeech('아메리카노 두 잔')); await settle();
  expect(mockNative.correctMenuSpeech).not.toHaveBeenCalled(); expect(mockActive).toBe(true);
  expect(text()).toContain('4,500'); expect(text()).not.toContain('9,000');
  tap('네, 이 주문으로 안내 시작'); await settle();
  expect(mockNative.setTarget).not.toHaveBeenCalled();
  act(() => mockCallbacks.onScreen({...menuScreen, keyframe_id: 2})); await settle();
  expect(mockNative.setTarget).toHaveBeenCalledWith('coffee', expect.anything());
});

test('losing the screen during dictation does not strand the pending speech result', async () => {
  let resolveSpeech: (value: string) => void = () => {};
  mockNative.listen.mockImplementation(() => new Promise(resolve => {resolveSpeech = resolve;}));
  tap('손끝길 시작'); await settle();
  act(() => {mockCallbacks.onResult({found: true}); mockCallbacks.onScreen(menuScreen);});
  act(() => mockCallbacks.onResult({found: false}));
  await act(async () => resolveSpeech('아메리카노 한 잔 포장')); await settle();
  expect(text()).toContain('네, 이 주문으로 안내 시작'); expect(mockActive).toBe(true);
});

test('pausing dictation ignores its late result and allows retry on the camera', async () => {
  let resolveSpeech: (value: string) => void = () => {};
  mockNative.listen.mockImplementationOnce(() => new Promise(resolve => {resolveSpeech = resolve;}));
  tap('손끝길 시작'); await settle();
  act(() => mockCallbacks.onScreen(menuScreen)); tap('안내 중지');
  await act(async () => resolveSpeech('아메리카노 두 잔')); await settle();
  expect(mockNative.correctMenuSpeech).not.toHaveBeenCalled(); expect(mockActive).toBe(false);
  tap('안내 계속'); tap('기기 음성 인식으로 주문하기'); await settle();
  expect(mockNative.listen).toHaveBeenLastCalledWith('system'); expect(mockActive).toBe(true);
  expect(text()).toContain('네, 이 주문으로 안내 시작');
});
afterEach(() => {act(() => root.unmount()); jest.restoreAllMocks();});
function openMenu() {tap('설정 열기');}
function openSettings() {openMenu(); tap('화면·카메라·음성 설정');}
function enterOrder() {
  tap('텍스트로 주문하기');
  const input = root.root.findAllByType(TextInput).find(item => item.props.accessibilityLabel === '주문 문장')!;
  act(() => input.props.onChangeText('아메리카노 한 잔 포장'));
  tap('입력한 주문 확인');
}
test('saved custom settings hydrate before auto-connection and main has no scrolling or input fields', () => {
  expect(mockConfig).toEqual({server: 'https://saved.example.com', code: 'ABCDEF'});
  expect(text()).toContain('서버 연결됨'); expect(text()).toContain('기존 매장');
  expect(root.root.findAllByType(ScrollView)).toHaveLength(0);
  expect(root.root.findAllByType(TextInput)).toHaveLength(0);
  openSettings(); expect(text()).toContain('내게 맞는 설정');
  tap('메인 화면으로'); expect(text()).toContain('기존 매장'); expect(root.root.findAllByType(TextInput)).toHaveLength(0);
});
test('saving a different server discards the previous server menu before the new response arrives', async () => {
  openMenu(); expect(text()).toContain('기존 매장 전용'); tap('화면·카메라·음성 설정'); tap('서버·매장 설정');
  const input = root.root.findAllByType(TextInput).find(item => item.props.accessibilityLabel === '백엔드 주소')!;
  act(() => input.props.onChangeText('https://new.example.com'));
  tap('설정 저장·자동 연결'); await settle();
  expect(mockConfig?.server).toBe('https://new.example.com'); expect(text()).not.toContain('기존 매장 전용');
  tap('메인 화면으로'); openMenu();
  expect(text()).toContain('시연용 기본 메뉴');
  expect(text()).not.toContain('● 서버 연결됨');
});
test('reconnecting during an order preserves the original menu until the user ends guidance', async () => {
  tap('손끝길 시작'); await settle();
  mockConnection = {...mockConnection, menu: newMenu}; act(() => root.update(<App/>)); await settle();
  openMenu();
  expect(text()).toContain('새 매장 메뉴는 안내 종료 후 적용됩니다');
  tap('메인 화면으로'); tap('안내 계속'); tap('텍스트로 주문하기');
  const input = root.root.findAllByType(TextInput).find(item => item.props.accessibilityLabel === '주문 문장')!;
  act(() => input.props.onChangeText('아메리카노 한 잔 포장'));
  tap('입력한 주문 확인'); expect(text()).toContain('주문이 맞나요?'); expect(text()).toContain('4,500');
  openMenu(); tap('주문 안내 종료'); await settle(); openMenu(); expect(text()).toContain('새 메뉴'); expect(text()).toContain('새 매장');
});
test('native guidance, pause and verdicts update visual text and reader content', async () => {
  tap('손끝길 시작'); await settle(); act(() => mockCallbacks.onScreen(menuScreen));
  enterOrder(); tap('네, 이 주문으로 안내 시작'); await settle();
  expect(root.root.findAllByType(ScrollView)).toHaveLength(0);
  expect(mockNative.setTarget).not.toHaveBeenCalled();
  act(() => mockCallbacks.onScreen({...menuScreen, keyframe_id: 2})); await settle();
  expect(mockNative.setTarget).toHaveBeenCalledWith('coffee', expect.anything());
  act(() => mockCallbacks.onEvent({type: 'direction', target_id: 'coffee', dir: 'right', distance: 'near', vibe_hz: 6, speak: '오른쪽으로 조금 이동하세요'}));
  expect(text()).toContain('오른쪽으로 이동'); expect(text()).toContain('목표 ·');
  tap('재안내'); expect(mockNative.say).toHaveBeenLastCalledWith('오른쪽으로 조금 이동하세요');
  tap('안내 중지'); expect(text()).toContain('안내가 멈췄어요');
  tap('재안내'); expect(mockNative.say).toHaveBeenLastCalledWith(expect.stringContaining('안내가 멈췄습니다'));
  tap('안내 계속'); await settle();
  act(() => mockCallbacks.onScreen({...menuScreen, keyframe_id: 3})); await settle();
  act(() => mockCallbacks.onEvent({type: 'press', target_id: 'coffee', vibe_hz: 10, speak: '지금 누르세요'}));
  expect(text()).toContain('지금 누르세요');
  act(() => mockCallbacks.onVerdict({result: 'uncertain', reason: 'insufficient_evidence', speak: '눌린 결과를 확인하지 못했습니다'}));
  expect(text()).toContain('눌린 결과를 확인하지 못했습니다');
  openMenu(); expect(text()).toContain('최근 안내 자막'); tap('화면 글자 보기·읽기'); expect(text()).toContain('카메라에서 읽은 화면'); expect(text()).toContain('아메리카노');
});

test('menus pause the camera and cannot reuse an old target until a fresh screen is received', async () => {
  tap('손끝길 시작'); await settle(); enterOrder(); tap('네, 이 주문으로 안내 시작'); await settle();
  act(() => mockCallbacks.onScreen(menuScreen)); await settle(); expect(mockActive).toBe(true);
  openMenu(); expect(mockActive).toBe(false); expect(mockNative.clearTarget).toHaveBeenCalled();
  act(() => mockCallbacks.onEvent({type: 'press', target_id: 'coffee', vibe_hz: 10, speak: '지금 누르세요'}));
  tap('메인 화면으로'); expect(mockActive).toBe(false); expect(text()).toContain('안내가 멈췄어요');
  const count = mockNative.setTarget.mock.calls.length;
  tap('안내 계속'); await settle(); expect(mockActive).toBe(true); expect(mockNative.setTarget.mock.calls).toHaveLength(count);
  act(() => mockCallbacks.onScreen({...menuScreen, keyframe_id: 2})); await settle();
  expect(mockNative.setTarget.mock.calls.length).toBe(count + 1);
});

test('rapid theme and camera preference changes preserve custom server settings and reload correctly', async () => {
  openSettings(); tap('밝은 테마');
  const camera = root.root.findAllByType(Switch).find(item => item.props.accessibilityLabel === '넓은 카메라 화각 사용')!;
  act(() => camera.props.onValueChange(false)); await settle();
  const saved = JSON.parse((await AsyncStorage.getItem('settings'))!);
  expect(saved).toMatchObject({theme: 'light', wideCamera: false, server: 'https://saved.example.com', code: 'ABCDEF', statsEnabled: false});
  act(() => root.unmount()); act(() => {root = renderer.create(<App/>);}); await settle(); openSettings();
  const choice = root.root.findAllByType(Pressable).find(item => item.props.accessibilityLabel === '밝은 테마')!;
  expect(choice.props.accessibilityState.checked).toBe(true);
});

test('active main camera uses the entire frame and keeps text ordering between camera and guidance controls', async () => {
  tap('손끝길 시작'); await settle();
  const Camera = require('react-native-vision-camera').Camera;
  expect(root.root.findByType(Camera).props.resizeMode).toBe('contain');
  expect(root.root.findAllByType(ScrollView)).toHaveLength(0);
  expect(root.root.findAllByType(Pressable).map(button => button.props.accessibilityLabel)).toEqual(['설정 열기', '텍스트로 주문하기', '안내 중지', '재안내']);
});

test('an ultra-wide opening error falls back once, and a standard camera error stops guidance for retry', async () => {
  mockWideAvailable = true; act(() => root.update(<App/>));
  tap('손끝길 시작'); await settle();
  const Camera = require('react-native-vision-camera').Camera;
  expect(root.root.findByType(Camera).props.device.id).toBe('ultra');
  act(() => root.root.findByType(Camera).props.onError()); await settle();
  expect(root.root.findByType(Camera).props.device.id).toBe('camera');
  expect(mockActive).toBe(true);
  act(() => root.root.findByType(Camera).props.onError()); await settle();
  expect(mockActive).toBe(false); expect(text()).toContain('카메라 다시 열기');
  tap('카메라 다시 열기'); await settle();
  expect(root.root.findByType(Camera).props.device.id).toBe('camera'); expect(mockActive).toBe(true);
});

test('new UX preferences save together and control actual native feedback while explicit repeat remains available', async () => {
  openSettings(); tap('글자 크기 · 더 크게'); tap('안내 속도 · 느리게');
  for (const label of ['자동 음성 안내', '진동 안내']) {
    const control = root.root.findAllByType(Switch).find(item => item.props.accessibilityLabel === label)!;
    act(() => control.props.onValueChange(false));
  }
  await settle();
  expect(mockNative.configureFeedback).toHaveBeenLastCalledWith(false, false, 0.75);
  expect(JSON.parse((await AsyncStorage.getItem('settings'))!)).toMatchObject({textSize: 2, speechSpeed: 0, voiceEnabled: false, vibrationEnabled: false, server: 'https://saved.example.com'});
  tap('메인 화면으로'); tap('손끝길 시작'); await settle();
  expect(root.root.findAllByType(ScrollView)).toHaveLength(0);
  tap('재안내'); expect(mockNative.say).toHaveBeenCalled();
  act(() => root.unmount()); act(() => {root = renderer.create(<App/>);}); await settle();
  expect(mockNative.configureFeedback).toHaveBeenLastCalledWith(false, false, 0.75);
  openSettings();
  const selected = root.root.findAllByType(Pressable).find(item => item.props.accessibilityLabel === '글자 크기 · 더 크게')!;
  expect(selected.props.accessibilityState.checked).toBe(true);
});
