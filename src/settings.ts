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
  return {server, code, statsEnabled: saved.statsEnabled === true,
    theme: saved.theme === 'light' ? 'light' as const : 'dark' as const,
    wideCamera: saved.wideCamera !== false, lowVision: saved.lowVision !== false,
    textSize: (saved.textSize === 1 || saved.textSize === 2 ? saved.textSize : 0) as 0 | 1 | 2,
    voiceEnabled: saved.voiceEnabled !== false, vibrationEnabled: saved.vibrationEnabled !== false,
    speechSpeed: (saved.speechSpeed === 0 || saved.speechSpeed === 2 ? saved.speechSpeed : 1) as 0 | 1 | 2};
}
