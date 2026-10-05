import AsyncStorage from '@react-native-async-storage/async-storage';
import {URL} from 'react-native-url-polyfill';
import type {MenuItem} from './domain';

export interface Menu {store_code: string; store_name: string; menu_version: number; categories: string[]; items: MenuItem[]}
export interface Step {screen_type: string; target_kind: string; result: string; reach_s?: number; hints: number; fail_reason?: string}
export interface Usage {store_code?: string; app_version: string; model_version: string; completed: boolean; duration_s: number; steps: Step[]; event_id: string}
export const DEFAULT_MENU: MenuItem[] = [
  {name: '아메리카노', price: 4500, category: '커피', aliases: ['아아'], sold_out: false},
  {name: '카페라떼', price: 5000, category: '커피', aliases: ['라떼'], sold_out: false},
  {name: '바닐라라떼', price: 5500, category: '커피', aliases: [], sold_out: false},
];

export function validateBaseUrl(value: string) {
  const url = new URL(value.trim());
  const local = ['localhost', '127.0.0.1', '10.0.2.2'].includes(url.hostname);
  if (url.protocol !== 'https:' && !(url.protocol === 'http:' && local)) {throw new Error('서버 주소는 HTTPS 주소를 사용해 주세요.');}
  if (url.username || url.password || url.search || url.hash) {throw new Error('서버 주소 형식이 올바르지 않습니다.');}
  return url.toString().replace(/\/$/, '');
}

async function request<T>(base: string, path: string, read: (response: Response) => Promise<T>, init?: RequestInit) {
  const controller = new AbortController();
  const cancel = () => controller.abort();
  init?.signal?.addEventListener('abort', cancel);
  if (init?.signal?.aborted) {controller.abort();}
  const timeout = setTimeout(() => controller.abort(), 8000);
  try {return await read(await fetch(base + path, {...init, signal: controller.signal}));}
  finally {clearTimeout(timeout); init?.signal?.removeEventListener('abort', cancel);}
}

export class MenuResponseError extends Error {
  constructor(message: string, readonly retryable = false) {super(message);}
}

function isMenu(value: unknown, code: string): value is Menu {
  if (!value || typeof value !== 'object') {return false;}
  const m = value as Menu;
  return m.store_code === code && typeof m.store_name === 'string' && !!m.store_name.trim()
    && Number.isInteger(m.menu_version) && m.menu_version >= 0
    && Array.isArray(m.categories) && m.categories.every(c => typeof c === 'string')
    && Array.isArray(m.items) && m.items.every(item => item && typeof item.name === 'string' && !!item.name.trim()
      && typeof item.sold_out === 'boolean' && Array.isArray(item.aliases) && item.aliases.every(a => typeof a === 'string')
      && (item.price == null || (Number.isInteger(item.price) && item.price >= 0 && item.price <= 10000000))
      && (item.options == null || (Array.isArray(item.options) && item.options.every(o => o && typeof o.group === 'string'
        && Array.isArray(o.values) && o.values.every(v => typeof v === 'string')))));
}

export async function cachedMenu(base: string, code: string): Promise<Menu | undefined> {
  try {
    const raw = await AsyncStorage.getItem(`menu:${validateBaseUrl(base)}:${code.trim().toUpperCase()}`);
    const parsed = raw ? JSON.parse(raw) : undefined;
    return parsed && isMenu(parsed.menu, code.trim().toUpperCase()) ? parsed.menu : undefined;
  } catch (_) {return undefined;}
}

