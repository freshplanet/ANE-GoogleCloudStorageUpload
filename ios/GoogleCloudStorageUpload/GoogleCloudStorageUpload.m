//////////////////////////////////////////////////////////////////////////////////////
//
//  Copyright 2012 Freshplanet (http://freshplanet.com | opensource@freshplanet.com)
//  
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//  
//    http://www.apache.org/licenses/LICENSE-2.0
//  
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
//  
//////////////////////////////////////////////////////////////////////////////////////

#import "GoogleCloudStorageUpload.h"
#import <AVFoundation/AVFoundation.h>
#import <MediaPlayer/MediaPlayer.h>
//#import "UIImage+Resize.h"
#import "GoogleCloudUploader.h"

FREContext GoogleCloudStorageUploadCtx = nil;

@implementation GoogleCloudStorageUpload

#pragma mark - Singleton

static GoogleCloudStorageUpload *sharedInstance = nil;

+ (GoogleCloudStorageUpload *)sharedInstance
{
    if (sharedInstance == nil)
    {
        sharedInstance = [[super allocWithZone:NULL] init];
    }
    
    return sharedInstance;
}

+ (id)allocWithZone:(NSZone *)zone
{
    return [self sharedInstance];
}

- (id)copy
{
    return self;
}

#pragma mark - GoogleCloudStorageUpload ane generic code

+ (void)dispatchEvent:(NSString *)eventName withInfo:(NSString *)info
{
    if (GoogleCloudStorageUploadCtx != nil)
    {
        FREDispatchStatusEventAsync(GoogleCloudStorageUploadCtx, (const uint8_t *)[eventName UTF8String], (const uint8_t *)[info UTF8String]);
    }
}

+ (void)log:(NSString *)message
{
    [GoogleCloudStorageUpload dispatchEvent:@"LOGGING" withInfo:message];
}

+ (void)status:(NSString*)code level:(NSString*)level
{
    FREDispatchStatusEventAsync(GoogleCloudStorageUploadCtx, (const uint8_t *)[code UTF8String], (const uint8_t *)[level UTF8String]);
}

#pragma mark - GoogleCloudStorageUpload preprocessing

+ (UIImage *) resizeImage:(UIImage *)image toMaxDimension:(CGSize)maxDimensions {
    
    // make sure that the image has the correct size
    if ( (image.size.width > maxDimensions.width || image.size.height > maxDimensions.height ) &&
        maxDimensions.width > 0 && maxDimensions.height > 0)
    {
        float reductionFactor = MAX(image.size.width / maxDimensions.width, image.size.height / maxDimensions.height);
        CGSize newSize = CGSizeMake(image.size.width/reductionFactor, image.size.height/reductionFactor);
        
        // resize the image
        UIGraphicsBeginImageContext(newSize);
        [image drawInRect:CGRectMake(0, 0, newSize.width, newSize.height)];
        image = UIGraphicsGetImageFromCurrentImageContext();
        UIGraphicsEndImageContext();
        NSLog(@"resized image to %f x %f", newSize.width, newSize.height);
    }
    return image;
}

+ (NSData *) getBytesForImageAt:(NSString *)imageJPEGPath withMaxImageSize:(CGSize)maxDimensions {
    
    // Load the image from the disk
    NSData *imageJPEGData = [NSData dataWithContentsOfFile:imageJPEGPath];
    UIImage *image = [UIImage imageWithData:imageJPEGData];
    
    NSLog(@"Loaded image from %@", imageJPEGPath);
    UIImage *resizedImage = [self resizeImage:image toMaxDimension:maxDimensions];
    
    if (image != resizedImage) {
        // JPEG compression
        imageJPEGData = UIImageJPEGRepresentation(resizedImage, 0.95);
        
    }
    
    return imageJPEGData;
}

typedef void(^exportToMP4Completion)(NSString* error, NSURL *toURL);

