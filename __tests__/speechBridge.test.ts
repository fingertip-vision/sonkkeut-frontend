import {Sonkkeut} from 'react-native-sonkkeut';
import {NativeModules} from 'react-native';
jest.mock('react-native', () => ({NativeModules: {Sonkkeut: {menuRagCatalog: jest.fn(), finishListening: jest.fn()}}, Platform: {OS: 'android'}}));
jest.mock('react-native-vision-camera', () => ({VisionCameraProxy: {initFrameProcessorPlugin: () => null}}));

test('retrieval uses the scoped SQLite snapshot returned by native code', async () => {
  const requested = [{name: '아메리카노', aliases: [], sold_out: false}];
  const stored = [{name: '바닐라라떼', aliases: [], sold_out: false}];
  NativeModules.Sonkkeut.menuRagCatalog.mockResolvedValue(JSON.stringify(stored));
  const result = await Sonkkeut.correctMenuSpeech('바닐라떼 두 잔', 'server/store/v2', requested);
  expect(NativeModules.Sonkkeut.menuRagCatalog).toHaveBeenCalledWith('server/store/v2', JSON.stringify(requested));
  expect(result.text).toBe('바닐라라떼 두 잔');
  expect(result.original).toBe('바닐라떼 두 잔');
});

test('recording completion forwards to native without cancelling inference', () => {
  Sonkkeut.finishListening();
  expect(NativeModules.Sonkkeut.finishListening).toHaveBeenCalledTimes(1);
});
