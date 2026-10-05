const {getDefaultConfig, mergeConfig} = require('@react-native/metro-config');

/**
 * Metro configuration
 * https://reactnative.dev/docs/metro
 *
 * @type {import('metro-config').MetroConfig}
 */
const path = require('path');
const fs = require('fs');
const installed = path.resolve(__dirname, 'node_modules/react-native-sonkkeut');
const aiModule = fs.existsSync(installed) ? installed : path.resolve(__dirname, '../sonkkeut-ai/android/react-native-sonkkeut');
const config = {
  projectRoot: __dirname,
  watchFolders: [aiModule],
  resolver: {
    nodeModulesPaths: [path.join(__dirname, 'node_modules')],
    extraNodeModules: {'react-native-sonkkeut': aiModule},
  },
};

module.exports = mergeConfig(getDefaultConfig(__dirname), config);
