import {restoreSettings} from '../src/settings';
import {BACKEND_URL, DEFAULT_STORE_CODE} from '../src/config';

test('existing shipped demo settings migrate to the AWS store without enabling consent', () => {
  expect(restoreSettings({server: 'https://sonkkeutgil-mvp-oct02.enterenter0311.chatgpt.site/', code: 'qxwbw2', statsEnabled: false}))
    .toEqual({server: BACKEND_URL, code: DEFAULT_STORE_CODE, statsEnabled: false});
});

test('custom servers, custom stores, and an intentionally empty store code stay unchanged', () => {
  for (const saved of [
    {server: 'http://127.0.0.1:18080', code: 'BYDHTF', statsEnabled: true},
    {server: 'https://sonkkeutgil-mvp-oct02.enterenter0311.chatgpt.site', code: 'ABCDEF', statsEnabled: true},
    {server: BACKEND_URL, code: '', statsEnabled: false},
  ]) {expect(restoreSettings(saved)).toEqual(saved);}
  expect(restoreSettings(null)).toEqual({server: BACKEND_URL, code: DEFAULT_STORE_CODE, statsEnabled: false});
});
