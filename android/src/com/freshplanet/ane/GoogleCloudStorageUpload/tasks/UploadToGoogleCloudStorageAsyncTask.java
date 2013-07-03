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

package com.freshplanet.ane.GoogleCloudStorageUpload.tasks;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Iterator;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;

import com.freshplanet.ane.GoogleCloudStorageUpload.GoogleCloudStorageUploadExtension;

public class UploadToGoogleCloudStorageAsyncTask extends AsyncTask<String, Void, String> {

	private static String TAG = "AirImagePicker";
	
	private final HttpClient client = new DefaultHttpClient();
	private String response = "";
	
	private String mediaPath;
	private JSONObject uploadParams;
	private Boolean isVideo = false;
	private double maxDuration;
	private Boolean isImage = false;
	private int maxWidth;
	private int maxHeight;
	
	private String status;
	
	public UploadToGoogleCloudStorageAsyncTask(JSONObject uParams, String mPath)
	{
		mediaPath = mPath;
		uploadParams = uParams;
	}
	
	public UploadToGoogleCloudStorageAsyncTask setVideoMaxDuration(double maxDuration) {
		this.isVideo = true;
		this.maxDuration = maxDuration;
		return this;
	}
	public UploadToGoogleCloudStorageAsyncTask setMaxImageSize(int maxWidth, int maxHeight) {
		this.isImage = true;
		this.maxWidth = maxWidth;
		this.maxHeight = maxHeight;
		return this;
	}
	
	@Override
	protected String doInBackground(String... urls) {
		Log.d(TAG, "[UploadToGoogleCloudStorageAsyncTask] Entering doInBackground()");
		
		byte[] result = null;
		HttpPost post = new HttpPost(urls[0]);
		
		try {
			Log.d(TAG, "[UploadToGoogleCloudStorageAsyncTask] ~~~ DBG: Prepare for httpPostData");
			// prepare for httpPost data
			String boundary = "b0undaryFP";
			
			
			Log.d(TAG, "[UploadToGoogleCloudStorageAsyncTask] ~~~ DBG: Get the byte[] of the media we want to upload");
			// Get the byte[] of the media we want to upload
			byte[] mediaBytes = toByteArray(mediaPath);
			
			if(isImage)
			{
				mediaBytes = resizeImage(mediaBytes, maxWidth, maxHeight);
			}
			if(isVideo)
			{
				if(mediaBytes.length > maxDuration * 1000 * 1000)
					status = "FILE_TOO_BIG";
					return null;
			}
			
			Log.d(TAG, "[UploadToGoogleCloudStorageAsyncTask] ~~~ DBG: build the data");
			// build the data 
			ByteArrayOutputStream requestBody = new ByteArrayOutputStream();
			for (@SuppressWarnings("unchecked")
			Iterator<String> keys = uploadParams.keys(); keys.hasNext();) {
				String key = keys.next();
				String value = uploadParams.getString(key);
				requestBody.write(("\r\n--%@\r\n".replace("%@", boundary)).getBytes());
				requestBody.write(("Content-Disposition: form-data; name=\"%@\"\r\n\r\n%@".
						replaceFirst("%@", key).replaceFirst("%@", value)).getBytes());
			}
			requestBody.write(("\r\n--%@\r\n".replace("%@", boundary)).getBytes());
			requestBody.write(("Content-Disposition: form-data; name=\"file\"; filename=\"file\"\r\n\r\n").getBytes());
			requestBody.write(mediaBytes);
			// this is the final boundary
			requestBody.write(("\r\n--%@\r\n".replace("%@", boundary)).getBytes());
			
			Log.d(TAG, "[UploadToGoogleCloudStorageAsyncTask] ~~~ DBG: Set content-type and content of http post");
			// Set content-type and content of http post
			post.setHeader("Content-Type", "multipart/form-data; boundary="+boundary);
			post.setEntity(new ByteArrayEntity( requestBody.toByteArray() ));
			
			Log.d(TAG, "[UploadToGoogleCloudStorageAsyncTask] ~~~ DBG: execute post.");
			
			// execute post.
			HttpResponse httpResponse = client.execute(post);
			StatusLine statusResponse = httpResponse.getStatusLine();
			if (statusResponse.getStatusCode() == HttpURLConnection.HTTP_OK)
			{
				result = EntityUtils.toByteArray(httpResponse.getEntity());
				response = new String(result, "UTF-8");
				Log.d(TAG, "[UploadToGoogleCloudStorageAsyncTask] ~~~ DBG: got a response: " + response);
				status = "FILE_UPLOAD_DONE";
			}
			else
			{
				status = "FILE_UPLOAD_ERROR";
				Log.d(TAG, "[UploadToGoogleCloudStorageAsyncTask] ~~~ ERR: status code: " + statusResponse.toString());
			}
		} catch (JSONException e) {
			e.printStackTrace();
			status = "FILE_UPLOAD_ERROR";
		} catch (ClientProtocolException e) {
			e.printStackTrace();
			status = "FILE_UPLOAD_ERROR";
		} catch (IOException e) {
			e.printStackTrace();
			status = "FILE_UPLOAD_ERROR";
		} 
		
		Log.d(TAG, "[UploadToGoogleCloudStorageAsyncTask] Exiting doInBackground()");
		return response;
	}

