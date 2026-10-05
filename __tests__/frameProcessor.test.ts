import {VisionCameraProxy} from 'react-native-vision-camera';
import type {Frame} from 'react-native-vision-camera';
import {sonkkeutProcess} from 'react-native-sonkkeut';

jest.mock('react-native', () => ({NativeModules: {Sonkkeut: {}}, Platform: {OS: 'android'}, NativeEventEmitter: jest.fn()}));
jest.mock('react-native-vision-camera', () => ({
  useFrameProcessor: jest.fn(),
  VisionCameraProxy: {initFrameProcessorPlugin: jest.fn(() => ({
    // Match VisionCamera 4.6.4's JSI contract: a provided second argument must be an object.
    call: jest.fn((...args: unknown[]) => {
      if (args.length > 1 && (args[1] === undefined || args[1] === null)) {
        throw new Error('Value is undefined, expected an Object');
      }
      return {found: false};
    }),
  }))},
}));

const frame = {} as Frame;
const pluginCall = (VisionCameraProxy.initFrameProcessorPlugin as jest.Mock).mock.results[0].value.call as jest.Mock;
beforeEach(() => {pluginCall.mockClear();});

test('camera frames without a rotation override reach the native plugin without crashing', () => {
  expect(sonkkeutProcess(frame)).toEqual({found: false});
  expect(sonkkeutProcess(frame, undefined)).toEqual({found: false});
  expect(pluginCall.mock.calls).toEqual([[frame], [frame]]);
});

test('an explicit rotation, including zero, remains available to the native frame processor', () => {
  expect(sonkkeutProcess(frame, 0)).toEqual({found: false});
  expect(sonkkeutProcess(frame, 90)).toEqual({found: false});
  expect(pluginCall.mock.calls).toEqual([[frame, {rotation: 0}], [frame, {rotation: 90}]]);
});
