import {BACKEND_URL, DEFAULT_STORE_CODE} from './config';

export function restoreSettings(value: unknown) {
  const saved = value && typeof value === 'object' ? value as Record<string, unknown> : {};
  let server = typeof saved.server === 'string' ? saved.server : BACKEND_URL;
  let code = typeof saved.code === 'string' ? saved.code : DEFAULT_STORE_CODE;
  // Only migrate the previous shipped demo pair. A user's custom server/store stays intact.
  if (server.trim().replace(/\/$/, '') === 'https://sonkkeutgil-mvp-oct02.enterenter0311.chatgpt.site'
    && code.trim().toUpperCase() === 'QXWBW2') {
    server = BACKEND_URL;
    code = DEFAULT_STORE_CODE;
  }
  return {server, code, statsEnabled: saved.statsEnabled === true};
}
