//////////////////////////////////////////////////////////////////////////////////////
//
//  Copyright 2013 Freshplanet (http://freshplanet.com | opensource@freshplanet.com)
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

package com.freshplanet.ane.GoogleCloudStorageUpload.functions;

import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREInvalidObjectException;
import com.adobe.fre.FREObject;
import com.adobe.fre.FRETypeMismatchException;
import com.adobe.fre.FREWrongThreadException;
import com.freshplanet.ane.GoogleCloudStorageUpload.tasks.UploadToGoogleCloudStorageAsyncTask;

public class UploadImageToServer implements FREFunction {

	private static String TAG = "AirImagePicker";
	
	@Override
	public FREObject call(FREContext ctx, FREObject[] args) 
	{
		Log.d(TAG, "[UploadImageToServer] Entering call()");
		
		String localURL = null;
		String uploadURL = null;
		JSONObject uploadParamsJSON = null;
		int maxWidth = -1;
		int maxHeight = -1;
		Log.d(TAG, "[UploadImageToServer] try catch()");
		
		try {
			localURL = args[0].getAsString();
			uploadURL = args[1].getAsString();
			uploadParamsJSON = new JSONObject(args[2].getAsString());
			maxWidth = args[3].getAsInt();
			maxHeight = args[4].getAsInt();
			Log.d(TAG, "[UploadImageToServer] localURL: "+localURL+ " uploadURL: "+uploadURL+" uploadParamsJSON: "+uploadParamsJSON );
		} catch (IllegalStateException e) {
			Log.e(TAG, e.toString());
			e.printStackTrace();
		} catch (FRETypeMismatchException e) {
			Log.e(TAG, e.toString());
			e.printStackTrace();
		} catch (FREInvalidObjectException e) {
			Log.e(TAG, e.toString());
			e.printStackTrace();
		} catch (FREWrongThreadException e) {
			Log.e(TAG, e.toString());
			e.printStackTrace();
		} catch (JSONException e) {
			Log.e(TAG, e.toString());
			e.printStackTrace();
		} catch (Exception e) {
			Log.e(TAG, e.toString());
			e.printStackTrace();
		}
		
		if (localURL != null && uploadURL != null && uploadParamsJSON != null)
		{
			new UploadToGoogleCloudStorageAsyncTask(uploadParamsJSON, localURL).setMaxImageSize(maxWidth, maxHeight).execute(uploadURL);
		}
		
		Log.d(TAG, "[UploadImageToServer] Exiting call()");
		return null;
	}
	
	
	
	

}
