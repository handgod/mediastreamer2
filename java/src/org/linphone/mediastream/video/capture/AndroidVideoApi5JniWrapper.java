package org.linphone.mediastream.video.capture;

import java.util.List;

import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.util.Log;
import android.view.SurfaceView;
 
/**
 * Wrapper for Android Camera API. Used by Mediastreamer to record 
 * video from webcam.
 * This file depends only on Android SDK >= 5
 */
public class AndroidVideoApi5JniWrapper {
	
	static public native void setAndroidSdkVersion(int version);
	public static native void putImage(long nativePtr, byte[] buffer);
	
	static public int detectCameras(int[] indexes, int[] frontFacing, int[] orientation) {
		Log.d("mediastreamer", "detectCameras\n");
		int count = 1;
		
		indexes[0] = 0;
		frontFacing[0] = 0;
		orientation[0] = 90;
		
		return count;
	}
	
	/**
	 * Return the hw-available available resolution best matching the requested one.
	 * Best matching meaning :
	 * - try to find the same one
	 * - try to find one just a little bigger (ex: CIF when asked QVGA)
	 * - as a fallback the nearest smaller one
	 * @param requestedW Requested video size width
	 * @param requestedH Requested video size height
	 * @return int[width, height] of the chosen resolution, may be null if no
	 * resolution can possibly match the requested one
	 */
	static public int[] selectNearestResolutionAvailable(int cameraId, int requestedW, int requestedH) {
		Log.d("mediastreamer", "selectNearestResolutionAvailable: " + cameraId + ", " + requestedW + "x" + requestedH);
		
		return selectNearestResolutionAvailableForCamera(Camera.open(), requestedW, requestedH);
	}
	
	public static Object startRecording(int cameraId, int width, int height, int fps, int rotation, final long nativePtr) {
		Log.d("mediastreamer", "startRecording(" + cameraId + ", " + width + ", " + height + ", " + fps + ", " + rotation + ", " + nativePtr + ")");
		Camera camera = Camera.open(); 
		
		applyCameraParameters(camera, width, height, fps);
		  
		camera.setPreviewCallback(new Camera.PreviewCallback() {
			@Override
			public void onPreviewFrame(byte[] data, Camera camera) {
				// forward image data to JNI
				putImage(nativePtr, data);
			}
		});
		 
		camera.startPreview();
		Log.d("mediastreamer", "Returning camera object: " + camera);
		return camera; 
	} 
	
	public static void stopRecording(Object cam) {
		Log.d("mediastreamer", "stopRecording(" + cam + ")"); 
		Camera camera = (Camera) cam;
		 
		if (camera != null) {
			camera.setPreviewCallback(null);
			camera.stopPreview();
			camera.release(); 
		} else {
			Log.i("mediastreamer", "Cannot stop recording ('camera' is null)");
		}
	} 
	
	public static void setPreviewDisplaySurface(Object cam, Object surf) {
		Log.d("mediastreamer", "setPreviewDisplaySurface(" + cam + ", " + surf + ")");
		Camera camera = (Camera) cam;
		SurfaceView surface = (SurfaceView) surf;
		try {
			camera.setPreviewDisplay(surface.getHolder());
		} catch (Exception exc) {
			exc.printStackTrace(); 
		}
	}
	
	protected static int[] selectNearestResolutionAvailableForCamera(Camera cam, int requestedW, int requestedH) {
		// inversing resolution since webcams only support landscape ones
		if (requestedH > requestedW) {
			int t = requestedH;
			requestedH = requestedW;
			requestedW = t;
		}
		Camera.Parameters p = cam.getParameters();
		List<Size> supportedSizes = p.getSupportedPreviewSizes();
		Log.d("mediastreamer", supportedSizes.size() + " supported resolutions :");
		for(Size s : supportedSizes) {
			Log.d("mediastreamer", "\t" + s.width + "x" + s.height);
		}
		int rW = Math.max(requestedW, requestedH);
		int rH = Math.min(requestedW, requestedH);
		
		try {
			// look for exact match
			for(Size s: supportedSizes) {
				if (s.width == rW && s.height == rH)
					return new int[] {s.width, s.height};
			}
			// look for just above match (120%)
			for(Size s: supportedSizes) {
				if (s.width < rW || s.height < rH)
					continue; 
				if (s.width <= rW * 1.2 && s.height <= rH * 1.2)
					return new int[] {s.width, s.height};
			}
			// return nearest smaller (or the 1st one, if no smaller is found)
			Size n = supportedSizes.get(0);
			for(Size s: supportedSizes) {
				if (s.width > rW || s.height > rH)
					continue;
				if (n == null)
					n = s; 
				else if (s.width > n.width && s.height > n.height)
					n = s;
			}
			return new int[] {n.width, n.height};
		} catch (Exception exc) {
			exc.printStackTrace();
			return null;
		} finally {
			Log.d("mediastreamer", "resolution selection done");
			cam.release();
		}		
	}
	
	protected static void applyCameraParameters(Camera camera, int width, int height, int requestedFps) {
		Parameters params = camera.getParameters();
		 
		params.setPreviewSize(width, height); 
		
		List<Integer> supported = params.getSupportedPreviewFrameRates();
		if (supported != null) {
			int nearest = Integer.MAX_VALUE;
			for(Integer fr: supported) {
				int diff = Math.abs(fr.intValue() - requestedFps);
				if (diff < nearest) {
					nearest = diff;
					params.setPreviewFrameRate(fr.intValue());
				}
			}
			Log.d("mediastreamer", "Preview framerate set:" + params.getPreviewFrameRate());
		}
		
		camera.setParameters(params);		
	}
}
