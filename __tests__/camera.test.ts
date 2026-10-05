import type {CameraDevice} from 'react-native-vision-camera';
import {cameraView} from '../src/camera';

const standard = {id: 'wide', physicalDevices: ['wide-angle-camera'], minZoom: 1, neutralZoom: 1} as CameraDevice;
const ultra = {id: 'ultra', physicalDevices: ['ultra-wide-angle-camera', 'wide-angle-camera'], minZoom: 0.5, neutralZoom: 1} as CameraDevice;
test('a supported ultra-wide camera starts at minimum zoom and can be explicitly disabled', () => {
  expect(cameraView(standard, ultra, true)).toEqual({device: ultra, zoom: 0.5, ultraWide: true});
  expect(cameraView(standard, ultra, false)).toEqual({device: standard, zoom: 1, ultraWide: false});
});
test('closest-match and missing camera results fall back safely without claiming ultra-wide support', () => {
  expect(cameraView(standard, standard, true)).toEqual({device: standard, zoom: 1, ultraWide: false});
  expect(cameraView(undefined, undefined, true)).toEqual({device: undefined, zoom: 1, ultraWide: false});
});
