//
//  MediaExtension.h
//  camera_avfoundation
//
//  Created by Eittipat.K on 19/11/2565 BE.
//

#ifndef MediaExtension_h
#define MediaExtension_h

@import AVFoundation;
@import Foundation;
@import Flutter;
@import Sentry;

#import "QueueUtils.h"

@interface MediaExtension : NSObject

- (instancetype)init;

- (void)addTimestamp;

- (void)saveExtensionFilesWithCaptureFile:(NSString *)captureFile
                        completionHandler:(void (^)(void))handler;

- (void)writeTimestampToFileWithOutput:(NSString *)output
                     completionHandler:(void (^)(NSError *))handler;

- (void)writeAudioToFileWithSource:(NSString *)source
                            output:(NSString *)output
                 completionHandler:(void (^)(NSError *))handler;

@end

#endif /* MediaExtension_h */
