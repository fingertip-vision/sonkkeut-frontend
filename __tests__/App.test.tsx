import React from 'react';
import {Platform, Pressable, TextInput} from 'react-native';
import renderer, {act} from 'react-test-renderer';
import AsyncStorage from '@react-native-async-storage/async-storage';
import App from '../App';
import type {GuidanceEvent, ScreenStructure, Verdict} from 'react-native-sonkkeut';
import type {Connection, ServerConfig} from '../src/useBackendConnection';

const mockNative = {say: jest.fn(), cancelListening: jest.fn(), silence: jest.fn(), cancelSpeechModelDownload: jest.fn(),
  setMenuAliases: jest.fn(), clearTarget: jest.fn(), stop: jest.fn(), requestKeyframe: jest.fn(), setTarget: jest.fn(async () => true),
  getSpeechModelStatus: jest.fn(async () => ({ready: true, installed: true})), addModelDownloadListener: jest.fn(() => () => {})};
let mockCallbacks: {onScreen: (screen: ScreenStructure) => void; onEvent: (event: GuidanceEvent) => void; onVerdict: (verdict: Verdict) => void};
const mockAi = {ready: true, result: {found: true, hint: undefined as string | undefined}, frameProcessor: undefined};
let mockConnection: Connection;
let mockConfig: ServerConfig | undefined;
jest.mock('react-native-sonkkeut', () => ({get Sonkkeut() {return mockNative;}, useSonkkeut: (options: typeof mockCallbacks) => {mockCallbacks = options; return mockAi;}}));
jest.mock('react-native-vision-camera', () => ({Camera: Object.assign(() => null, {requestCameraPermission: async () => 'granted'}), useCameraDevice: () => ({id: 'camera'}), useCameraFormat: () => undefined}));
jest.mock('../src/useBackendConnection', () => ({useBackendConnection: (config?: ServerConfig) => {mockConfig = config; return {connection: mockConnection, reconnect: jest.fn()};}}));
jest.mock('@react-native-async-storage/async-storage', () => require('@react-native-async-storage/async-storage/jest/async-storage-mock'));
const oldMenu = {store_code: 'ABCDEF', store_name: '기존 매장', menu_version: 1, categories: [], items: [{name: '아메리카노', price: 4500, aliases: [], sold_out: false}, {name: '기존 매장 전용', price: 1000, aliases: [], sold_out: false}]};
const newMenu = {...oldMenu, menu_version: 2, store_name: '새 매장', items: [{name: '새 메뉴', price: 9999, aliases: [], sold_out: false}]};
const menuScreen: ScreenStructure = {screen_type: 'menu', keyframe_id: 1, cart_count: 0, elements: [{id: 'coffee', kind: 'menu', text: '아메리카노', conf: 0.95, conf_ocr: 0.95, box: [0, 0, 0.4, 0.4]}]};
let root: renderer.ReactTestRenderer;
async function settle() {await act(async () => {for (let i = 0; i < 12; i++) {await Promise.resolve();}});}
function tap(label: string) {
  const button = root.root.findAllByType(Pressable).find(item => item.props.accessibilityLabel === label);
  expect(button).toBeDefined(); expect(button!.props.disabled).toBe(false); act(() => {button!.props.onPress();});
}
function text() {return JSON.stringify(root.toJSON());}
beforeEach(async () => {
  jest.clearAllMocks(); await AsyncStorage.clear();
  jest.replaceProperty(Platform, 'OS', 'android');
  mockConnection = {status: 'online', server: 'https://saved.example.com', configKey: JSON.stringify(['https://saved.example.com', 'ABCDEF']), network: 'Wi-Fi', menu: oldMenu, message: '자동 연결 완료'};
  await AsyncStorage.setItem('settings', JSON.stringify({server: 'https://saved.example.com', code: 'ABCDEF', statsEnabled: false}));
  jest.spyOn(require('react-native').PermissionsAndroid, 'request').mockResolvedValue('granted');
  act(() => {root = renderer.create(<App/>);}); await settle();
});
afterEach(() => {act(() => root.unmount()); jest.restoreAllMocks();});
test('saved custom settings hydrate before automatic connection and server menus appear without a connection button', () => {
  expect(mockConfig).toEqual({server: 'https://saved.example.com', code: 'ABCDEF'});
  expect(text()).toContain('서버 연결됨'); expect(text()).toContain('기존 매장'); expect(text()).toContain('아메리카노');
  expect(root.root.findAllByType(TextInput)).toHaveLength(0);
  tap('서버·매장 설정'); expect(text()).toContain('내게 맞는 설정');
  tap('홈으로'); expect(text()).toContain('기존 매장'); expect(root.root.findAllByType(TextInput)).toHaveLength(0);
});
test('saving a different server discards the previous server menu before the new response arrives', async () => {
  expect(text()).toContain('기존 매장 전용'); tap('서버·매장 설정');
  const input = root.root.findAllByType(TextInput).find(item => item.props.accessibilityLabel === '백엔드 주소')!;
  act(() => input.props.onChangeText('https://new.example.com'));
  tap('설정 저장·자동 연결'); await settle();
  expect(mockConfig?.server).toBe('https://new.example.com'); expect(text()).not.toContain('기존 매장 전용');
  expect(text()).toContain('시연용 기본 메뉴');
  expect(text()).not.toContain('● 서버 연결됨');
});
test('reconnecting during an order preserves the original menu until the user ends guidance', async () => {
  tap('손끝길 시작'); await settle();
  mockConnection = {...mockConnection, menu: newMenu}; act(() => root.update(<App/>)); await settle();
  expect(text()).toContain('새 매장 메뉴는 안내 종료 후 적용됩니다');
  tap('주문 입력 열기');
  const input = root.root.findAllByType(TextInput).find(item => item.props.accessibilityLabel === '주문 문장')!;
  act(() => input.props.onChangeText('아메리카노 한 잔 포장'));
  tap('입력한 주문 확인'); expect(text()).toContain('주문이 맞나요?'); expect(text()).toContain('4,500');
  tap('주문 안내 종료'); await settle(); expect(text()).toContain('새 메뉴'); expect(text()).toContain('새 매장');
});
test('native guidance, pause and verdicts update visual text and reader content', async () => {
  tap('손끝길 시작'); await settle(); act(() => mockCallbacks.onScreen(menuScreen));
  const input = root.root.findAllByType(TextInput).find(item => item.props.accessibilityLabel === '주문 문장')!;
  act(() => input.props.onChangeText('아메리카노 한 잔 포장')); tap('입력한 주문 확인'); tap('네, 이 주문으로 안내 시작'); await settle();
  expect(mockNative.setTarget).toHaveBeenCalledWith('coffee', expect.anything());
  act(() => mockCallbacks.onEvent({type: 'direction', target_id: 'coffee', dir: 'right', distance: 'near', vibe_hz: 6, speak: '오른쪽으로 조금 이동하세요'}));
  expect(text()).toContain('오른쪽으로 이동'); expect(text()).toContain('목표 버튼'); expect(text()).toContain('최근 안내 자막');
  tap('일시 정지'); expect(text()).toContain('안내가 멈췄어요');
  tap('계속하기'); await settle();
  act(() => mockCallbacks.onScreen({...menuScreen, keyframe_id: 2})); await settle();
  act(() => mockCallbacks.onEvent({type: 'press', target_id: 'coffee', vibe_hz: 10, speak: '지금 누르세요'}));
  expect(text()).toContain('화면 반응 확인 중');
  act(() => mockCallbacks.onVerdict({result: 'uncertain', reason: 'insufficient_evidence', speak: '눌린 결과를 확인하지 못했습니다'}));
  expect(text()).toContain('다시 확인 필요'); expect(text()).toContain('눌린 결과를 확인하지 못했습니다');
  tap('화면 글자 보기·읽기'); expect(text()).toContain('카메라에서 읽은 화면'); expect(text()).toContain('아메리카노');
});
