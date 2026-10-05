import {useEffect, useState} from 'react';
import NetInfo from '@react-native-community/netinfo';
import {cachedMenu, health, loadMenu, MenuResponseError, modelVersion, validateBaseUrl} from './backend';
import type {Menu} from './backend';

export interface ServerConfig {server: string; code: string}
export interface Connection {
  status: 'checking' | 'online' | 'offline' | 'retrying' | 'error';
  network: string;
  message: string;
  menu?: Menu;
  server?: string;
  modelVersion?: string;
  checkedAt?: number;
  configKey?: string;
}

// Network presence alone is not proof that the deployed backend is reachable.
export function useBackendConnection(config: ServerConfig | undefined, foreground: boolean) {
  const [network, setNetwork] = useState<{available: boolean | null; label: string}>({available: null, label: '네트워크 확인 중'});
  const [refresh, setRefresh] = useState(0);
  const [connection, setConnection] = useState<Connection>({status: 'checking', network: '네트워크 확인 중', message: '저장된 설정을 불러오고 있습니다'});
  useEffect(() => NetInfo.addEventListener(state => {
    const available = state.isConnected === false || state.isInternetReachable === false ? false : state.isConnected === true ? true : null;
    const label = available == null ? '네트워크 확인 중' : !available ? '인터넷 연결 없음' : state.type === 'wifi' ? 'Wi-Fi' : state.type === 'cellular' ? '모바일 데이터' : '인터넷';
    setNetwork(current => current.available === available && current.label === label ? current : {available, label});
  }), []);

  const server = config?.server;
  const code = config?.code;
  useEffect(() => {
    if (server == null || code == null) {return;}
    let disposed = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let controller: AbortController | undefined;
    let menu: Menu | undefined;
    let failures = 0;
    let base: string;
    const selectedCode = code.trim().toUpperCase();
    const update = (value: Omit<Connection, 'network'>) => {
      if (!disposed) {setConnection({...value, network: network.label, configKey: JSON.stringify([server, code])});}
    };
    const schedule = (delay: number) => {
      if (!disposed) {timer = setTimeout(check, delay);}
    };
    async function check() {
      if (disposed || !foreground || network.available !== true) {return;}
      controller = new AbortController();
      const signal = controller.signal;
      update({status: failures ? 'retrying' : 'checking', server: base, menu, message: failures ? '서버에 다시 연결하고 있습니다' : '서버와 매장 메뉴를 확인하고 있습니다'});
      try {
        const [, result] = await Promise.all([health(base, signal), selectedCode ? loadMenu(base, selectedCode, signal) : undefined]);
        if (disposed || signal.aborted) {return;}
        if (result?.offline) {menu = result.menu; throw new Error('저장된 메뉴로 안내합니다');}
        menu = result?.menu;
        failures = 0;
        update({status: 'online', server: base, menu, checkedAt: Date.now(), message: menu ? `${menu.store_name} · 메뉴 ${menu.menu_version} · 자동 연결 완료` : '자동 연결 완료 · 기본 시연 메뉴'});
        // Model metadata is optional and must not prevent using a healthy menu service.
        try {
          const version = await modelVersion(base, signal);
          if (!disposed && !signal.aborted) {setConnection(current => ({...current, modelVersion: version}));}
        } catch (_) {}
        schedule(60000);
      } catch (error) {
        controller.abort();
        if (disposed) {return;}
        if (error instanceof MenuResponseError && !error.retryable) {
          update({status: 'error', server: base, message: `${error.message} · 매장 설정을 확인해 주세요`});
          return;
        }
        failures++;
        const delay = [5000, 15000, 30000, 60000][Math.min(failures - 1, 3)];
        update({status: 'retrying', server: base, menu, message: `서버 응답을 기다리고 있습니다 · ${delay / 1000}초 후 자동 재시도${menu ? ' · 저장된 메뉴 사용' : ''}`});
        schedule(delay);
      }
    }
    async function initialize() {
      try {
        base = validateBaseUrl(server!);
        if (selectedCode && !/^[A-Z2-9]{6}$/.test(selectedCode)) {throw new Error('매장 코드는 영문·숫자 6자리입니다');}
        menu = selectedCode ? await cachedMenu(base, selectedCode) : undefined;
        if (disposed) {return;}
        if (network.available !== true || !foreground) {
          update({status: network.available === false ? 'offline' : 'checking', server: base, menu,
            message: network.available === false ? `인터넷 연결을 기다립니다${menu ? ' · 저장된 메뉴 사용' : ' · 기본 시연 메뉴 사용'}` : '네트워크 연결을 확인하고 있습니다'});
          return;
        }
        await check();
      } catch (error) {
        update({status: 'error', message: (error as Error).message});
      }
    }
    initialize();
    return () => {disposed = true; controller?.abort(); if (timer) {clearTimeout(timer);}};
  }, [server, code, foreground, network, refresh]);
  return {connection, reconnect: () => setRefresh(n => n + 1)};
}
