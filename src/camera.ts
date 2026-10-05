import type {CameraDevice} from 'react-native-vision-camera';

export function cameraView(standard: CameraDevice | undefined, wider: CameraDevice | undefined, enabled: boolean) {
  const hasUltraWide = wider?.physicalDevices.includes('ultra-wide-angle-camera') === true;
  const device = enabled && hasUltraWide ? wider : standard;
  return {device, zoom: device ? enabled && hasUltraWide ? device.minZoom : device.neutralZoom : 1,
    ultraWide: enabled && hasUltraWide};
}
