package com.damly.pcm;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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
    private boolean isWorking = false;
    private Timer timer;
    private File mRecordFile;
    private int mRecorderSecondsElapsed = 0;
    private AudioRecord mAudioRecord;
    private double mVolume = 0.0f;
    private int mProgressUpdateInterval = 50;
    private DataOutputStream mDataOutputStream;
    private int mBufferSize;
    private Handler mHandler;
    private int mFrequency = 16000;
    private int mAudioSource = MediaRecorder.AudioSource.MIC;

    private DataInputStream mDataInputStream;
    private AudioTrack mAudioTrack;
    private int mAudioStream = AudioManager.STREAM_MUSIC;


    public PcmRecorderModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return "PcmRecorder";
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

    @ReactMethod
    public void checkAuthorizationStatus(Promise promise) {
        int permissionCheck = ContextCompat.checkSelfPermission(getCurrentActivity(),
                Manifest.permission.RECORD_AUDIO);
        boolean permissionGranted = permissionCheck == PackageManager.PERMISSION_GRANTED;
        promise.resolve(permissionGranted);
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

    private double calculateVolume(byte[] buffer) {
        double sumVolume = 0.0;
        double avgVolume = 0.0;
        double volume = 0.0;

        for (int i = 0; i < buffer.length; i += 2) {
            int v1 = buffer[i] & 0xFF;
            int v2 = buffer[i + 1] & 0xFF;
            int temp = v1 + (v2 << 8);// 小端
            if (temp >= 0x8000) {
                temp = 0xffff - temp;
            }
            sumVolume += Math.abs(temp);
        }

        avgVolume = sumVolume / buffer.length / 2;
        volume = Math.log10(1 + avgVolume) * 10;

        return volume;

    }

    private void StartRecord() {
        try {

            byte[] buffer = new byte[mBufferSize];
            mAudioRecord.startRecording();
            isWorking = true;
            mVolume = 0.0;

            while (isWorking) {
                int bufferReadResult = mAudioRecord.read(buffer, 0, mBufferSize);
                if (bufferReadResult == AudioRecord.ERROR_INVALID_OPERATION || bufferReadResult == AudioRecord.ERROR_BAD_VALUE) {
                    continue;
                }
                if (bufferReadResult != 0 && bufferReadResult != -1) {
                    mDataOutputStream.write(buffer, 0, bufferReadResult);
                    Bundle bundle = new Bundle();
                    bundle.putDouble("volume", calculateVolume(buffer));
                    Message msg = mHandler.obtainMessage();
                    msg.setData(bundle);
                    mHandler.sendMessage(msg);
                }
            }
            mAudioRecord.stop();
            mAudioRecord = null;
            if (mDataOutputStream != null) {
                mDataOutputStream.flush();
                mDataOutputStream.close();
                mDataOutputStream = null;
            }

            sendEvent("recordingFinished", null);
        } catch (Throwable t) {
            Log.e(TAG, "录音失败");
        }
    }

    private boolean prepareRecording(String recordingPath, ReadableMap recordingSettings) {

        try{
            mProgressUpdateInterval = recordingSettings.getInt("UpdateInterval");
        }catch (Exception e) {
            mProgressUpdateInterval = 50;
        }
        try{
            mFrequency = (int)recordingSettings.getDouble("SampleRate");
        }catch (Exception e) {
            mFrequency = 16000;
        }
        try{
            mAudioSource = this.getAudioSourceFromString(recordingSettings.getString("AudioSource"));
        }catch (Exception e) {
            mAudioSource = MediaRecorder.AudioSource.MIC;
        }

        mCurrentOutputFile = recordingPath;
        mRecordFile = new File(recordingPath);
        if (mRecordFile.exists())
            mRecordFile.delete();
        try {
            mRecordFile.createNewFile();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void startTimer() {
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

    private void stopTimer() {
        mRecorderSecondsElapsed = 0;
        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
        }
    }

    private void doCallBack(final Callback callback, String error) {
        try {
            callback.invoke(error);
        } catch (Exception e) {
        }
    }

    @ReactMethod
    public void startRecording(String recordingPath, ReadableMap recordingSettings, final Callback callback) {
        if (isWorking) {
            this.doCallBack(callback, "The recorder is working");
            return;
        }

        if(!this.prepareRecording(recordingPath, recordingSettings)){
            this.doCallBack(callback, "Prepare for recording is fail.");
            return;
        }

        try {
            mDataOutputStream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(mRecordFile)));
            mBufferSize = AudioRecord.getMinBufferSize(mFrequency, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            mAudioRecord = new AudioRecord(mAudioSource, mFrequency, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, mBufferSize);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            this.doCallBack(callback, "Create file for recording is fail.");
            return;
        }

        Log.i(TAG, "开始录音");
        Log.e(TAG, "录制采样率: " + mFrequency);

        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                mVolume = msg.getData().getDouble("volume");
            }
        };

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                StartRecord();
                isWorking = false;
            }
        });
        thread.start();
        startTimer();
        this.doCallBack(callback, null);
    }

    @ReactMethod
    public void stopRecording(final Callback callback) {
        if (!isWorking) {
            this.doCallBack(callback, null);
            return;
        }
        isWorking = false;
        this.stopTimer();
        this.doCallBack(callback, null);
    }


    private boolean preparePlaying(String recordingPath, ReadableMap recordingSettings) {

        try{
            mAudioStream = this.getAudioSystem(recordingSettings.getString("AudioStream"));
        }catch (Exception e) {
            mAudioStream = AudioManager.STREAM_MUSIC;
        }
        try{
            mFrequency= (int)recordingSettings.getDouble("SampleRate");
        }catch (Exception e) {
            mFrequency = 16000;
        }
        try {
            mDataInputStream  = new DataInputStream(new BufferedInputStream(new FileInputStream(recordingPath)));
            return true;
        } catch (IOException e) {
            return false;
        }
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

        try {
            Log.e(TAG, "播放采样率: " + mFrequency);

            byte[] data = new byte[mBufferSize];
            mAudioTrack.play();
            isWorking = true;
            while (isWorking) {
                int i = 0;
                while (mDataInputStream.available() > 0 && i < data.length) {
                    data[i] = mDataInputStream.readByte();
                    i++;
                }
                mAudioTrack.write(data, 0, data.length);

                if (i != mBufferSize) {
                    break;
                }
            }
            mAudioTrack.stop();
            mAudioTrack.release();
            mAudioTrack = null;
            mDataInputStream.close();
            mDataInputStream = null;

            sendEvent("playingFinished", null);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @ReactMethod
    public void startPlaying(String filePath, ReadableMap playingSettings, final Callback callback) {

        if(!this.preparePlaying(filePath, playingSettings)) {
            this.doCallBack(callback, "Prepare for playing is fail.");
            return;
        }

        //最小缓存区
        mBufferSize = AudioTrack.getMinBufferSize(mFrequency, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        //创建AudioTrack对象   依次传入 :流类型、采样率（与采集的要一致）、音频通道（采集是IN 播放时OUT）、量化位数、最小缓冲区、模式
        mAudioTrack = new AudioTrack(mAudioStream, mFrequency, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, mBufferSize, AudioTrack.MODE_STREAM);


        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                StartPlay();
                isWorking = false;
            }
        });
        thread.start();

        this.doCallBack(callback, null);
    }

    @ReactMethod
    public void stopPlaying(final Callback callback) {
        if (!isWorking) {
            this.doCallBack(callback, null);
            return;
        }
        isWorking = false;
        this.doCallBack(callback, null);
    }

    private void sendEvent(String eventName, Object params) {
        getReactApplicationContext()
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }
}
