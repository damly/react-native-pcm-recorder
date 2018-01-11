'use strict';

import React from "react";

import ReactNative, {
  NativeModules,
  NativeAppEventEmitter,
  DeviceEventEmitter,
  Platform
} from "react-native";

var PcmRecorder = NativeModules.PcmRecorderModule;
var PcmPlayer = NativeModules.PcmPlayerModule;

let AudioUtils = {};

if (Platform.OS === 'ios') {
  AudioUtils = {
    MainBundlePath: PcmRecorderModule.MainBundlePath,
    CachesDirectoryPath: PcmRecorderModule.NSCachesDirectoryPath,
    DocumentDirectoryPath: PcmRecorderModule.NSDocumentDirectoryPath,
    LibraryDirectoryPath: PcmRecorderModule.NSLibraryDirectoryPath,
  };
} else if (Platform.OS === 'android') {
  AudioUtils = {
    MainBundlePath: PcmRecorderModule.MainBundlePath,
    CachesDirectoryPath: PcmRecorderModule.CachesDirectoryPath,
    DocumentDirectoryPath: PcmRecorderModule.DocumentDirectoryPath,
    LibraryDirectoryPath: PcmRecorderModule.LibraryDirectoryPath,
    PicturesDirectoryPath: PcmRecorderModule.PicturesDirectoryPath,
    MusicDirectoryPath: PcmRecorderModule.MusicDirectoryPath,
    DownloadsDirectoryPath: PcmRecorderModule.DownloadsDirectoryPath
  };
}

module.exports = {PcmRecorder, PcmPlayer, AudioUtils};