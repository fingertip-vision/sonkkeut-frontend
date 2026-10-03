import AsyncStorage from '@react-native-async-storage/async-storage';
import {clearUsage, enqueueUsage, flushUsage, health, loadMenu, validateBaseUrl} from '../src/backend';
import type {Usage} from '../src/backend';

jest.mock('@react-native-async-storage/async-storage', () => require('@react-native-async-storage/async-storage/jest/async-storage-mock'));
const base = 'http://127.0.0.1:18080';
const code = 'BYDHTF';
const menu = {store_code: code, store_name: '카페 손끝', menu_version: 1, categories: ['커피'], items: []};
const fetchMock = jest.fn();
beforeEach(async () => {await AsyncStorage.clear(); fetchMock.mockReset(); global.fetch = fetchMock;});

test('React Native uses a complete URL parser for loopback and HTTPS settings', () => {
  expect(validateBaseUrl(' http://127.0.0.1:18080/ ')).toBe(base);
  expect(validateBaseUrl('https://sonkkeut.example.com')).toBe('https://sonkkeut.example.com');
  for (const value of ['http://example.com', 'http://127.0.0.1.example.com', 'https://user:password@example.com', 'https://example.com?key=secret', 'https://example.com#data']) {
    expect(() => validateBaseUrl(value)).toThrow();
  }
});

test('menu ETag cache survives a network outage without changing store identity', async () => {
  fetchMock.mockResolvedValueOnce({ok: true, status: 200, json: async () => menu, headers: {get: () => '"menu-1"'}});
  expect(await loadMenu(base, code)).toEqual({menu, offline: false});
  fetchMock.mockResolvedValueOnce({status: 304});
  expect(await loadMenu(base, code)).toEqual({menu, offline: false});
  expect(fetchMock.mock.calls[1][1].headers).toEqual({'If-None-Match': '"menu-1"'});
  fetchMock.mockRejectedValueOnce(new Error('offline'));
  expect(await loadMenu(base, code)).toEqual({menu, offline: true});
  fetchMock.mockRejectedValueOnce(new Error('offline'));
  await expect(loadMenu(base, 'ABCDEF')).rejects.toThrow('offline');
});

test('damaged menu cache cannot block online fetch and storage failures do not hide a fresh menu', async () => {
  await AsyncStorage.setItem(`menu:${base}:${code}`, '{damaged');
  fetchMock.mockResolvedValueOnce({ok: true, status: 200, json: async () => menu, headers: {get: () => null}});
  expect(await loadMenu(base, code)).toEqual({menu, offline: false});
  (AsyncStorage.setItem as jest.Mock).mockRejectedValueOnce(new Error('disk full'));
  fetchMock.mockResolvedValueOnce({ok: true, status: 200, json: async () => ({...menu, menu_version: 2}), headers: {get: () => null}});
  expect(await loadMenu(base, code)).toEqual({menu: {...menu, menu_version: 2}, offline: false});
});

test('deleted stores and malformed or mismatched menus cannot silently use cached data', async () => {
  await AsyncStorage.setItem(`menu:${base}:${code}`, JSON.stringify({menu}));
  fetchMock.mockResolvedValueOnce({ok: false, status: 404});
  await expect(loadMenu(base, code)).rejects.toThrow('404');
  for (const invalid of [{...menu, store_code: 'ABCDEF'}, {...menu, items: [{name: '잘못된 메뉴'}]}]) {
    fetchMock.mockResolvedValueOnce({ok: true, status: 200, json: async () => invalid});
    await expect(loadMenu(base, code)).rejects.toThrow('응답 형식');
  }
  fetchMock.mockResolvedValueOnce({ok: false, status: 503});
  expect(await loadMenu(base, code)).toEqual({menu, offline: true});
});

const usage = (event_id: string): Usage => ({app_version: '0.1.0', model_version: '2026.10.02', completed: true, duration_s: 12, steps: [], event_id});
test('simultaneous session queue writes survive an outage and retry with the same identifiers', async () => {
  fetchMock.mockRejectedValue(new Error('offline'));
  await Promise.all([enqueueUsage(base, usage('first-session')), enqueueUsage(base, usage('second-session'))]);
  const stored = JSON.parse((await AsyncStorage.getItem('anonymous-queue'))!);
  expect(stored.map((entry: {usage: Usage}) => entry.usage.event_id)).toEqual(['first-session', 'second-session']);
  fetchMock.mockResolvedValue({ok: true, status: 201});
  await flushUsage();
  expect(await AsyncStorage.getItem('anonymous-queue')).toBe('[]');
  expect(fetchMock.mock.calls.slice(-2).map(call => JSON.parse(call[1].body).event_id)).toEqual(['first-session', 'second-session']);
});

test('permanently invalid sessions do not block later sessions, and retryable errors keep the queue', async () => {
  await AsyncStorage.setItem('anonymous-queue', JSON.stringify([{base, usage: usage('invalid-first')}, {base, usage: usage('valid-second')} ]));
  fetchMock.mockResolvedValueOnce({ok: false, status: 422}).mockResolvedValueOnce({ok: true, status: 201});
  await flushUsage(); expect(await AsyncStorage.getItem('anonymous-queue')).toBe('[]');
  fetchMock.mockResolvedValueOnce({ok: false, status: 429});
  await enqueueUsage(base, usage('rate-limited'));
  expect(JSON.parse((await AsyncStorage.getItem('anonymous-queue'))!)).toHaveLength(1);
});

test('withdrawing statistics consent cancels queued sessions and an in-flight upload', async () => {
  let started!: () => void;
  const uploading = new Promise<void>(resolve => {started = resolve;});
  fetchMock.mockImplementationOnce((_url: string, init: RequestInit) => new Promise((_resolve, reject) => {
    init.signal!.addEventListener('abort', () => reject(new Error('cancelled'))); started();
  }));
  const first = enqueueUsage(base, usage('first-upload'));
  const second = enqueueUsage(base, usage('pending-upload'));
  await uploading;
  await clearUsage(); await Promise.all([first, second]);
  expect(fetchMock).toHaveBeenCalledTimes(1); expect(await AsyncStorage.getItem('anonymous-queue')).toBeNull();
  await flushUsage(); expect(fetchMock).toHaveBeenCalledTimes(1);
});

test('timeouts remain armed until the response body finishes', async () => {
  jest.useFakeTimers();
  let started!: () => void;
  const reading = new Promise<void>(resolve => {started = resolve;});
  fetchMock.mockImplementationOnce((_url: string, init: RequestInit) => Promise.resolve({ok: true,
    json: () => new Promise((_resolve, reject) => {
      init.signal!.addEventListener('abort', () => reject(new Error('body timeout'))); started();
    }),
  }));
  const request = health(base).catch(e => e);
  await reading; jest.advanceTimersByTime(8000);
  await expect(request).resolves.toMatchObject({message: 'body timeout'});
  jest.useRealTimers();
});
