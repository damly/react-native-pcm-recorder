//
//  PcmRecorder.m
//  PcmRecorder
//
//  Created by damly on 2018/1/11.
//  Copyright © 2018年 damly. All rights reserved.
//

#import "PcmRecorder.h"

#import "PcmRecorder.h"
#import <React/RCTConvert.h>
#import <React/RCTBridge.h>
#import <React/RCTUtils.h>
#import <React/RCTEventDispatcher.h>
#import <AVFoundation/AVFoundation.h>

NSString *const AudioRecorderEventProgress = @"recordingProgress";
NSString *const AudioRecorderEventFinished = @"recordingFinished";
NSString *const AudioPlayingEventFinished = @"playingFinished";

@implementation PcmRecorder {
    
    AVAudioRecorder *_audioRecorder;
    AVAudioPlayer *_audioPlayer;
    
    NSTimeInterval _currentTime;
    id _progressUpdateTimer;
    int _progressUpdateInterval;
    NSDate *_prevProgressUpdateTime;
    NSURL *_audioFileURL;
    NSNumber *_audioQuality;
    NSNumber *_audioEncoding;
    NSNumber *_audioChannels;
    NSNumber *_audioSampleRate;
    AVAudioSession *_recordSession;
    BOOL _meteringEnabled;
    BOOL _measurementMode;
}

@synthesize bridge = _bridge;

RCT_EXPORT_MODULE();

- (void)sendProgressUpdate {
    if (_audioRecorder && _audioRecorder.recording) {
        _currentTime = _audioRecorder.currentTime;
    } else {
        return;
    }
    
    if (_prevProgressUpdateTime == nil ||
        (([_prevProgressUpdateTime timeIntervalSinceNow] * -1000.0) >= _progressUpdateInterval)) {
        NSMutableDictionary *body = [[NSMutableDictionary alloc] init];
        [body setObject:[NSNumber numberWithFloat:_currentTime] forKey:@"currentTime"];
        if (_meteringEnabled) {
            [_audioRecorder updateMeters];
            //float _currentMetering = [_audioRecorder averagePowerForChannel: 0];
            
            float _currentMetering = pow(10, (0.05 * [_audioRecorder averagePowerForChannel:0]));
            _currentMetering = _currentMetering * 40.0;
            [body setObject:[NSNumber numberWithFloat:_currentMetering] forKey:@"currentMetering"];
        }
        
        [self.bridge.eventDispatcher sendAppEventWithName:AudioRecorderEventProgress body:body];
        
        _prevProgressUpdateTime = [NSDate date];
    }
}

- (void)stopProgressTimer {
    [_progressUpdateTimer invalidate];
}

- (void)startProgressTimer {
    _prevProgressUpdateTime = nil;
    
    [self stopProgressTimer];
    
    _progressUpdateTimer = [CADisplayLink displayLinkWithTarget:self selector:@selector(sendProgressUpdate)];
    [_progressUpdateTimer addToRunLoop:[NSRunLoop mainRunLoop] forMode:NSDefaultRunLoopMode];
}

- (void)audioRecorderDidFinishRecording:(AVAudioRecorder *)recorder successfully:(BOOL)flag {
    [self.bridge.eventDispatcher sendAppEventWithName:AudioRecorderEventFinished body:@{}];
}

- (void )audioPlayerDidFinishPlaying: (AVAudioPlayer *) player successfully:(BOOL ) flag {
    
    
    NSLog(@"audioPlayerDidFinishPlaying send Event AudioPlayingEventFinished");
        
    [self.bridge.eventDispatcher sendAppEventWithName:AudioPlayingEventFinished body:@{}];
}

- (NSString *) applicationDocumentsDirectory
{
    NSArray *paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES);
    NSString *basePath = ([paths count] > 0) ? [paths objectAtIndex:0] : nil;
    return basePath;
}

