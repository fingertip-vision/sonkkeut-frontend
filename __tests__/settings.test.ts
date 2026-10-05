import {restoreSettings} from '../src/settings';
import {BACKEND_URL, DEFAULT_STORE_CODE} from '../src/config';

test('existing shipped demo settings migrate to the AWS store without enabling consent', () => {
  expect(restoreSettings({server: 'https://sonkkeutgil-mvp-oct02.enterenter0311.chatgpt.site/', code: 'qxwbw2', statsEnabled: false}))
    .toMatchObject({server: BACKEND_URL, code: DEFAULT_STORE_CODE, statsEnabled: false, theme: 'dark', wideCamera: true, lowVision: true});
});

test('custom servers, custom stores, and an intentionally empty store code stay unchanged', () => {
  for (const saved of [
    {server: 'http://127.0.0.1:18080', code: 'BYDHTF', statsEnabled: true},
    {server: 'https://sonkkeutgil-mvp-oct02.enterenter0311.chatgpt.site', code: 'ABCDEF', statsEnabled: true},
    {server: BACKEND_URL, code: '', statsEnabled: false},
  ]) {expect(restoreSettings(saved)).toMatchObject({...saved, theme: 'dark', wideCamera: true, lowVision: true});}
  expect(restoreSettings(null)).toMatchObject({server: BACKEND_URL, code: DEFAULT_STORE_CODE, statsEnabled: false, theme: 'dark', wideCamera: true, lowVision: true});
});

test('feedback and text preferences restore without accepting malformed speeds or toggles', () => {
  expect(restoreSettings({textSize: 2, voiceEnabled: false, vibrationEnabled: false, speechSpeed: 0})).toMatchObject({textSize: 2, voiceEnabled: false, vibrationEnabled: false, speechSpeed: 0});
  expect(restoreSettings({textSize: 99, voiceEnabled: 'false', vibrationEnabled: null, speechSpeed: -5})).toMatchObject({textSize: 0, voiceEnabled: true, vibrationEnabled: true, speechSpeed: 1});
});

test('explicit accessibility preferences persist and malformed values use accessible defaults', () => {
  expect(restoreSettings({theme: 'light', wideCamera: false, lowVision: false})).toMatchObject({theme: 'light', wideCamera: false, lowVision: false});
  expect(restoreSettings({theme: 'sepia', wideCamera: 'false', lowVision: null})).toMatchObject({theme: 'dark', wideCamera: true, lowVision: true});
});