export async function loadMenu(base: string, code: string, signal?: AbortSignal): Promise<{menu: Menu; offline: boolean}> {
  base = validateBaseUrl(base);
  code = code.trim().toUpperCase();
  if (!/^[A-Z2-9]{6}$/.test(code)) {throw new Error('매장 코드는 6자리입니다.');}
  const key = `menu:${base}:${code}`;
  let cache: {etag?: string; menu: Menu} | undefined;
  try {
    const cached = await AsyncStorage.getItem(key);
    const parsed = cached ? JSON.parse(cached) : undefined;
    if (parsed && isMenu(parsed.menu, code)) {cache = {menu: parsed.menu, etag: typeof parsed.etag === 'string' ? parsed.etag : undefined};}
  } catch (_) {} // A damaged or unavailable cache must not prevent an online lookup.
  try {
    return await request(base, `/api/stores/${code}/menu`, async response => {
      if (response.status === 304 && cache) {return {menu: cache.menu, offline: false};}
      if (!response.ok) {throw new MenuResponseError(`메뉴 조회 실패 (${response.status})`, response.status >= 500 || [408, 429].includes(response.status));}
      let menu: unknown;
      try {menu = await response.json();} catch (e) {
        if ((e as Error).name === 'AbortError') {throw e;}
        throw new MenuResponseError('메뉴 응답 형식 오류');
      }
      if (!isMenu(menu, code)) {throw new MenuResponseError('매장 코드 또는 메뉴 응답 형식이 올바르지 않습니다.');}
      try {await AsyncStorage.setItem(key, JSON.stringify({menu, etag: response.headers.get('etag')}));} catch (_) {}
      return {menu, offline: false};
    }, {headers: cache?.etag ? {'If-None-Match': cache.etag} : {}, signal});
  } catch (e) {
    if (signal?.aborted) {throw e;}
    if (cache && (!(e instanceof MenuResponseError) || e.retryable)) {return {menu: cache.menu, offline: true};}
    throw e;
  }
}

export async function health(base: string, signal?: AbortSignal) {
  await request(validateBaseUrl(base), '/healthz', async response => {
    if (!response.ok || !(await response.json()).ok) {throw new Error('서버 연결에 실패했습니다.');}
  }, {signal});
}

export async function modelVersion(base: string, signal?: AbortSignal) {
  return request(validateBaseUrl(base), '/api/models/latest', async response => {
    if (!response.ok) {throw new Error('모델 정보를 확인하지 못했습니다.');}
    const version = (await response.json()).version;
    if (typeof version !== 'string' || !version.trim()) {throw new Error('모델 응답 형식 오류');}
    return version;
  }, {signal});
}

// Serialize queue access so two completed sessions cannot overwrite one another.
let pending: Promise<unknown> = Promise.resolve();
let usageGeneration = 0;
let usageController: AbortController | undefined;
async function readUsageQueue(): Promise<{base: string; usage: Usage}[]> {
  const raw = await AsyncStorage.getItem('anonymous-queue');
  try {
    const queue = raw ? JSON.parse(raw) : [];
    if (!Array.isArray(queue)) {return [];}
    return queue.filter(item => item && typeof item.base === 'string' && item.usage && typeof item.usage === 'object');
  } catch (_) {return [];}
}
export function enqueueUsage(base: string, usage: Usage): Promise<void> {
  const generation = usageGeneration;
  const job = pending.then(async () => {
    if (generation !== usageGeneration) {return;}
    const queue = await readUsageQueue();
    if (generation !== usageGeneration) {return;}
    queue.push({base: validateBaseUrl(base), usage});
    await AsyncStorage.setItem('anonymous-queue', JSON.stringify(queue.slice(-50)));
    if (generation === usageGeneration) {await flushInternal();}
  });
  pending = job.catch(() => {});
  return job;
}
async function flushInternal() {
  const generation = usageGeneration;
  const queue = await readUsageQueue();
  while (queue.length && generation === usageGeneration) {
    const {base, usage} = queue[0];
    let normalizedBase: string;
    try {normalizedBase = validateBaseUrl(base);} catch (_) {
      queue.shift(); await AsyncStorage.setItem('anonymous-queue', JSON.stringify(queue)); continue;
    }
    try {
      usageController = new AbortController();
      const res = await request(normalizedBase, '/api/stats/sessions', async response => response,
        {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(usage), signal: usageController.signal});
      // Invalid data cannot succeed on retry. Discard it so later sessions can upload.
      const permanentFailure = res.status >= 400 && res.status < 500 && ![408, 429].includes(res.status);
      if (!res.ok && !permanentFailure) {break;}
    } catch (_) {break;} finally {usageController = undefined;}
    queue.shift();
    await AsyncStorage.setItem('anonymous-queue', JSON.stringify(queue));
  }
}
export function flushUsage(): Promise<void> {
  const generation = usageGeneration;
  const job = pending.then(() => generation === usageGeneration ? flushInternal() : undefined);
  pending = job.catch(() => {});
  return job;
}

export function clearUsage(): Promise<void> {
  usageGeneration++;
  usageController?.abort();
  const job = pending.then(() => AsyncStorage.removeItem('anonymous-queue'));
  pending = job.catch(() => {});
  return job;
}
