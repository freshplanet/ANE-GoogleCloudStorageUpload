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

public class UploadVideoToServer implements FREFunction {

	private static String TAG = "GoogleCloudStorageUpload";
	
	@Override
	public FREObject call(FREContext ctx, FREObject[] args) 
	{
		Log.d(TAG, "[UploadVideoToServer] Entering call()");
		
		String localURL = null;
		String uploadURL = null;
		JSONObject uploadParamsJSON = null;
		double maxDuration = 30.0;
		
		try {
			localURL = args[0].getAsString();
			uploadURL = args[1].getAsString();
			uploadParamsJSON = new JSONObject(args[2].getAsString());
			maxDuration = args[3].getAsDouble();
			Log.d(TAG, "[UploadVideoToServer] localURL: "+localURL+ " uploadURL: "+uploadURL+" uploadParamsJSON: "+uploadParamsJSON );
		} catch (IllegalStateException e) {
			e.printStackTrace();
		} catch (FRETypeMismatchException e) {
			e.printStackTrace();
		} catch (FREInvalidObjectException e) {
			e.printStackTrace();
		} catch (FREWrongThreadException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			e.printStackTrace();
		}
		
		if (localURL != null && uploadURL != null && uploadParamsJSON != null)
		{
			new UploadToGoogleCloudStorageAsyncTask(uploadParamsJSON, localURL).setVideoMaxDuration(maxDuration).execute(uploadURL);
		}
		
		Log.d(TAG, "[UploadVideoToServer] Exiting call()");
		return null;
	}
	
	
	
	

}