+ (void) exportToMP4:(NSURL *)originalMediaURL withMaxDuration:(double) maxDuration onComplete:(exportToMP4Completion)onComplete
{
    NSLog(@"Entering - exportToMP4:originalMediaURL:withExportURL originalMediaURL: %@", originalMediaURL);
    
    // create a temp url for the mp4 video
    NSURL *tmpFolderURL =[[NSURL alloc] initFileURLWithPath:NSTemporaryDirectory() isDirectory:YES];
    NSURL *toURL = [tmpFolderURL URLByAppendingPathComponent:@"uploadVideo.mp4"];
    
    // check if there was already a video at this url
    if( [[NSFileManager defaultManager] fileExistsAtPath:[toURL path]] ) {
        // remove existing video
        [[NSFileManager defaultManager] removeItemAtPath:[toURL path] error:NULL];
    }
    
    // check if the device can compress to low quality
    AVURLAsset *avAsset = [AVURLAsset URLAssetWithURL:originalMediaURL options:nil];
    NSArray *compatiblePresets = [AVAssetExportSession exportPresetsCompatibleWithAsset:avAsset];
    
    if ([compatiblePresets containsObject:AVAssetExportPresetMediumQuality])
    {
        // prepare the compression session
        AVAssetExportSession *exportSession = [[AVAssetExportSession alloc] initWithAsset:avAsset presetName:AVAssetExportPresetMediumQuality];
        exportSession.outputURL = toURL;
        exportSession.outputFileType = AVFileTypeMPEG4;
        
        exportSession.shouldOptimizeForNetworkUse = YES;
        
        if(maxDuration > 0)
        {
            // trim the video to 30s max
            CMTime start = CMTimeMakeWithSeconds(0.0, 1);
            CMTime duration = CMTimeMakeWithSeconds(maxDuration, 1);
            CMTimeRange range = CMTimeRangeMake(start, duration);
            exportSession.timeRange = range;
        }
        
        // export the video asynchronously
        [exportSession exportAsynchronouslyWithCompletionHandler:^{
            switch ([exportSession status]) {
                case AVAssetExportSessionStatusFailed:
                    NSLog(@"Export session failed: %@",[exportSession error]);
                    onComplete(@"Export session failed", toURL);
                    break;
                    
                case AVAssetExportSessionStatusCancelled:
                    NSLog(@"Export cancelled");
                    onComplete(@"Export cancelled", toURL);
                    break;
                    
                case AVAssetExportSessionStatusCompleted:
                    NSLog(@"Export successful");
                    onComplete(nil, toURL);
                    break;
                    
                default:
                    break;
            }
            
            // remove the original video
            //[[NSFileManager defaultManager] removeItemAtPath:[originalMediaURL path] error:NULL];
        }];
    }
    NSLog(@"Exiting - exportToMP4 toURL %@", toURL);
}

@end



DEFINE_ANE_FUNCTION(uploadImageToServer)
{
    NSLog(@"Entering uploadImageToServer");
    
    uint32_t stringLength;
    
    NSString *localURLPath = nil;
    NSString *uploadURLPath = nil;
    NSDictionary *params = nil;
    
    const uint8_t *localURLString;
    if (FREGetObjectAsUTF8(argv[0], &stringLength, &localURLString) == FRE_OK) {
        localURLPath = [NSString stringWithUTF8String:(const char *)localURLString];
    }
    
    const uint8_t *uploadURLString;
    if (FREGetObjectAsUTF8(argv[1], &stringLength, &uploadURLString) == FRE_OK) {
        uploadURLPath = [NSString stringWithUTF8String:(const char *)uploadURLString];
    }
    
    const uint8_t *uploadParamsString;
    if (FREGetObjectAsUTF8(argv[2], &stringLength, &uploadParamsString) == FRE_OK) {
        NSData *data = [[NSString stringWithUTF8String:(const char *)uploadParamsString] dataUsingEncoding:NSUTF8StringEncoding];
        params = [NSJSONSerialization JSONObjectWithData:data options:kNilOptions error:nil];
    }
    
    FREObject imageMaxWidthObj = argv[3];
    uint32_t imageMaxWidth;
    FREGetObjectAsUint32(imageMaxWidthObj, &imageMaxWidth);
    
    FREObject imageMaxHeightObj = argv[4];
    uint32_t imageMaxHeight;
    FREGetObjectAsUint32(imageMaxHeightObj, &imageMaxHeight);
    
    CGSize maxDimensions = CGSizeMake(imageMaxWidth, imageMaxHeight);
    
    if ( localURLPath != nil && uploadURLPath != nil && params != nil && [params count] > 0 )
    {
        NSURL *mediaURL = [NSURL fileURLWithPath:localURLPath];
        
        if([[NSFileManager defaultManager] fileExistsAtPath:mediaURL.path])
        {
            NSLog(@"File exits at %@", mediaURL.path);
            
            dispatch_queue_t queue = dispatch_queue_create("com.freshplanet.apps.MegaPop.resizeImage", NULL);
            dispatch_async(queue, ^{
                NSURL *uploadURL = [NSURL URLWithString:uploadURLPath];
                NSData *mediaData = [GoogleCloudStorageUpload getBytesForImageAt:mediaURL.path withMaxImageSize:maxDimensions];
                dispatch_async(dispatch_get_main_queue(), ^{
                    NSLog(@"Starting upload");
                    GoogleCloudUploader *uploader = [[GoogleCloudUploader alloc] init];
                    [uploader startUpload:mediaData withUploadURL:uploadURL andUploadParams:params];
                });
            });
        }
        else
        {
            NSLog(@"File does not exits at %@", mediaURL.path);
            [GoogleCloudStorageUpload status:@"FILE_UPLOAD_ERROR" level:@""];
        }
    }
    
    NSLog(@"Exiting uploadToServer");
    return nil;
}

