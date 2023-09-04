//
//  MediaExtension.m
//  camera_avfoundation
//
//  Created by Eittipat.K on 19/11/2565 BE.
//

#import "MediaExtension.h"

@implementation MediaExtension {
  bool isDoneWritingTimestamp;
  bool isDoneWritingAudio;
  NSMutableArray<NSString*>* timestamp;
}

- (instancetype)init {
  self = [super init];
  NSAssert(self, @"super init cannot be nil");
  
  timestamp = [[NSMutableArray alloc] init];
  return self;
}

- (void)addTimestamp {
  NSTimeInterval value = [[NSDate date] timeIntervalSince1970] * 1000.0;
  NSString *timeString = [NSString stringWithFormat:@"%f", value];
  [timestamp addObject: timeString];
}


- (void)saveExtensionFilesWithCaptureFile:(NSString *)captureFile
                        completionHandler:(void (^)(void))handler {
  
  NSString *pathname = [captureFile stringByDeletingLastPathComponent];
  NSString *filename = [[captureFile lastPathComponent]stringByDeletingPathExtension];
  NSString *m4aFile = [NSString stringWithFormat:@"%@/%@.m4a",pathname,filename];
  NSString *txtFile = [NSString stringWithFormat:@"%@/%@.txt",pathname,filename];
  
  // create task handler
  void (^taskHandler)(NSError *) = ^(NSError *error){
    if(error != nil) {
      NSLog(@"Error: %@", error);
      SentryAttachment *attachment = [[SentryAttachment alloc] initWithPath:captureFile];
      [SentrySDK captureError:error withScopeBlock:^(SentryScope * _Nonnull scope) {
        [scope addAttachment:attachment];
      }];
    }
  };
  
  // create completion handler
  void (^completionHandler)(void) = ^(void) {
    if(self->isDoneWritingAudio && self->isDoneWritingTimestamp) {
      NSLog(@"Saving extension files is DONE!");
      handler();
    }
  };
  
  [self writeTimestampToFileWithOutput:txtFile completionHandler:^(NSError *error) {
    FLTEnsureToRunOnMainQueue(^{
      taskHandler(error);
      self->isDoneWritingTimestamp = true;
      completionHandler();
    });
  }];
  
  [self writeAudioToFileWithSource:captureFile output:m4aFile completionHandler:^(NSError *error) {
    FLTEnsureToRunOnMainQueue(^{
      taskHandler(error);
      self->isDoneWritingAudio = true;
      completionHandler();
    });
  }];
  
}

- (void)writeTimestampToFileWithOutput:(NSString *)output
                     completionHandler:(void (^)(NSError *))handler {
  
  NSLog(@"Writing timestamp file to %@",output);
  
  NSURL *dstURL = [NSURL fileURLWithPath:output];
  NSString *content = [timestamp componentsJoinedByString:@"\n"];
  NSError *error;
  BOOL success = [content writeToURL:dstURL
                          atomically:false
                            encoding:NSUTF8StringEncoding
                               error: &error];
  handler(success?nil:error);
}

- (void)writeAudioToFileWithSource:(NSString *)source
                            output:(NSString *)output
                 completionHandler:(void (^)(NSError *))handler {
  
  NSLog(@"Writing audio file to %@",output);
  
  NSURL *srcURL = [NSURL fileURLWithPath:source];
  NSURL *dstURL = [NSURL fileURLWithPath:output];
  
  AVAsset* srcAsset = [AVURLAsset URLAssetWithURL:srcURL options:nil];
  AVAssetExportSession* exporter = [[AVAssetExportSession alloc]
                                    initWithAsset:srcAsset
                                    presetName:AVAssetExportPresetAppleM4A];
  exporter.outputFileType = AVFileTypeAppleM4A;
  exporter.outputURL = dstURL;
  exporter.timeRange = CMTimeRangeMake(kCMTimeZero, [srcAsset duration]);
  [exporter exportAsynchronouslyWithCompletionHandler:^{
    AVAssetExportSessionStatus status = exporter.status;
    switch(status) {
      case AVAssetExportSessionStatusCompleted:
      {
        handler(nil);
        break;
      }
      case AVAssetExportSessionStatusCancelled:
      case AVAssetExportSessionStatusFailed:
      {
        handler(exporter.error);
        break;
      }
      case AVAssetExportSessionStatusWaiting:
      case AVAssetExportSessionStatusUnknown:
      case AVAssetExportSessionStatusExporting:
      default:
      {
        NSString *domain = @"MediaExtension";
        NSString *desc = [NSString stringWithFormat:@"Unhandle AVAssetExportSessionStatus %ld", status];
        NSDictionary *userInfo = [[NSDictionary alloc]
                                  initWithObjectsAndKeys:desc,
                                  @"NSLocalizedDescriptionKey", NULL];
        handler([NSError errorWithDomain:domain code:-1 userInfo:userInfo]);
        break;
      }
    }
  }];
}
@end
