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

package com.freshplanet.ane.GoogleCloudStorageUpload
{
	import flash.events.EventDispatcher;
	import flash.events.StatusEvent;
	import flash.external.ExtensionContext;
	import flash.system.Capabilities;

	public class GoogleCloudStorageUpload extends EventDispatcher
	{
		// --------------------------------------------------------------------------------------//
		//																						 //
		// 									   PUBLIC API										 //
		// 																						 //
		// --------------------------------------------------------------------------------------//
		
		/** GoogleCloudStorageUpload is supported on iOS and Android devices. */
		public static function get isSupported() : Boolean
		{
			var isIOS:Boolean = (Capabilities.manufacturer.indexOf("iOS") != -1);
			var isAndroid:Boolean = (Capabilities.manufacturer.indexOf("Android") != -1)
			return isIOS || isAndroid;
		}
		
		public function GoogleCloudStorageUpload()
		{
			if (!_instance)
			{
				_context = ExtensionContext.createExtensionContext(EXTENSION_ID, null);
				if (!_context)
				{
					log("ERROR - Extension context is null. Please check if extension.xml is setup correctly.");
					return;
				}
				_context.addEventListener(StatusEvent.STATUS, onStatus);
				
				_instance = this;
			}
			else
			{
				throw Error("This is a singleton, use getInstance(), do not call the constructor directly.");
			}
		}
		
		public static function getInstance() : GoogleCloudStorageUpload
		{
			return _instance ? _instance : new GoogleCloudStorageUpload();
		}
		
		public var logEnabled : Boolean = true;
		
		
		
		/**
		 *  Perform a Google Cloud Storage Upload of the media stored locally in localURL.
		 *
		 * @param localURL location on the device where the media is stored.  Usually should correspond to a video.
		 * @param uploadURL the URL returned by HelloPop backend that corresponds to the GCS upload endpoint.
		 * @param uploadParams http post parameters expected by GCS as part of the upload in JSON format.
		 * @param callback  Function to be called when the upload is completed.
		 */
		public function uploadImageToServer( localURL:String, uploadURL:String, uploadParams:String, maxWidth:int, maxHeight:int, callback:Function ):void
		{
			if (!isSupported) callback("NOT_SUPPORTED", null);
			else
			{
				_callback = callback;
				log("uploadImageToServer localURL: " + localURL + "uploadURL: " + uploadURL + "uploadParams: " + uploadParams);
				_context.call("uploadImageToServer", localURL, uploadURL, uploadParams, maxWidth, maxHeight);
			}
		}
		
		public function uploadVideoToServer( localURL:String, uploadURL:String, uploadParams:String, maxDuration:Number, callback:Function ):void
		{
			if (!isSupported) callback("NOT_SUPPORTED", null);
			else
			{
				_callback = callback;
				log("uploadVideoToServer localURL: " + localURL + "uploadURL: " + uploadURL + "uploadParams: " + uploadParams);
				_context.call("uploadVideoToServer", localURL, uploadURL, uploadParams, maxDuration);
			}
		}
		
		public function uploadBinaryFileToServer( localURL:String, uploadURL:String, uploadParams:String, callback:Function ):void
		{
			if (!isSupported) callback("NOT_SUPPORTED", null);
			else
			{
				_callback = callback;
				log("uploadBinaryFileToServer localURL: " + localURL + "uploadURL: " + uploadURL + "uploadParams: " + uploadParams);
				_context.call("uploadBinaryFileToServer", localURL, uploadURL, uploadParams);
			}
		}
		
		// --------------------------------------------------------------------------------------//
		//																						 //
		// 									 	PRIVATE API										 //
		// 																						 //
		// --------------------------------------------------------------------------------------//
		
		private static const EXTENSION_ID : String = "com.freshplanet.GoogleCloudStorageUpload";
		
		private static var _instance : GoogleCloudStorageUpload;
		
		private var _context : ExtensionContext;
		private var _callback:Function;
		
		private function onStatus( event : StatusEvent ) : void
		{
			if (event.code == "LOGGING") // Simple log message
			{
				log(event.level);
			}
			else if (event.code == "FILE_UPLOAD_PROGRESS")
			{
				if (_callback != null)
				{
					_callback("FILE_UPLOAD_PROGRESS", event.level);
				}
			}
			else if (event.code == "FILE_UPLOAD_DONE")
			{
				if (_callback != null)
				{
					_callback("FILE_UPLOAD_DONE", event.level);
					_callback = null;
				}
			}
			else if (event.code == "FILE_UPLOAD_ERROR")
			{
				if (_callback != null)
				{
					_callback("FILE_UPLOAD_ERROR");
					_callback = null;
				}
			}
			else if (event.code == "FILE_TOO_BIG")// on android we don't support resizing videos
			{
				if (_callback != null)
				{
					_callback("FILE_TOO_BIG");
					_callback = null;
				}
			}
		}
		
		private function log( message : String ) : void
		{
			if (logEnabled) trace("[GoogleCloudStorageUpload] " + message);
		}
	}
}