package oneit.martinlutern.so_ice;

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

@SuppressLint("NewApi")
public class Preview extends SurfaceView implements SurfaceHolder.Callback {
	SurfaceHolder mHolder;
	Camera mCamera;
	Camera.PreviewCallback previewCallback;

	Preview(Context context, Camera.PreviewCallback previewCallback) {
		super(context);
		this.previewCallback = previewCallback;

		// Install a SurfaceHolder.Callback so we get notified when the
		// underlying surface is created and destroyed.
		mHolder = getHolder();
		mHolder.addCallback(this);
		mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
	}

	Camera getFrontFacingCamera() throws NoSuchElementException {
		Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
		for (int cameraIndex = 0; cameraIndex < Camera.getNumberOfCameras(); cameraIndex++) {
			Camera.getCameraInfo(cameraIndex, cameraInfo);
			if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
				try {
					mCamera = Camera.open(cameraIndex);
				} catch (RuntimeException e) {
					e.printStackTrace();
				}
			}
		}
		return mCamera;
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		// The Surface has been created, acquire the camera and tell it where
		// to draw.
		getFrontFacingCamera();
		try {
			mCamera.setPreviewDisplay(holder);
		} catch (IOException exception) {
			mCamera.release();
			mCamera = null;
			// TODO: add more exception handling logic here
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		// Surface will be destroyed when we return, so stop the preview.
		// Because the CameraDevice object is not a shared resource, it's very
		// important to release it when the activity is paused.
		mCamera.stopPreview();
		mCamera.release();
		mCamera = null;
	}

	private Size getOptimalPreviewSize(List<Size> sizes, int w, int h) {
		final double ASPECT_TOLERANCE = 0.05;
		double targetRatio = (double) w / h;
		if (sizes == null)
			return null;

		Size optimalSize = null;
		double minDiff = Double.MAX_VALUE;

		int targetHeight = h;

		// Try to find an size match aspect ratio and size
		for (Size size : sizes) {
			double ratio = (double) size.width / size.height;
			if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
				continue;
			if (Math.abs(size.height - targetHeight) < minDiff) {
				optimalSize = size;
				minDiff = Math.abs(size.height - targetHeight);
			}
		}

		// Cannot find the one match the aspect ratio, ignore the requirement
		if (optimalSize == null) {
			minDiff = Double.MAX_VALUE;
			for (Size size : sizes) {
				if (Math.abs(size.height - targetHeight) < minDiff) {
					optimalSize = size;
					minDiff = Math.abs(size.height - targetHeight);
				}
			}
		}
		return optimalSize;
	}

	@SuppressWarnings("deprecation")
	@SuppressLint("NewApi")
	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
		// Now that the size is known, set up the camera parameters and begin
		// the preview.
		@SuppressWarnings("deprecation")
		Camera.Parameters parameters = mCamera.getParameters();
		// parameters.setColorEffect(Camera.Parameters.EFFECT_MONO);
		// mCamera.setParameters(parameters);
		// mCamera.startPreview();
		// mCamera.setParameters(parameters);
		// try {
		// mCamera.setPreviewDisplay(holder);
		// } catch (IOException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// }
		@SuppressWarnings("deprecation")
		List<Size> sizes = parameters.getSupportedPreviewSizes();
		Size optimalSize = getOptimalPreviewSize(sizes, w, h);
		parameters.setPreviewSize(optimalSize.width, optimalSize.height);
		parameters.set("camera-id", 2);
		parameters.setPreviewSize(800, 480);
		mCamera.setParameters(parameters);
		Camera.Size ize = parameters.getPictureSize();
		int high = ize.height;
		int width = ize.width;

		// Log.this("Ukuran_gambar", "height= "+high+" width= "+width);
		if (previewCallback != null) {
			mCamera.setPreviewCallbackWithBuffer(previewCallback);
			Camera.Size size = parameters.getPreviewSize();
			@SuppressWarnings("deprecation")
			byte[] data = new byte[size.width
					* size.height
					* ImageFormat
							.getBitsPerPixel(parameters.getPreviewFormat()) / 8];
			mCamera.addCallbackBuffer(data);
		}
		mCamera.startPreview();
	}

}