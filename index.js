import {
    NativeModules,
    Platform
} from "react-native";

var PcmRecorderModule = NativeModules.PcmRecorder;

let AudioUtils = {};


var PcmRecorder = {

    startRecording: function(path, options, callback) {

        if(typeof callback !== "function") {
            callback = ()=>{}
        }

        var defaultOptions = {
            SampleRate: 16000.0,
            AudioQuality: 'High',
            UpdateInterval: 50,
            AudioSource: 'Mic'
        };

        var recordingOptions = {...defaultOptions, ...options};

        if (Platform.OS === 'ios') {
            PcmRecorderModule.startRecording(
                path,
                recordingOptions.SampleRate,
                recordingOptions.AudioQuality,
                recordingOptions.UpdateInterval,
                recordingOptions.AudioSource,
                callback
            );
        } else {
            PcmRecorderModule.startRecording(path, recordingOptions, callback);
        }
    },

    stopRecording: function(callback) {
        if(typeof callback !== "function") {
            callback = ()=>{}
        }
        PcmRecorderModule.stopRecording(callback);
    },

    startPlaying: function(path, options, callback) {

        if(typeof callback !== "function") {
            callback = ()=>{}
        }

        var defaultOptions = {
            SampleRate: 16000.0,
            AudioStream: 'StreamMusic'
        };

        var playingOptions = {...defaultOptions, ...options};

        if (Platform.OS === 'ios') {
            PcmRecorderModule.startPlaying(
                path,
                playingOptions.SampleRate,
                playingOptions.AudioStream,
                callback
            );
        } else {
            PcmRecorderModule.startPlaying(path, playingOptions, callback);
        }
    },

    stopPlaying: function(callback) {
        if(typeof callback !== "function") {
            callback = ()=>{}
        }
         PcmRecorderModule.stopPlaying(callback);
    },

    // checkAuthorizationStatus: PcmRecorderModule.checkAuthorizationStatus,
    // requestAuthorization: PcmRecorderModule.requestAuthorization,
    removeListeners: function() {
        if (this.progressSubscription) this.progressSubscription.remove();
        if (this.finishedSubscription) this.finishedSubscription.remove();
    },
};

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

module.exports = {PcmRecorder, AudioUtils};