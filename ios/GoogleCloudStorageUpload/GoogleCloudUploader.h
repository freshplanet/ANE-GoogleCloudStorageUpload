//
//  GoogleCloudUploader.h
//  GoogleCloudStorageUpload
//
//  Copyright (c) 2013 FreshPlanet. All rights reserved.
//

#import <Foundation/Foundation.h>

@interface GoogleCloudUploader : NSObject<NSURLConnectionDataDelegate>
{
    NSMutableData *_responseData;
}

- (void) startUpload:(NSData*)mediaData withUploadURL:(NSURL*)uploadURL andUploadParams:(NSDictionary*)params;

@end
