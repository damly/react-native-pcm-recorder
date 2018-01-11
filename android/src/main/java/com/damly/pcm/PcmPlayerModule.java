package com.damly.pcm;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Created by damly on 2018/1/10.
 */

public class PcmPlayerModule extends ReactContextBaseJavaModule {
    public static final String TAG = "PcmRecorderModule";
    private AudioTrack mAudioTrack;
    private boolean isPlaying = false;
    private String mCurrentPlayFile;
    private ReadableMap mPlayingSettings;

    public PcmPlayerModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return "PcmPlayerModule";
    }

    private int getAudioSystem(String audioStream) {

        switch (audioStream) {
            case "StreamVoiceCall":
                return AudioManager.STREAM_VOICE_CALL;
            case "StreamSystem":
                return AudioManager.STREAM_SYSTEM;
            case "StreamRing":
                return AudioManager.STREAM_RING;
            case "StreamMusic":
                return AudioManager.STREAM_MUSIC;
            case "StreamAlarm":
                return AudioManager.STREAM_ALARM;
            case "StreamNotification":
                return AudioManager.STREAM_NOTIFICATION;
            case "StreamDtmf":
                return AudioManager.STREAM_DTMF;
            case "Default":
            default:
                return AudioManager.STREAM_MUSIC;
        }
    }

    private void StartPlay() {

        DataInputStream dis = null;
        try {
            dis = new DataInputStream(new BufferedInputStream(new FileInputStream(mCurrentPlayFile)));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        //16K采集率
        int sampleRateInHz = mPlayingSettings.getInt("SampleRate");
        if(sampleRateInHz != 8000 && sampleRateInHz != 16000) {
            sampleRateInHz = 16000;
        }
        int audioStream = this.getAudioSystem(mPlayingSettings.getString("AudioStream"));
        //最小缓存区
        int bufferSizeInBytes = AudioTrack.getMinBufferSize(sampleRateInHz, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        //创建AudioTrack对象   依次传入 :流类型、采样率（与采集的要一致）、音频通道（采集是IN 播放时OUT）、量化位数、最小缓冲区、模式
        mAudioTrack = new AudioTrack(audioStream, sampleRateInHz, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSizeInBytes, AudioTrack.MODE_STREAM);

        byte[] data = new byte[bufferSizeInBytes];
        mAudioTrack.play();
        isPlaying = true;
        while (isPlaying) {
            int i = 0;
            try {
                while (dis.available() > 0 && i < data.length) {
                    data[i] = dis.readByte();
                    i++;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            mAudioTrack.write(data, 0, data.length);

            if (i != bufferSizeInBytes) {
                break;
            }
        }
        mAudioTrack.stop();
        mAudioTrack.release();
        mAudioTrack = null;
        sendEvent("playingFinished", null);
    }

    @ReactMethod
    public void startPlaying(String filePath, ReadableMap playingSettings, Promise promise){
        mCurrentPlayFile = filePath;
        mPlayingSettings = playingSettings;

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                StartPlay();
            }
        });
        thread.start();
    }

    @ReactMethod
    public void stopPlaying(Promise promise) {
        if (!isPlaying) {
            logAndRejectPromise(promise, "INVALID_STATE", "Please call startPlaying before stopping playing");
            return;
        }
        isPlaying = false;
        promise.resolve(mCurrentPlayFile);
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
