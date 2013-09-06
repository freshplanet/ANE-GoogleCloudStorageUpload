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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer; 
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

	private static String TAG = "GoogleCloudStorageUpload";
	
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
			
			Log.d(TAG, "[UploadToGoogleCloudStorageAsyncTask] ~~~ DBG: build the data");
			// build the data
			byte[] bytes = null;
			byte[] imageBytes = null;
			
			if(isImage)
			{
				// Get the byte[] of the media we want to upload
				imageBytes = getImageByteArray(mediaPath);
				imageBytes = resizeImage(imageBytes, maxWidth, maxHeight);
			}
			
			//all the stuff that comes before the media bytes
			ByteArrayOutputStream preMedia = new ByteArrayOutputStream();
			for (@SuppressWarnings("unchecked")
			Iterator<String> keys = uploadParams.keys(); keys.hasNext();) {
				String key = keys.next();
				String value = uploadParams.getString(key);
				preMedia.write(("\r\n--%@\r\n".replace("%@", boundary)).getBytes());
				preMedia.write(("Content-Disposition: form-data; name=\"%@\"\r\n\r\n%@".
						replaceFirst("%@", key).replaceFirst("%@", value)).getBytes());
			}
			preMedia.write(("\r\n--%@\r\n".replace("%@", boundary)).getBytes());
			preMedia.write(("Content-Disposition: form-data; name=\"file\"; filename=\"file\"\r\n\r\n").getBytes());
			
			//all the stuff that comes after the media bytes
			ByteArrayOutputStream postMedia = new ByteArrayOutputStream();
			postMedia.write(("\r\n--%@\r\n".replace("%@", boundary)).getBytes());
			
			Log.d(TAG, "[UploadToGoogleCloudStorageAsyncTask] ~~~ DBG: Set content-type and content of http post");
			// Set content-type and content of http post
			post.setHeader("Content-Type", "multipart/form-data; boundary="+boundary);
			
			if(isImage && imageBytes != null)
				bytes = createHeaderByteArrayImage(preMedia.toByteArray(),  postMedia.toByteArray(),  imageBytes);
			else
				bytes = createHeaderByteArrayFile(preMedia.toByteArray(),  postMedia.toByteArray(),  mediaPath);
			
			preMedia.close();
			postMedia.close();
				
			if(isVideo)
			{
				if(bytes.length > maxDuration * 1000 * 1000)
				{
					status = "FILE_TOO_BIG";
					return null;
				}
			}
			
			if(bytes == null)
			{
				status = "ERROR_CREATING_HEADER";
				return null;
			}

			ByteArrayEntity entity = new ByteArrayEntity(bytes){
				@Override
				public void writeTo(final OutputStream outstream) throws IOException 
				{
				    if (outstream == null) {
				        throw new IllegalArgumentException("Output stream may not be null");
				    }

				    InputStream instream = new ByteArrayInputStream(this.content);

				    try {
				        byte[] tmp = new byte[512];
				        int total = (int) this.content.length;
				        int progress = 0;
				        int increment = 0;
				        int l;
				        int percent;

				        while ((l = instream.read(tmp)) != -1) {
				            progress = progress + l;
				            percent = Math.round(((float) progress / (float) total) * 100);

				            if (percent > increment) {
				                increment += 10;
				                // update percentage here !!
				            }
				            double percentage = (double) percent / 100.0;
				            GoogleCloudStorageUploadExtension.context.dispatchStatusEventAsync("FILE_UPLOAD_PROGRESS", ""+percentage);
				            
				            outstream.write(tmp, 0, l);
				        }

				        outstream.flush();
				    } catch (Exception e) {
						e.printStackTrace();
					} finally {
				        instream.close();
				    }
				}
			};
			post.setEntity(entity);

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
		} catch (Exception e) {
			e.printStackTrace();
			status = "UNKNOWN_ERROR";
		}
		
		
		Log.d(TAG, "[UploadToGoogleCloudStorageAsyncTask] Exiting doInBackground()");
		return response;
	}

	public static Bitmap resizeImage(Bitmap image, int maxWidth, int maxHeight) {
		Log.d(TAG, "[AirImagePickerExtensionContext] Entering resizeImage - maxWidth: "+maxWidth+" - maxHeight: "+maxHeight);
		Bitmap result = image;
		// make sure that the image has the correct height
		if ((image.getWidth() > maxWidth || image.getHeight() > maxHeight)
				&& maxWidth != -1 && maxHeight != -1)
		{
	        float reductionFactor = Math.max(image.getWidth() / maxWidth, image.getHeight() / maxHeight);

			result = Bitmap.createScaledBitmap( image, (int)(image.getWidth()/reductionFactor), (int)(image.getHeight()/reductionFactor), true);
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
	
	private byte[] createHeaderByteArrayFile( byte[] prefix, byte[] suffix, String path )
	{
		Log.d(TAG, "[UploadToGoogleCloudStorageAsyncTask] Entering createHeaderByteArrayVideo()");
		
		File file = new File(path);
		int fileLength = (int)file.length();
		int size = fileLength + prefix.length + suffix.length;
		byte[] bytes = new byte[size];
		System.arraycopy(prefix, 0, bytes, 0, prefix.length);
		
		BufferedInputStream buf;
		try {
			buf = new BufferedInputStream(new FileInputStream(file));
			buf.read(bytes, prefix.length, fileLength);
			buf.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		System.arraycopy(suffix, 0, bytes, fileLength+prefix.length, suffix.length);
			
		Log.d(TAG, "[UploadToGoogleCloudStorageAsyncTask] Exiting createHeaderByteArrayVideo()");
		return bytes;
	}


	private byte[] getImageByteArray( String path )
	{
		Log.d(TAG, "[UploadToGoogleCloudStorageAsyncTask] Entering getImageByteArray()");
		
		File file = new File(path);
		int size = (int)file.length();
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
			
		Log.d(TAG, "[UploadToGoogleCloudStorageAsyncTask] Exiting getImageByteArray()");
		return bytes;
	}
	
	private byte[] createHeaderByteArrayImage( byte[] prefix, byte[] suffix, byte[] image )
	{
		Log.d(TAG, "[UploadToGoogleCloudStorageAsyncTask] Entering createHeaderByteArrayImage()");
		
		int size = image.length + prefix.length + suffix.length;
		ByteBuffer bytes = ByteBuffer.allocate(size);
		bytes.put(prefix);
		bytes.put(image);
		bytes.put(suffix);
			
		Log.d(TAG, "[UploadToGoogleCloudStorageAsyncTask] Exiting createHeaderByteArrayImage()");
		return bytes.array();
	}

}