DEFINE_ANE_FUNCTION(uploadVideoToServer)
{
    NSLog(@"Entering uploadImageToServer");
    
    uint32_t stringLength;
    
    NSString *localURLPath = nil;
    NSString *uploadURLPath = nil;
    NSDictionary *params = nil;
    
    const uint8_t *localURLString;
    if (FREGetObjectAsUTF8(argv[0], &stringLength, &localURLString) == FRE_OK) {
        localURLPath = [NSString stringWithUTF8String:(const char *)localURLString];
    }
    
    const uint8_t *uploadURLString;
    if (FREGetObjectAsUTF8(argv[1], &stringLength, &uploadURLString) == FRE_OK) {
        uploadURLPath = [NSString stringWithUTF8String:(const char *)uploadURLString];
    }
    
    const uint8_t *uploadParamsString;
    if (FREGetObjectAsUTF8(argv[2], &stringLength, &uploadParamsString) == FRE_OK) {
        NSData *data = [[NSString stringWithUTF8String:(const char *)uploadParamsString] dataUsingEncoding:NSUTF8StringEncoding];
        params = [NSJSONSerialization JSONObjectWithData:data options:kNilOptions error:nil];
    }
    
    FREObject maxDurationObj = argv[3];
    double maxDuration = -1;
    FREGetObjectAsDouble(maxDurationObj, &maxDuration);
    
    if ( localURLPath != nil && uploadURLPath != nil && params != nil && [params count] > 0 )
    {
        NSURL *mediaURL = [NSURL fileURLWithPath:localURLPath];
        
        if([[NSFileManager defaultManager] fileExistsAtPath:mediaURL.path])
        {
            NSLog(@"File exits at %@", mediaURL.path);
            
            dispatch_queue_t queue = dispatch_queue_create("com.freshplanet.apps.MegaPop.resizeVideo", NULL);
            dispatch_async(queue, ^{
                
                [GoogleCloudStorageUpload exportToMP4:mediaURL withMaxDuration:maxDuration onComplete:^(NSString *error, NSURL *toURL) {
                    if (error) NSLog(@"export failed");
                    else
                    {
                        NSURL *uploadURL = [NSURL URLWithString:uploadURLPath];
                        NSData *mediaData = [[NSFileManager defaultManager] contentsAtPath:toURL.path];
                        dispatch_async(dispatch_get_main_queue(), ^{
                            NSLog(@"Starting upload");
                            GoogleCloudUploader *uploader = [[GoogleCloudUploader alloc] init];
                            [uploader startUpload:mediaData withUploadURL:uploadURL andUploadParams:params];
                        });
                    }
                }];
            });
            
        }
        else
        {
            NSLog(@"File does not exits at %@", mediaURL.path);
        }
    }
    else {
        NSLog(@"Problem with params: localURLPath %@ uploadURLPath %@ params %@", localURLPath, uploadURLPath, params);
    }
    
    NSLog(@"Exiting uploadImageToServer");
    return nil;
}