RCT_EXPORT_METHOD(startRecording:(NSString *)path sampleRate:(int)sampleRate quality:(NSString *)quality updateInterval:(int)updateInterval audioSource:(NSString *)audioSource withCallback:(RCTResponseSenderBlock)callback)
{
    _prevProgressUpdateTime = nil;
    [self stopProgressTimer];
    
    _audioFileURL = [NSURL fileURLWithPath:path];
    
    // Default options
    _audioQuality = [NSNumber numberWithInt:AVAudioQualityHigh];
    _audioEncoding = [NSNumber numberWithInt:kAudioFormatAppleIMA4];
    _audioChannels = [NSNumber numberWithInt:1];
    _audioSampleRate = [NSNumber numberWithFloat:16000.0];
    _meteringEnabled = YES;
    _audioEncoding =[NSNumber numberWithInt:kAudioFormatLinearPCM];
    _progressUpdateInterval = updateInterval;
    
    // Set audio quality from options
    if (quality != nil) {
        if ([quality  isEqual: @"Low"]) {
            _audioQuality =[NSNumber numberWithInt:AVAudioQualityLow];
        } else if ([quality  isEqual: @"Medium"]) {
            _audioQuality =[NSNumber numberWithInt:AVAudioQualityMedium];
        } else if ([quality  isEqual: @"High"]) {
            _audioQuality =[NSNumber numberWithInt:AVAudioQualityHigh];
        }
    }

    // Set sample rate from options
    _audioSampleRate = [NSNumber numberWithFloat:sampleRate];
    
    NSDictionary *recordSettings = [NSDictionary dictionaryWithObjectsAndKeys:
                                    _audioQuality, AVEncoderAudioQualityKey,
                                    _audioEncoding, AVFormatIDKey,
                                    _audioChannels, AVNumberOfChannelsKey,
                                    _audioSampleRate, AVSampleRateKey,
                                    nil];
    
    // Measurement mode to disable mic auto gain and high pass filters

    
    NSError *error = nil;
    
    _recordSession = [AVAudioSession sharedInstance];
    
    if (_measurementMode) {
        [_recordSession setCategory:AVAudioSessionCategoryRecord error:nil];
        [_recordSession setMode:AVAudioSessionModeMeasurement error:nil];
    }else{
        [_recordSession setCategory:AVAudioSessionCategoryMultiRoute error:nil];
    }
    
    _audioRecorder = [[AVAudioRecorder alloc]
                      initWithURL:_audioFileURL
                      settings:recordSettings
                      error:&error];
    
    _audioRecorder.meteringEnabled = _meteringEnabled;
    _audioRecorder.delegate = self;
    
    if (error) {
        NSLog(@"error: %@", [error localizedDescription]);
        // TODO: dispatch error over the bridge
        callback(@[error]);
    } else {
        [_audioRecorder prepareToRecord];
    }
    
    if (!_audioRecorder.recording) {
        [self startProgressTimer];
        [_recordSession setActive:YES error:nil];
        [_audioRecorder record];
        callback(@[]);
    }
    else {
        callback(@[@"stop the recording before recording"]);
    }
}

RCT_EXPORT_METHOD(stopRecording: (RCTResponseSenderBlock)callback)
{
    [_audioRecorder stop];
    [_recordSession setCategory:AVAudioSessionCategoryPlayback error:nil];
    _prevProgressUpdateTime = nil;
    callback(@[]);
}

RCT_EXPORT_METHOD(pauseRecording)
{
    if (_audioRecorder.recording) {
        [self stopProgressTimer];
        [_audioRecorder pause];
    }
}

RCT_EXPORT_METHOD(startPlaying:(NSString *)path sampleRate:(int)sampleRate audioStream:(NSString *)audioStream  withCallback:(RCTResponseSenderBlock)callback)
{
    if (_audioRecorder && _audioRecorder.recording) {
        NSLog(@"stop the recording before playing");
        callback(@[@"stop the recording before playing"]);
        return;
    }
    if(_audioPlayer && _audioPlayer.playing) {
        NSLog(@"stop the recording before playing");
        callback(@[@"stop the playing before playing"]);
        return;
    }
    
    NSError *error;
    NSURL *audioFileURL = [NSURL fileURLWithPath:path];
    _audioPlayer = [[AVAudioPlayer alloc]
                    initWithContentsOfURL:audioFileURL
                    error:&error];
    _audioPlayer.volume = 1;
    _audioPlayer.delegate = self;
    if (error) {
        NSLog(@"audio playback loading error: %@", [error localizedDescription]);
        // TODO: dispatch error over the bridge
        callback(false);
        callback(@[error]);
    } else {
        [_audioPlayer play];
        callback(@[]);
    }
}

RCT_EXPORT_METHOD(stopPlaying:(RCTResponseSenderBlock)callback)
{
    if (_audioPlayer.playing) {
        [_audioPlayer stop];
    }
    
    callback(@[]);
}


RCT_EXPORT_METHOD(checkAuthorizationStatus:(RCTPromiseResolveBlock)resolve reject:(__unused RCTPromiseRejectBlock)reject)
{
    AVAudioSessionRecordPermission permissionStatus = [[AVAudioSession sharedInstance] recordPermission];
    switch (permissionStatus) {
        case AVAudioSessionRecordPermissionUndetermined:
            resolve(@("undetermined"));
            break;
        case AVAudioSessionRecordPermissionDenied:
            resolve(@("denied"));
            break;
        case AVAudioSessionRecordPermissionGranted:
            resolve(@("granted"));
            break;
        default:
            reject(RCTErrorUnspecified, nil, RCTErrorWithMessage(@("Error checking device authorization status.")));
            break;
    }
}

RCT_EXPORT_METHOD(requestAuthorization:(RCTPromiseResolveBlock)resolve
                  rejecter:(__unused RCTPromiseRejectBlock)reject)
{
    [[AVAudioSession sharedInstance] requestRecordPermission:^(BOOL granted) {
        if(granted) {
            resolve(@YES);
        } else {
            resolve(@NO);
        }
    }];
}

- (NSString *)getPathForDirectory:(int)directory
{
    NSArray *paths = NSSearchPathForDirectoriesInDomains(directory, NSUserDomainMask, YES);
    return [paths firstObject];
}

- (NSDictionary *)constantsToExport
{
    return @{
             @"MainBundlePath": [[NSBundle mainBundle] bundlePath],
             @"NSCachesDirectoryPath": [self getPathForDirectory:NSCachesDirectory],
             @"NSDocumentDirectoryPath": [self getPathForDirectory:NSDocumentDirectory],
             @"NSLibraryDirectoryPath": [self getPathForDirectory:NSLibraryDirectory]
             };
}

@end