	public static Bitmap resizeImage(Bitmap image, int maxWidth, int maxHeight) {
		Log.d(TAG, "[AirImagePickerExtensionContext] Entering resizeImage");
		Bitmap result = image;
		// make sure that the image has the correct height
		if (image.getWidth() > maxWidth || image.getHeight() > maxHeight
				&& maxWidth != -1 && maxHeight != -1)
		{
	        float reductionFactor = Math.max(image.getWidth() / maxWidth, image.getHeight() / maxHeight);
	        
			result = Bitmap.createScaledBitmap( image, (int)(maxWidth/reductionFactor), (int)(maxHeight/reductionFactor), true);
			Log.d(TAG, "[AirImagePickerExtensionContext] resized image");
		}
		Log.d(TAG, "[AirImagePickerExtensionContext] Exiting resizeImage");
		return result;
	}
	
	private static byte[] getJPEGRepresentationFromBitmap(Bitmap bitmap)
	{
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
		return outputStream.toByteArray();
	}
	private static byte[] resizeImage(byte[] mediaBytes, int maxWidth, int maxHeight) {
		// TODO Auto-generated method stub
		Bitmap image = BitmapFactory.decodeByteArray(mediaBytes, 0, mediaBytes.length);
		Bitmap newImage = resizeImage(image, maxWidth, maxHeight);
		if(image != newImage)
			mediaBytes = getJPEGRepresentationFromBitmap(newImage);
		return mediaBytes;
	}

	@Override
	protected void onPostExecute(String result) {
		Log.d(TAG, "[UploadToGoogleCloudStorageAsyncTask] Entering onPostExecute()");
		
		Log.d(TAG, "[UploadToGoogleCloudStorageAsyncTask] ~~~ DBG: dispatching to actionscript a StatusEvent: " + result);
		
		GoogleCloudStorageUploadExtension.context.dispatchStatusEventAsync(status, result);
		
		Log.d(TAG, "[UploadToGoogleCloudStorageAsyncTask] Exiting onPostExecute()");
	}
	
	private byte[] toByteArray( String path )
	{
		Log.d(TAG, "[UploadToGoogleCloudStorageAsyncTask] Entering toByteArray()");
		
		File file = new File(path);
		int size = (int) file.length();
		byte[] bytes = new byte[size];
		
			BufferedInputStream buf;
			try {
				buf = new BufferedInputStream(new FileInputStream(file));
				buf.read(bytes, 0, bytes.length);
				buf.close();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
		Log.d(TAG, "[UploadToGoogleCloudStorageAsyncTask] Exiting toByteArray()");
		return bytes;
	}


}
