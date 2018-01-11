package com.damly.pcm;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by damly on 2018/1/10.
 */

public class PcmRecorderModule extends ReactContextBaseJavaModule {

    public static final String TAG = "PcmRecorderModule";
    private static final String DocumentDirectoryPath = "DocumentDirectoryPath";
    private static final String PicturesDirectoryPath = "PicturesDirectoryPath";
    private static final String MainBundlePath = "MainBundlePath";
    private static final String CachesDirectoryPath = "CachesDirectoryPath";
    private static final String LibraryDirectoryPath = "LibraryDirectoryPath";
    private static final String MusicDirectoryPath = "MusicDirectoryPath";
    private static final String DownloadsDirectoryPath = "DownloadsDirectoryPath";

    private String mCurrentOutputFile;
    private boolean isRecording = false;
    private Timer timer;
    private File mRecordFile;
    private int mRecorderSecondsElapsed = 0;
    private ReadableMap mRecordingSettings;
    private AudioRecord mAudioRecord;
    private double mVolume = 0.0;
    private int mProgressUpdateInterval = 50;

    public PcmRecorderModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return "PcmRecorderModule";
    }

    @Override
    public Map<String, Object> getConstants() {
        Map<String, Object> constants = new HashMap<>();
        constants.put(DocumentDirectoryPath, this.getReactApplicationContext().getFilesDir().getAbsolutePath());
        constants.put(PicturesDirectoryPath, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath());
        constants.put(MainBundlePath, "");
        constants.put(CachesDirectoryPath, this.getReactApplicationContext().getCacheDir().getAbsolutePath());
        constants.put(LibraryDirectoryPath, "");
        constants.put(MusicDirectoryPath, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath());
        constants.put(DownloadsDirectoryPath, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath());
        return constants;
    }

    private int getAudioSourceFromString(String audioSource) {
        switch (audioSource) {
            case "Mic":
                return MediaRecorder.AudioSource.MIC;
            case "voiceUpLink":
                return MediaRecorder.AudioSource.VOICE_UPLINK;
            case "VoiceDownLink":
                return MediaRecorder.AudioSource.VOICE_DOWNLINK;
            case "VoiceCall":
                return MediaRecorder.AudioSource.VOICE_CALL;
            case "VoiceRecognition":
                return MediaRecorder.AudioSource.VOICE_RECOGNITION;
            case "VoiceCommunication":
                return MediaRecorder.AudioSource.VOICE_COMMUNICATION;
            case "Default":
            default:
                return MediaRecorder.AudioSource.DEFAULT;
        }
    }

    private void StartRecord() {
        Log.i(TAG,"开始录音");

        //16K采集率
        int frequency = mRecordingSettings.getInt("SampleRate");
        if(frequency != 8000 && frequency != 16000) {
            frequency = 16000;
        }

        Log.e(TAG, "录制采样率: "+frequency);

        int audioSource = this.getAudioSourceFromString(mRecordingSettings.getString("AudioSource"));

        //格式
        int channelConfiguration = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        //16Bit
        int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;

        try {
            OutputStream os = new FileOutputStream(mRecordFile);
            BufferedOutputStream bos = new BufferedOutputStream(os);
            DataOutputStream dos = new DataOutputStream(bos);
            int bufferSize = AudioRecord.getMinBufferSize(frequency, channelConfiguration, audioEncoding);
            mAudioRecord = new AudioRecord(audioSource, frequency, channelConfiguration, audioEncoding, bufferSize);

            short[] buffer = new short[bufferSize];
            mAudioRecord.startRecording();
            Log.i(TAG, "开始录音");
            isRecording = true;
            mVolume = 0.0;
            while (isRecording) {
                int bufferReadResult = mAudioRecord.read(buffer, 0, bufferSize);
                long v = 0;
                for (int i = 0; i < bufferReadResult; i++) {
                    dos.writeShort(buffer[i]);
                    v += buffer[i] * buffer[i];
                }
                double mean = v / (double) bufferReadResult;
                mVolume = 10 * Math.log10(mean);


            }
            mAudioRecord.stop();
            mAudioRecord = null;
            dos.close();
            sendEvent("recordingFinished", null);
        } catch (Throwable t) {
            Log.e(TAG, "录音失败");
        }
    }

    @ReactMethod
    public void startRecording(String recordingPath, ReadableMap recordingSettings, Promise promise) {
        if (isRecording){
            logAndRejectPromise(promise, "INVALID_STATE", "Please call stopRecording before starting recording");
            return;
        }

        mCurrentOutputFile = recordingPath;
        mRecordingSettings = recordingSettings;
        mRecordFile = new File(recordingPath);
        if (mRecordFile.exists())
            mRecordFile.delete();
        try {
            mRecordFile.createNewFile();
        } catch (IOException e) {
            logAndRejectPromise(promise, "INVALID_STATE", "Create file fail, Please check the file path.");
        }

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                StartRecord();
            }
        });
        thread.start();
        promise.resolve(mCurrentOutputFile);
    }

    @ReactMethod
    public void stopRecording(Promise promise){
        if (!isRecording){
            logAndRejectPromise(promise, "INVALID_STATE", "Please call startRecording before stopping recording");
            return;
        }
        isRecording = false;
        this.stopTimer();
        promise.resolve(mCurrentOutputFile);
    }

    private void startTimer(){
        stopTimer();
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                double currentTime = (mRecorderSecondsElapsed * mProgressUpdateInterval) / 1000.00;
                WritableMap body = Arguments.createMap();
                body.putDouble("currentTime", currentTime);
                body.putDouble("currentMetering", mVolume);
                sendEvent("recordingProgress", body);
                mRecorderSecondsElapsed++;
            }
        }, 0, mProgressUpdateInterval);
    }

    private void stopTimer(){
        mRecorderSecondsElapsed = 0;
        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
        }
    }

    private void sendEvent(String eventName, Object params) {
        getReactApplicationContext()
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }

    private void logAndRejectPromise(Promise promise, String errorCode, String errorMessage) {
        Log.e(TAG, errorMessage);
        promise.reject(errorCode, errorMessage);
    }
}
