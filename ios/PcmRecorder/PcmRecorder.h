//
//  PcmRecorder.h
//  PcmRecorder
//
//  Created by damly on 2018/1/11.
//  Copyright © 2018年 damly. All rights reserved.
//

#import <React/RCTBridgeModule.h>
#import <React/RCTLog.h>
#import <AVFoundation/AVFoundation.h>

@interface PcmRecorder : NSObject <RCTBridgeModule, AVAudioRecorderDelegate>

@end