DEFINE_ANE_FUNCTION(uploadBinaryFileToServer)
{
    NSLog(@"Entering uploadBinaryFileToServer");
    
    uint32_t stringLength;
    
    NSString *localURLPath = nil;
    NSString *uploadURLPath = nil;
    NSDictionary *params = nil;
    
    const uint8_t *localURLString;
    if (FREGetObjectAsUTF8(argv[0], &stringLength, &localURLString) == FRE_OK) {
        localURLPath = [NSString stringWithUTF8String:(const char *)localURLString];
    }
    
    const uint8_t *uploadURLString;
    if (FREGetObjectAsUTF8(argv[1], &stringLength, &uploadURLString) == FRE_OK) {
        uploadURLPath = [NSString stringWithUTF8String:(const char *)uploadURLString];
    }
    
    const uint8_t *uploadParamsString;
    if (FREGetObjectAsUTF8(argv[2], &stringLength, &uploadParamsString) == FRE_OK) {
        NSData *data = [[NSString stringWithUTF8String:(const char *)uploadParamsString] dataUsingEncoding:NSUTF8StringEncoding];
        params = [NSJSONSerialization JSONObjectWithData:data options:kNilOptions error:nil];
    }
    
    
    
    if ( localURLPath != nil && uploadURLPath != nil && params != nil && [params count] > 0 )
    {
        NSURL *mediaURL = [NSURL fileURLWithPath:localURLPath];
        
        if([[NSFileManager defaultManager] fileExistsAtPath:mediaURL.path])
        {
            NSLog(@"File exits at %@", mediaURL.path);
            
            NSURL *uploadURL = [NSURL URLWithString:uploadURLPath];
            NSData *mediaData = [NSData dataWithContentsOfFile:mediaURL.path];
            NSLog(@"Starting upload");
            GoogleCloudUploader *uploader = [[GoogleCloudUploader alloc] init];
            [uploader startUpload:mediaData withUploadURL:uploadURL andUploadParams:params];
            
        }
        else
        {
            NSLog(@"File does not exits at %@", mediaURL.path);
            [GoogleCloudStorageUpload status:@"FILE_UPLOAD_ERROR" level:@""];
        }
    }
    else {
        NSLog(@"Problem with params: localURLPath %@ uploadURLPath %@ params %@", localURLPath, uploadURLPath, params);
    }
    
    NSLog(@"Exiting uploadBinaryFileToServer");
    return nil;
}

#pragma mark - C interface

void GoogleCloudStorageUploadContextInitializer(void* extData, const uint8_t* ctxType, FREContext ctx,
                        uint32_t* numFunctionsToTest, const FRENamedFunction** functionsToSet) 
{
    // Register the links btwn AS3 and ObjC. (dont forget to modify the nbFuntionsToLink integer if you are adding/removing functions)
    NSInteger nbFuntionsToLink = 3;
    *numFunctionsToTest = nbFuntionsToLink;
    
    FRENamedFunction* func = (FRENamedFunction*) malloc(sizeof(FRENamedFunction) * nbFuntionsToLink);
    
    
    func[0].name = (const uint8_t*) "uploadImageToServer";
    func[0].functionData = NULL;
    func[0].function = &uploadImageToServer;
    
    func[1].name = (const uint8_t*) "uploadVideoToServer";
    func[1].functionData = NULL;
    func[1].function = &uploadVideoToServer;
    
    func[2].name = (const uint8_t*) "uploadBinaryFileToServer";
    func[2].functionData = NULL;
    func[2].function = &uploadBinaryFileToServer;
    
    *functionsToSet = func;
    
    GoogleCloudStorageUploadCtx = ctx;
}

void GoogleCloudStorageUploadContextFinalizer(FREContext ctx) { }

void GoogleCloudStorageUploadInitializer(void** extDataToSet, FREContextInitializer* ctxInitializerToSet, FREContextFinalizer* ctxFinalizerToSet)
{
	*extDataToSet = NULL;
	*ctxInitializerToSet = &GoogleCloudStorageUploadContextInitializer;
	*ctxFinalizerToSet = &GoogleCloudStorageUploadContextFinalizer;
}

void GoogleCloudStorageUploadFinalizer(void *extData) { }
