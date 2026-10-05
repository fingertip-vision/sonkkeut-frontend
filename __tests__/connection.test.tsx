import React from 'react';
import renderer, {act} from 'react-test-renderer';
import {useBackendConnection} from '../src/useBackendConnection';
import type {Connection, ServerConfig} from '../src/useBackendConnection';
import {cachedMenu, health, loadMenu, MenuResponseError, modelVersion} from '../src/backend';
import type {NetInfoState} from '@react-native-community/netinfo';

let mockNetworkListener: (state: NetInfoState) => void;
const mockUnsubscribe = jest.fn();
jest.mock('@react-native-community/netinfo', () => ({__esModule: true, default: {addEventListener: (listener: typeof mockNetworkListener) => {mockNetworkListener = listener; return mockUnsubscribe;}}}));
jest.mock('../src/backend', () => ({...jest.requireActual('../src/backend'), cachedMenu: jest.fn(), health: jest.fn(), loadMenu: jest.fn(), modelVersion: jest.fn()}));
jest.mock('@react-native-async-storage/async-storage', () => require('@react-native-async-storage/async-storage/jest/async-storage-mock'));
const config = {server: 'https://example.com', code: 'ABCDEF'};
const menu = {store_code: 'ABCDEF', store_name: '테스트 매장', menu_version: 2, categories: [], items: []};
let latest: Connection;
let retry: () => void;
function Harness({settings, foreground = true}: {settings?: ServerConfig; foreground?: boolean}) {
  const hook = useBackendConnection(settings, foreground); latest = hook.connection; retry = hook.reconnect;
  return null;
}
async function settle() {await act(async () => {for (let i = 0; i < 12; i++) {await Promise.resolve();}});}
function network(connected: boolean, type = 'wifi') {act(() => mockNetworkListener({isConnected: connected, isInternetReachable: connected, type} as NetInfoState));}
beforeEach(() => {
  jest.useFakeTimers(); jest.clearAllMocks();
  (cachedMenu as jest.Mock).mockResolvedValue(undefined); (health as jest.Mock).mockResolvedValue(undefined);
  (loadMenu as jest.Mock).mockResolvedValue({menu, offline: false}); (modelVersion as jest.Mock).mockResolvedValue('2026.10.03');
});
afterEach(() => {jest.useRealTimers();});
test('startup waits for saved settings then connects without a user action', async () => {
  let root!: renderer.ReactTestRenderer;
  act(() => {root = renderer.create(<Harness/>);}); network(true); await settle(); expect(health).not.toHaveBeenCalled();
  act(() => root.update(<Harness settings={config}/>)); await settle();
  expect(latest.status).toBe('online'); expect(latest.menu).toEqual(menu); expect(latest.network).toBe('Wi-Fi');
  expect(health).toHaveBeenCalledWith(config.server, expect.anything());
  act(() => root.unmount()); expect(mockUnsubscribe).toHaveBeenCalled();
});
test('offline startup uses cached menus, reconnects on mobile data and cancels background work', async () => {
  (cachedMenu as jest.Mock).mockResolvedValue(menu);
  let root!: renderer.ReactTestRenderer;
  act(() => {root = renderer.create(<Harness settings={config}/>);}); network(false); await settle();
  expect(latest.status).toBe('offline'); expect(latest.menu).toEqual(menu); expect(health).not.toHaveBeenCalled();
  network(true, 'cellular'); await settle(); expect(latest.status).toBe('online'); expect(latest.network).toBe('모바일 데이터');
  act(() => root.update(<Harness settings={config} foreground={false}/>)); await settle();
  const count = (health as jest.Mock).mock.calls.length;
  await act(async () => {jest.advanceTimersByTime(120000);}); expect(health).toHaveBeenCalledTimes(count);
  act(() => root.update(<Harness settings={config}/>)); await settle(); expect(latest.status).toBe('online');
  act(() => root.unmount());
});
test('server failure retries automatically and a missing store stops retries without stale cache', async () => {
  (cachedMenu as jest.Mock).mockResolvedValue(menu); (health as jest.Mock).mockRejectedValueOnce(new Error('timeout'));
  let root!: renderer.ReactTestRenderer;
  act(() => {root = renderer.create(<Harness settings={config}/>);}); network(true); await settle();
  expect(latest.status).toBe('retrying'); expect(latest.menu).toEqual(menu);
  await act(async () => {jest.advanceTimersByTime(5000);}); await settle(); expect(latest.status).toBe('online');
  (loadMenu as jest.Mock).mockRejectedValueOnce(new MenuResponseError('메뉴 조회 실패 (404)'));
  act(() => retry()); await settle(); expect(latest.status).toBe('error'); expect(latest.menu).toBeUndefined();
  const count = (health as jest.Mock).mock.calls.length;
  await act(async () => {jest.advanceTimersByTime(120000);}); expect(health).toHaveBeenCalledTimes(count);
  act(() => root.unmount());
});
test('changing servers aborts old requests and prevents their delayed results replacing the new menu', async () => {
  let resolveOld!: (value: unknown) => void;
  (loadMenu as jest.Mock).mockImplementationOnce(() => new Promise(resolve => {resolveOld = resolve;}));
  let root!: renderer.ReactTestRenderer;
  act(() => {root = renderer.create(<Harness settings={config}/>);}); network(true); await settle();
  const oldSignal = (health as jest.Mock).mock.calls[0][1] as AbortSignal;
  act(() => root.update(<Harness settings={{server: 'https://new.example.com', code: 'NEWABC'}}/>)); await settle();
  expect(oldSignal.aborted).toBe(true); expect(latest.server).toBe('https://new.example.com');
  await act(async () => {resolveOld({menu: {...menu, store_name: '오래된 응답'}, offline: false});}); await settle();
  expect(latest.menu?.store_name).toBe('테스트 매장');
  act(() => root.unmount()); expect(jest.getTimerCount()).toBe(0);
});
