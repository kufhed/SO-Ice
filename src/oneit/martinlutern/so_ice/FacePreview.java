/*
 * Copyright (C) 2010,2011,2012 Samuel Audet
 *
 * FacePreview - A fusion of OpenCV's facedetect and Android's CameraPreview samples,
 *               with JavaCV + JavaCPP as the glue in between.
 *
 * This file was based on CameraPreview.java that came with the Samples for 
 * Android SDK API 8, revision 1 and contained the following copyright notice:
 *
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 * IMPORTANT - Make sure the AndroidManifest.xml file looks like this:
 *
 * <?xml version="1.0" encoding="utf-8"?>
 * <manifest xmlns:android="http://schemas.android.com/apk/res/android"
 *     package="org.bytedeco.javacv.facepreview"
 *     android:versionCode="1"
 *     android:versionName="1.0" >
 *     <uses-sdk android:minSdkVersion="4" />
 *     <uses-permission android:name="android.permission.CAMERA" />
 *     <uses-feature android:name="android.hardware.camera" />
 *     <application android:label="@string/app_name">
 *         <activity
 *             android:name="FacePreview"
 *             android:label="@string/app_name"
 *             android:screenOrientation="landscape">
 *             <intent-filter>
 *                 <action android:name="android.intent.action.MAIN" />
 *                 <category android:name="android.intent.category.LAUNCHER" />
 *             </intent-filter>
 *         </activity>
 *     </application>
 * </manifest>
 */

package oneit.martinlutern.so_ice;

import static org.bytedeco.javacpp.helper.opencv_objdetect.cvHaarDetectObjects;
import static org.bytedeco.javacpp.opencv_core.IPL_DEPTH_8U;
import static org.bytedeco.javacpp.opencv_core.cvClearMemStorage;
import static org.bytedeco.javacpp.opencv_core.cvGetSeqElem;
import static org.bytedeco.javacpp.opencv_core.cvLoad;
import static org.bytedeco.javacpp.opencv_objdetect.CV_HAAR_DO_CANNY_PRUNING;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Locale;

import org.bytedeco.javacpp.*;
import org.bytedeco.javacpp.opencv_core.CvMemStorage;
import org.bytedeco.javacpp.opencv_core.CvRect;
import org.bytedeco.javacpp.opencv_core.CvSeq;
import org.bytedeco.javacpp.opencv_core.IplImage;
import org.bytedeco.javacpp.opencv_objdetect.CvHaarClassifierCascade;
import org.bytedeco.javacpp.helper.opencv_core.AbstractCvMemStorage;
import org.bytedeco.javacpp.helper.opencv_core.AbstractIplImage;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

public class FacePreview extends Activity implements
		TextToSpeech.OnInitListener {
	private FrameLayout layout;
	private FaceView faceView;
	private TextToSpeech tts;
	private final int REQ_CODE_SPEECH_INPUT = 100;
	private Preview mPreview;
	EditText editText_isi;
	Button btn_suara;
	PowerManager.WakeLock wakeLock;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main_sign);

		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK,
				"My wakelook");
		wakeLock.acquire();
		tts = new TextToSpeech(this, this);
		btn_suara = (Button) findViewById(R.id.btn_suara);
		editText_isi = (EditText) findViewById(R.id.tampil_huruf);

		String fontPath1 = "fonts/roboto_m.ttf";
		Typeface tf1 = Typeface.createFromAsset(getAssets(), fontPath1);
		editText_isi.setTypeface(tf1);

		btn_suara.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				if (editText_isi.getText().toString() != null) {
					tts.speak(editText_isi.getText().toString(),
							TextToSpeech.QUEUE_FLUSH, null);
				} else {
					Toast.makeText(getApplicationContext(),
							"Kata yang ingin diucapkan tidak ada.",
							Toast.LENGTH_SHORT).show();
				}
			}
		});
		// Create our Preview view and set it as the content of our activity.
		try {
			layout = (FrameLayout) findViewById(R.id.tampil_camera);
			faceView = new FaceView(this);
			mPreview = new Preview(this, faceView);
			layout.addView(mPreview);
			layout.addView(faceView);
		} catch (IOException e) {
			e.printStackTrace();
			new AlertDialog.Builder(this).setMessage(e.getMessage()).create()
					.show();
		}
	}

	@Override
	public void onDestroy() {
		if (tts != null) {
			tts.stop();
			tts.shutdown();
		}
		super.onDestroy();
	}

	@Override
	public void onInit(int status) {
		if (status == TextToSpeech.SUCCESS) {
			int result = tts.setLanguage(Locale.getDefault());
			if (result == TextToSpeech.LANG_MISSING_DATA
					|| result == TextToSpeech.LANG_NOT_SUPPORTED) {
				Toast.makeText(
						getApplicationContext(),
						"Periksa kembali koneksi internet anda| Atau periksa google voice language anda.",
						Toast.LENGTH_LONG).show();
			}
		} else {
			Log.e("TTS", "Inisialisasi Gagal!");
		}
	}

	public class FaceView extends View implements Camera.PreviewCallback {
		public static final int SUBSAMPLING_FACTOR = 4;

		private IplImage grayImage;
		private final CvHaarClassifierCascade classifierA;
		private final CvHaarClassifierCascade classifierB;
		private final CvHaarClassifierCascade classifierC;
		private final CvHaarClassifierCascade classifierD;
		private final CvHaarClassifierCascade classifierE;
		private final CvHaarClassifierCascade classifierF;
		private final CvHaarClassifierCascade classifierG;
		private final CvHaarClassifierCascade classifierH;
		private final CvHaarClassifierCascade classifierI;
		private final CvHaarClassifierCascade classifierJ;
		private final CvHaarClassifierCascade classifierK;
		private final CvHaarClassifierCascade classifierL;
		private final CvHaarClassifierCascade classifierM;
		private final CvHaarClassifierCascade classifierN;
		private final CvHaarClassifierCascade classifierO;
		private final CvHaarClassifierCascade classifierP;
		private final CvHaarClassifierCascade classifierQ;
		private final CvHaarClassifierCascade classifierR;
		private final CvHaarClassifierCascade classifierS;
		private final CvHaarClassifierCascade classifierT;
		private final CvHaarClassifierCascade classifierU;
		private final CvHaarClassifierCascade classifierV;
		private final CvHaarClassifierCascade classifierW;
		private final CvHaarClassifierCascade classifierX;
		private final CvHaarClassifierCascade classifierY;
		private final CvHaarClassifierCascade classifierZ;
		private final CvMemStorage storage;
		private CvSeq facesA = null;
		private CvSeq facesB = null;
		private CvSeq facesC = null;
		private CvSeq facesD = null;
		private CvSeq facesE = null;
		private CvSeq facesF = null;
		private CvSeq facesG = null;
		private CvSeq facesH = null;
		private CvSeq facesI = null;
		private CvSeq facesJ = null;
		private CvSeq facesK = null;
		private CvSeq facesL = null;
		private CvSeq facesM = null;
		private CvSeq facesN = null;
		private CvSeq facesO = null;
		private CvSeq facesP = null;
		private CvSeq facesQ = null;
		private CvSeq facesR = null;
		private CvSeq facesS = null;
		private CvSeq facesT = null;
		private CvSeq facesU = null;
		private CvSeq facesV = null;
		private CvSeq facesW = null;
		private CvSeq facesX = null;
		private CvSeq facesY = null;
		private CvSeq facesZ = null;

		public FaceView(Context context) throws IOException {
			super(context);

			// Load the classifier file from Java resources.
			File classifierFileA = Loader.extractResource(getClass(),
					"/oneit/martinlutern/so_ice/A.xml", context.getCacheDir(),
					"classifier", ".xml");
			File classifierFileB = Loader.extractResource(getClass(),
					"/oneit/martinlutern/so_ice/B.xml", context.getCacheDir(),
					"classifier", ".xml");
			File classifierFileC = Loader.extractResource(getClass(),
					"/oneit/martinlutern/so_ice/C.xml", context.getCacheDir(),
					"classifier", ".xml");
			File classifierFileD = Loader.extractResource(getClass(),
					"/oneit/martinlutern/so_ice/D.xml", context.getCacheDir(),
					"classifier", ".xml");
			File classifierFileE = Loader.extractResource(getClass(),
					"/oneit/martinlutern/so_ice/E.xml", context.getCacheDir(),
					"classifier", ".xml");
			File classifierFileF = Loader.extractResource(getClass(),
					"/oneit/martinlutern/so_ice/F.xml", context.getCacheDir(),
					"classifier", ".xml");
			File classifierFileG = Loader.extractResource(getClass(),
					"/oneit/martinlutern/so_ice/G.xml", context.getCacheDir(),
					"classifier", ".xml");
			File classifierFileH = Loader.extractResource(getClass(),
					"/oneit/martinlutern/so_ice/H.xml", context.getCacheDir(),
					"classifier", ".xml");
			File classifierFileI = Loader.extractResource(getClass(),
					"/oneit/martinlutern/so_ice/I.xml", context.getCacheDir(),
					"classifier", ".xml");
			File classifierFileJ = Loader.extractResource(getClass(),
					"/oneit/martinlutern/so_ice/J.xml", context.getCacheDir(),
					"classifier", ".xml");
			File classifierFileK = Loader.extractResource(getClass(),
					"/oneit/martinlutern/so_ice/K.xml", context.getCacheDir(),
					"classifier", ".xml");
			File classifierFileL = Loader.extractResource(getClass(),
					"/oneit/martinlutern/so_ice/L.xml", context.getCacheDir(),
					"classifier", ".xml");
			File classifierFileM = Loader.extractResource(getClass(),
					"/oneit/martinlutern/so_ice/M.xml", context.getCacheDir(),
					"classifier", ".xml");
			File classifierFileN = Loader.extractResource(getClass(),
					"/oneit/martinlutern/so_ice/N.xml", context.getCacheDir(),
					"classifier", ".xml");
			File classifierFileO = Loader.extractResource(getClass(),
					"/oneit/martinlutern/so_ice/O.xml", context.getCacheDir(),
					"classifier", ".xml");
			File classifierFileP = Loader.extractResource(getClass(),
					"/oneit/martinlutern/so_ice/P.xml", context.getCacheDir(),
					"classifier", ".xml");
			File classifierFileQ = Loader.extractResource(getClass(),
					"/oneit/martinlutern/so_ice/Q.xml", context.getCacheDir(),
					"classifier", ".xml");
			File classifierFileR = Loader.extractResource(getClass(),
					"/oneit/martinlutern/so_ice/R.xml", context.getCacheDir(),
					"classifier", ".xml");
			File classifierFileS = Loader.extractResource(getClass(),
					"/oneit/martinlutern/so_ice/S.xml", context.getCacheDir(),
					"classifier", ".xml");
			File classifierFileT = Loader.extractResource(getClass(),
					"/oneit/martinlutern/so_ice/T.xml", context.getCacheDir(),
					"classifier", ".xml");
			File classifierFileU = Loader.extractResource(getClass(),
					"/oneit/martinlutern/so_ice/U.xml", context.getCacheDir(),
					"classifier", ".xml");
			File classifierFileV = Loader.extractResource(getClass(),
					"/oneit/martinlutern/so_ice/V.xml", context.getCacheDir(),
					"classifier", ".xml");
			File classifierFileW = Loader.extractResource(getClass(),
					"/oneit/martinlutern/so_ice/W.xml", context.getCacheDir(),
					"classifier", ".xml");
			File classifierFileX = Loader.extractResource(getClass(),
					"/oneit/martinlutern/so_ice/X.xml", context.getCacheDir(),
					"classifier", ".xml");
			File classifierFileY = Loader.extractResource(getClass(),
					"/oneit/martinlutern/so_ice/Y.xml", context.getCacheDir(),
					"classifier", ".xml");
			File classifierFileZ = Loader.extractResource(getClass(),
					"/oneit/martinlutern/so_ice/Z.xml", context.getCacheDir(),
					"classifier", ".xml");
			if (classifierFileA == null || classifierFileA.length() <= 0) {
				throw new IOException(
						"Could not extract the classifier file from Java resource.");
			}

			// Preload the opencv_objdetect module to work around a known bug.
			Loader.load(org.bytedeco.javacpp.opencv_objdetect.CvHaarClassifierCascade.class);
			classifierA = new CvHaarClassifierCascade(
					cvLoad(classifierFileA.getAbsolutePath()));
			classifierB = new CvHaarClassifierCascade(
					cvLoad(classifierFileB.getAbsolutePath()));
			classifierC = new CvHaarClassifierCascade(
					cvLoad(classifierFileC.getAbsolutePath()));
			classifierD = new CvHaarClassifierCascade(
					cvLoad(classifierFileD.getAbsolutePath()));
			classifierE = new CvHaarClassifierCascade(
					cvLoad(classifierFileE.getAbsolutePath()));
			classifierF = new CvHaarClassifierCascade(
					cvLoad(classifierFileF.getAbsolutePath()));
			classifierG = new CvHaarClassifierCascade(
					cvLoad(classifierFileG.getAbsolutePath()));
			classifierH = new CvHaarClassifierCascade(
					cvLoad(classifierFileH.getAbsolutePath()));
			classifierI = new CvHaarClassifierCascade(
					cvLoad(classifierFileI.getAbsolutePath()));
			classifierJ = new CvHaarClassifierCascade(
					cvLoad(classifierFileJ.getAbsolutePath()));
			classifierK = new CvHaarClassifierCascade(
					cvLoad(classifierFileK.getAbsolutePath()));
			classifierL = new CvHaarClassifierCascade(
					cvLoad(classifierFileL.getAbsolutePath()));
			classifierM = new CvHaarClassifierCascade(
					cvLoad(classifierFileM.getAbsolutePath()));
			classifierN = new CvHaarClassifierCascade(
					cvLoad(classifierFileN.getAbsolutePath()));
			classifierO = new CvHaarClassifierCascade(
					cvLoad(classifierFileO.getAbsolutePath()));
			classifierP = new CvHaarClassifierCascade(
					cvLoad(classifierFileP.getAbsolutePath()));
			classifierQ = new CvHaarClassifierCascade(
					cvLoad(classifierFileQ.getAbsolutePath()));
			classifierR = new CvHaarClassifierCascade(
					cvLoad(classifierFileR.getAbsolutePath()));
			classifierS = new CvHaarClassifierCascade(
					cvLoad(classifierFileS.getAbsolutePath()));
			classifierT = new CvHaarClassifierCascade(
					cvLoad(classifierFileT.getAbsolutePath()));
			classifierU = new CvHaarClassifierCascade(
					cvLoad(classifierFileU.getAbsolutePath()));
			classifierV = new CvHaarClassifierCascade(
					cvLoad(classifierFileV.getAbsolutePath()));
			classifierW = new CvHaarClassifierCascade(
					cvLoad(classifierFileW.getAbsolutePath()));
			classifierX = new CvHaarClassifierCascade(
					cvLoad(classifierFileX.getAbsolutePath()));
			classifierY = new CvHaarClassifierCascade(
					cvLoad(classifierFileY.getAbsolutePath()));
			classifierZ = new CvHaarClassifierCascade(
					cvLoad(classifierFileZ.getAbsolutePath()));

			classifierFileA.delete();
			classifierFileB.delete();
			classifierFileC.delete();
			classifierFileD.delete();
			classifierFileE.delete();
			classifierFileF.delete();
			classifierFileG.delete();
			classifierFileH.delete();
			classifierFileI.delete();
			classifierFileJ.delete();
			classifierFileK.delete();
			classifierFileL.delete();
			classifierFileM.delete();
			classifierFileN.delete();
			classifierFileO.delete();
			classifierFileP.delete();
			classifierFileQ.delete();
			classifierFileR.delete();
			classifierFileS.delete();
			classifierFileT.delete();
			classifierFileU.delete();
			classifierFileV.delete();
			classifierFileW.delete();
			classifierFileX.delete();
			classifierFileY.delete();
			classifierFileZ.delete();
			if (classifierA.isNull()) {
				throw new IOException("Could not load the classifier file.");
			}

			storage = CvMemStorage.create();
		}

		@Override
		public void onPreviewFrame(final byte[] data, final Camera camera) {
			try {
				Camera.Size size = camera.getParameters().getPreviewSize();
				processImage(data, size.width, size.height);
				camera.addCallbackBuffer(data);
			} catch (RuntimeException e) {
				// The camera has probably just been released, ignore.
			}
		}

		protected void processImage(byte[] data, int width, int height) {
			// First, downsample our image and convert it into a grayscale
			// IplImage
			int f = SUBSAMPLING_FACTOR;
			if (grayImage == null || grayImage.width() != width / f
					|| grayImage.height() != height / f) {
				grayImage = IplImage.create(width / f, height / f,
						IPL_DEPTH_8U, 1);
			}
			int imageWidth = grayImage.width();
			int imageHeight = grayImage.height();
			int dataStride = f * width;
			int imageStride = grayImage.widthStep();
			ByteBuffer imageBuffer = grayImage.getByteBuffer();
			for (int y = 0; y < imageHeight; y++) {
				int dataLine = y * dataStride;
				int imageLine = y * imageStride;
				for (int x = 0; x < imageWidth; x++) {
					imageBuffer.put(imageLine + x, data[dataLine + f * x]);
				}
			}

			facesA = cvHaarDetectObjects(grayImage, classifierA, storage, 1.1,
					3, CV_HAAR_DO_CANNY_PRUNING);
			facesB = cvHaarDetectObjects(grayImage, classifierB, storage, 1.1,
					3, CV_HAAR_DO_CANNY_PRUNING);
			facesC = cvHaarDetectObjects(grayImage, classifierC, storage, 1.1,
					3, CV_HAAR_DO_CANNY_PRUNING);
			facesD = cvHaarDetectObjects(grayImage, classifierD, storage, 1.1,
					3, CV_HAAR_DO_CANNY_PRUNING);
			facesE = cvHaarDetectObjects(grayImage, classifierE, storage, 1.1,
					3, CV_HAAR_DO_CANNY_PRUNING);
			facesF = cvHaarDetectObjects(grayImage, classifierF, storage, 1.1,
					3, CV_HAAR_DO_CANNY_PRUNING);
			facesG = cvHaarDetectObjects(grayImage, classifierG, storage, 1.1,
					3, CV_HAAR_DO_CANNY_PRUNING);
			facesH = cvHaarDetectObjects(grayImage, classifierH, storage, 1.1,
					3, CV_HAAR_DO_CANNY_PRUNING);
			facesI = cvHaarDetectObjects(grayImage, classifierI, storage, 1.1,
					3, CV_HAAR_DO_CANNY_PRUNING);
			facesJ = cvHaarDetectObjects(grayImage, classifierJ, storage, 1.1,
					3, CV_HAAR_DO_CANNY_PRUNING);
			facesK = cvHaarDetectObjects(grayImage, classifierK, storage, 1.1,
					3, CV_HAAR_DO_CANNY_PRUNING);
			facesL = cvHaarDetectObjects(grayImage, classifierL, storage, 1.1,
					3, CV_HAAR_DO_CANNY_PRUNING);
			facesM = cvHaarDetectObjects(grayImage, classifierM, storage, 1.1,
					3, CV_HAAR_DO_CANNY_PRUNING);
			facesN = cvHaarDetectObjects(grayImage, classifierN, storage, 1.1,
					3, CV_HAAR_DO_CANNY_PRUNING);
			facesO = cvHaarDetectObjects(grayImage, classifierO, storage, 1.1,
					3, CV_HAAR_DO_CANNY_PRUNING);
			facesP = cvHaarDetectObjects(grayImage, classifierP, storage, 1.1,
					3, CV_HAAR_DO_CANNY_PRUNING);
			facesQ = cvHaarDetectObjects(grayImage, classifierQ, storage, 1.1,
					3, CV_HAAR_DO_CANNY_PRUNING);
			facesR = cvHaarDetectObjects(grayImage, classifierR, storage, 1.1,
					3, CV_HAAR_DO_CANNY_PRUNING);
			facesS = cvHaarDetectObjects(grayImage, classifierS, storage, 1.1,
					3, CV_HAAR_DO_CANNY_PRUNING);
			facesT = cvHaarDetectObjects(grayImage, classifierT, storage, 1.1,
					3, CV_HAAR_DO_CANNY_PRUNING);
			facesU = cvHaarDetectObjects(grayImage, classifierU, storage, 1.1,
					3, CV_HAAR_DO_CANNY_PRUNING);
			facesV = cvHaarDetectObjects(grayImage, classifierV, storage, 1.1,
					3, CV_HAAR_DO_CANNY_PRUNING);
			facesW = cvHaarDetectObjects(grayImage, classifierW, storage, 1.1,
					3, CV_HAAR_DO_CANNY_PRUNING);
			facesX = cvHaarDetectObjects(grayImage, classifierX, storage, 1.1,
					3, CV_HAAR_DO_CANNY_PRUNING);
			facesY = cvHaarDetectObjects(grayImage, classifierY, storage, 1.1,
					3, CV_HAAR_DO_CANNY_PRUNING);
			facesZ = cvHaarDetectObjects(grayImage, classifierZ, storage, 1.1,
					3, CV_HAAR_DO_CANNY_PRUNING);
			postInvalidate();
			cvClearMemStorage(storage);
		}

		@Override
		protected void onDraw(Canvas canvas) {
			Paint paint = new Paint();
			paint.setColor(Color.WHITE);
			paint.setTextSize(20);

			String s = "";
			float textWidth = paint.measureText(s);
			canvas.drawText(s, (getWidth() - textWidth) / 2, 20, paint);

			if (facesA != null && facesB != null && facesC != null
					&& facesD != null && facesE != null && facesF != null
					&& facesG != null && facesH != null && facesI != null
					&& facesJ != null && facesK != null && facesL != null
					&& facesM != null && facesN != null && facesO != null
					&& facesP != null && facesQ != null && facesR != null
					&& facesS != null && facesT != null && facesU != null
					&& facesV != null && facesW != null && facesX != null
					&& facesY != null && facesZ != null) {
				int A = facesA.total();
				int B = facesB.total();
				int C = facesC.total();
				int D = facesD.total();
				int E = facesE.total();
				int F = facesF.total();
				int G = facesG.total();
				int H = facesH.total();
				int I = facesI.total();
				int J = facesJ.total();
				int K = facesK.total();
				int L = facesL.total();
				int M = facesM.total();
				int N = facesN.total();
				int O = facesO.total();
				int P = facesP.total();
				int Q = facesQ.total();
				int R = facesR.total();
				int S = facesS.total();
				int T = facesT.total();
				int U = facesU.total();
				int V = facesV.total();
				int W = facesW.total();
				int X = facesX.total();
				int Y = facesY.total();
				int Z = facesZ.total();
				if (A == 1) {
					if (facesA != null) {
						Log.i("total A", "Total A: " + A);
						String hasil = "A";
						editText_isi.setText(hasil);
						paint.setStrokeWidth(2);
						paint.setStyle(Paint.Style.STROKE);
						float scaleX = (float) getWidth() / grayImage.width();
						float scaleY = (float) getHeight() / grayImage.height();
						for (int i = 0; i < A; i++) {
							CvRect r = new CvRect(cvGetSeqElem(facesA, i));
							int x = r.x(), y = r.y(), w = r.width(), h = r
									.height();
							canvas.drawRect(x * scaleX, y * scaleY, (x + w)
									* scaleX, (y + h) * scaleY, paint);
						}
					}
				} else if (B == 1) {
					if (facesB != null) {
						int totalB = facesB.total();
						if (totalB == 1) {
							Log.i("total B", "Total B: " + B);
							String hasil = "B";
							editText_isi.setText(hasil);
							paint.setStrokeWidth(2);
							paint.setStyle(Paint.Style.STROKE);
							float scaleX = (float) getWidth()
									/ grayImage.width();
							float scaleY = (float) getHeight()
									/ grayImage.height();
							for (int i = 0; i < totalB; i++) {
								CvRect r = new CvRect(cvGetSeqElem(facesB, i));
								int x = r.x(), y = r.y(), w = r.width(), h = r
										.height();
								canvas.drawRect(x * scaleX, y * scaleY, (x + w)
										* scaleX, (y + h) * scaleY, paint);
							}
						}
					}
				} else if (C == 1) {
					if (facesC != null) {
						int totalC = facesC.total();
						if (totalC == 1) {
							Log.i("total C", "Total C: " + C);
							String hasil = "C";
							editText_isi.setText(hasil);
							paint.setStrokeWidth(2);
							paint.setStyle(Paint.Style.STROKE);
							float scaleX = (float) getWidth()
									/ grayImage.width();
							float scaleY = (float) getHeight()
									/ grayImage.height();
							for (int i = 0; i < totalC; i++) {
								CvRect r = new CvRect(cvGetSeqElem(facesC, i));
								int x = r.x(), y = r.y(), w = r.width(), h = r
										.height();
								canvas.drawRect(x * scaleX, y * scaleY, (x + w)
										* scaleX, (y + h) * scaleY, paint);
							}
						}
					}
				} else if (D == 1) {
					if (facesD != null) {
						int totalD = facesD.total();
						if (totalD == 1) {
							Log.i("total D", "Total D: " + D);
							String hasil = "D";
							editText_isi.setText(hasil);
							paint.setStrokeWidth(2);
							paint.setStyle(Paint.Style.STROKE);
							float scaleX = (float) getWidth()
									/ grayImage.width();
							float scaleY = (float) getHeight()
									/ grayImage.height();
							for (int i = 0; i < totalD; i++) {
								CvRect r = new CvRect(cvGetSeqElem(facesD, i));
								int x = r.x(), y = r.y(), w = r.width(), h = r
										.height();
								canvas.drawRect(x * scaleX, y * scaleY, (x + w)
										* scaleX, (y + h) * scaleY, paint);
							}
						}
					}
				} else if (E == 1) {
					if (facesE != null) {
						int totalE = facesE.total();
						if (totalE == 1) {
							Log.i("total E", "Total E: " + E);
							String hasil = "E";
							editText_isi.setText(hasil);
							paint.setStrokeWidth(2);
							paint.setStyle(Paint.Style.STROKE);
							float scaleX = (float) getWidth()
									/ grayImage.width();
							float scaleY = (float) getHeight()
									/ grayImage.height();
							for (int i = 0; i < totalE; i++) {
								CvRect r = new CvRect(cvGetSeqElem(facesE, i));
								int x = r.x(), y = r.y(), w = r.width(), h = r
										.height();
								canvas.drawRect(x * scaleX, y * scaleY, (x + w)
										* scaleX, (y + h) * scaleY, paint);
							}
						}
					}
				} else if (F == 1) {
					if (facesF != null) {
						int totalF = facesF.total();
						if (totalF == 1) {
							Log.i("total F", "Total F: " + F);
							String hasil = "F";
							editText_isi.setText(hasil);
							paint.setStrokeWidth(2);
							paint.setStyle(Paint.Style.STROKE);
							float scaleX = (float) getWidth()
									/ grayImage.width();
							float scaleY = (float) getHeight()
									/ grayImage.height();
							for (int i = 0; i < totalF; i++) {
								CvRect r = new CvRect(cvGetSeqElem(facesF, i));
								int x = r.x(), y = r.y(), w = r.width(), h = r
										.height();
								canvas.drawRect(x * scaleX, y * scaleY, (x + w)
										* scaleX, (y + h) * scaleY, paint);
							}
						}
					}
				} else if (G == 1) {
					if (facesG != null) {
						int totalG = facesG.total();
						if (totalG == 1) {
							Log.i("total G", "Total G: " + G);
							String hasil = "G";
							editText_isi.setText(hasil);
							paint.setStrokeWidth(2);
							paint.setStyle(Paint.Style.STROKE);
							float scaleX = (float) getWidth()
									/ grayImage.width();
							float scaleY = (float) getHeight()
									/ grayImage.height();
							for (int i = 0; i < totalG; i++) {
								CvRect r = new CvRect(cvGetSeqElem(facesG, i));
								int x = r.x(), y = r.y(), w = r.width(), h = r
										.height();
								canvas.drawRect(x * scaleX, y * scaleY, (x + w)
										* scaleX, (y + h) * scaleY, paint);
							}
						}
					}
				} else if (H == 1) {
					if (facesH != null) {
						int totalH = facesH.total();
						if (totalH == 1) {
							Log.i("total H", "Total H: " + H);
							String hasil = "H";
							editText_isi.setText(hasil);
							paint.setStrokeWidth(2);
							paint.setStyle(Paint.Style.STROKE);
							float scaleX = (float) getWidth()
									/ grayImage.width();
							float scaleY = (float) getHeight()
									/ grayImage.height();
							for (int i = 0; i < totalH; i++) {
								CvRect r = new CvRect(cvGetSeqElem(facesH, i));
								int x = r.x(), y = r.y(), w = r.width(), h = r
										.height();
								canvas.drawRect(x * scaleX, y * scaleY, (x + w)
										* scaleX, (y + h) * scaleY, paint);
							}
						}
					}
				} else if (I == 1) {
					if (facesI != null) {
						int totalI = facesI.total();
						if (totalI == 1) {
							Log.i("total I", "Total I: " + I);
							String hasil = "I";
							editText_isi.setText(hasil);
							paint.setStrokeWidth(2);
							paint.setStyle(Paint.Style.STROKE);
							float scaleX = (float) getWidth()
									/ grayImage.width();
							float scaleY = (float) getHeight()
									/ grayImage.height();
							for (int i = 0; i < totalI; i++) {
								CvRect r = new CvRect(cvGetSeqElem(facesI, i));
								int x = r.x(), y = r.y(), w = r.width(), h = r
										.height();
								canvas.drawRect(x * scaleX, y * scaleY, (x + w)
										* scaleX, (y + h) * scaleY, paint);
							}
						}
					}
				} else if (J == 1) {
					if (facesJ != null) {
						int totalJ = facesJ.total();
						if (totalJ == 1) {
							Log.i("total J", "Total J: " + J);
							String hasil = "J";
							editText_isi.setText(hasil);
							paint.setStrokeWidth(2);
							paint.setStyle(Paint.Style.STROKE);
							float scaleX = (float) getWidth()
									/ grayImage.width();
							float scaleY = (float) getHeight()
									/ grayImage.height();
							for (int i = 0; i < totalJ; i++) {
								CvRect r = new CvRect(cvGetSeqElem(facesJ, i));
								int x = r.x(), y = r.y(), w = r.width(), h = r
										.height();
								canvas.drawRect(x * scaleX, y * scaleY, (x + w)
										* scaleX, (y + h) * scaleY, paint);
							}
						}

					}
				} else if (K == 1) {
					if (facesK != null) {
						int totalK = facesK.total();
						if (totalK == 1) {
							Log.i("total K", "Total K: " + K);
							String hasil = "K";
							editText_isi.setText(hasil);
							paint.setStrokeWidth(2);
							paint.setStyle(Paint.Style.STROKE);
							float scaleX = (float) getWidth()
									/ grayImage.width();
							float scaleY = (float) getHeight()
									/ grayImage.height();
							for (int i = 0; i < totalK; i++) {
								CvRect r = new CvRect(cvGetSeqElem(facesK, i));
								int x = r.x(), y = r.y(), w = r.width(), h = r
										.height();
								canvas.drawRect(x * scaleX, y * scaleY, (x + w)
										* scaleX, (y + h) * scaleY, paint);
							}
						}

					}
				} else if (L == 1) {
					if (facesL != null) {
						int totalL = facesL.total();
						if (totalL == 1) {
							Log.i("total L", "Total L: " + L);
							String hasil = "L";
							editText_isi.setText(hasil);
							paint.setStrokeWidth(2);
							paint.setStyle(Paint.Style.STROKE);
							float scaleX = (float) getWidth()
									/ grayImage.width();
							float scaleY = (float) getHeight()
									/ grayImage.height();
							for (int i = 0; i < totalL; i++) {
								CvRect r = new CvRect(cvGetSeqElem(facesL, i));
								int x = r.x(), y = r.y(), w = r.width(), h = r
										.height();
								canvas.drawRect(x * scaleX, y * scaleY, (x + w)
										* scaleX, (y + h) * scaleY, paint);
							}
						}

					}
				} else if (M == 1) {
					if (facesM != null) {
						int totalM = facesM.total();
						if (totalM == 1) {
							Log.i("total M", "Total M: " + M);
							String hasil = "M";
							editText_isi.setText(hasil);
							paint.setStrokeWidth(2);
							paint.setStyle(Paint.Style.STROKE);
							float scaleX = (float) getWidth()
									/ grayImage.width();
							float scaleY = (float) getHeight()
									/ grayImage.height();
							for (int i = 0; i < totalM; i++) {
								CvRect r = new CvRect(cvGetSeqElem(facesM, i));
								int x = r.x(), y = r.y(), w = r.width(), h = r
										.height();
								canvas.drawRect(x * scaleX, y * scaleY, (x + w)
										* scaleX, (y + h) * scaleY, paint);
							}
						}

					}
				} else if (N == 1) {
					if (facesN != null) {
						int totalN = facesN.total();
						if (totalN == 1) {
							Log.i("total N", "Total N: " + N);
							String hasil = "N";
							editText_isi.setText(hasil);
							paint.setStrokeWidth(2);
							paint.setStyle(Paint.Style.STROKE);
							float scaleX = (float) getWidth()
									/ grayImage.width();
							float scaleY = (float) getHeight()
									/ grayImage.height();
							for (int i = 0; i < totalN; i++) {
								CvRect r = new CvRect(cvGetSeqElem(facesN, i));
								int x = r.x(), y = r.y(), w = r.width(), h = r
										.height();
								canvas.drawRect(x * scaleX, y * scaleY, (x + w)
										* scaleX, (y + h) * scaleY, paint);
							}
						}

					}
				} else if (O == 1) {
					if (facesO != null) {
						int totalO = facesO.total();
						if (totalO == 1) {
							Log.i("total O", "Total O: " + O);
							String hasil = "O";
							editText_isi.setText(hasil);
							paint.setStrokeWidth(2);
							paint.setStyle(Paint.Style.STROKE);
							float scaleX = (float) getWidth()
									/ grayImage.width();
							float scaleY = (float) getHeight()
									/ grayImage.height();
							for (int i = 0; i < totalO; i++) {
								CvRect r = new CvRect(cvGetSeqElem(facesO, i));
								int x = r.x(), y = r.y(), w = r.width(), h = r
										.height();
								canvas.drawRect(x * scaleX, y * scaleY, (x + w)
										* scaleX, (y + h) * scaleY, paint);
							}
						}

					}
				} else if (P == 1) {
					if (facesP != null) {
						int totalP = facesP.total();
						if (totalP == 1) {
							Log.i("total P", "Total P: " + P);
							String hasil = "P";
							editText_isi.setText(hasil);
							paint.setStrokeWidth(2);
							paint.setStyle(Paint.Style.STROKE);
							float scaleX = (float) getWidth()
									/ grayImage.width();
							float scaleY = (float) getHeight()
									/ grayImage.height();
							for (int i = 0; i < totalP; i++) {
								CvRect r = new CvRect(cvGetSeqElem(facesP, i));
								int x = r.x(), y = r.y(), w = r.width(), h = r
										.height();
								canvas.drawRect(x * scaleX, y * scaleY, (x + w)
										* scaleX, (y + h) * scaleY, paint);
							}
						}

					}
				} else if (Q == 1) {
					if (facesQ != null) {
						int totalQ = facesQ.total();
						if (totalQ == 1) {
							Log.i("total Q", "Total Q: " + Q);
							String hasil = "Q";
							editText_isi.setText(hasil);
							paint.setStrokeWidth(2);
							paint.setStyle(Paint.Style.STROKE);
							float scaleX = (float) getWidth()
									/ grayImage.width();
							float scaleY = (float) getHeight()
									/ grayImage.height();
							for (int i = 0; i < totalQ; i++) {
								CvRect r = new CvRect(cvGetSeqElem(facesQ, i));
								int x = r.x(), y = r.y(), w = r.width(), h = r
										.height();
								canvas.drawRect(x * scaleX, y * scaleY, (x + w)
										* scaleX, (y + h) * scaleY, paint);
							}
						}

					}
				} else if (R == 1) {
					if (facesR != null) {
						int totalR = facesR.total();
						if (totalR == 1) {
							Log.i("total R", "Total R: " + R);
							String hasil = "R";
							editText_isi.setText(hasil);
							paint.setStrokeWidth(2);
							paint.setStyle(Paint.Style.STROKE);
							float scaleX = (float) getWidth()
									/ grayImage.width();
							float scaleY = (float) getHeight()
									/ grayImage.height();
							for (int i = 0; i < totalR; i++) {
								CvRect r = new CvRect(cvGetSeqElem(facesR, i));
								int x = r.x(), y = r.y(), w = r.width(), h = r
										.height();
								canvas.drawRect(x * scaleX, y * scaleY, (x + w)
										* scaleX, (y + h) * scaleY, paint);
							}
						}
					}
				} else if (S == 1) {
					if (facesS != null) {
						int totalS = facesS.total();
						if (totalS == 1) {
							Log.i("total S", "Total S: " + S);
							String hasil = "S";
							editText_isi.setText(hasil);
							paint.setStrokeWidth(2);
							paint.setStyle(Paint.Style.STROKE);
							float scaleX = (float) getWidth()
									/ grayImage.width();
							float scaleY = (float) getHeight()
									/ grayImage.height();
							for (int i = 0; i < totalS; i++) {
								CvRect r = new CvRect(cvGetSeqElem(facesS, i));
								int x = r.x(), y = r.y(), w = r.width(), h = r
										.height();
								canvas.drawRect(x * scaleX, y * scaleY, (x + w)
										* scaleX, (y + h) * scaleY, paint);
							}
						}

					}
				} else if (T == 1) {
					if (facesT != null) {
						int totalT = facesT.total();
						if (totalT == 1) {
							Log.i("total T", "Total T: " + T);
							String hasil = "T";
							editText_isi.setText(hasil);
							paint.setStrokeWidth(2);
							paint.setStyle(Paint.Style.STROKE);
							float scaleX = (float) getWidth()
									/ grayImage.width();
							float scaleY = (float) getHeight()
									/ grayImage.height();
							for (int i = 0; i < totalT; i++) {
								CvRect r = new CvRect(cvGetSeqElem(facesT, i));
								int x = r.x(), y = r.y(), w = r.width(), h = r
										.height();
								canvas.drawRect(x * scaleX, y * scaleY, (x + w)
										* scaleX, (y + h) * scaleY, paint);
							}
						}

					}
				} else if (U == 1) {
					if (facesU != null) {
						int totalU = facesU.total();
						if (totalU == 1) {
							Log.i("total U", "Total U: " + U);
							String hasil = "U";
							editText_isi.setText(hasil);
							paint.setStrokeWidth(2);
							paint.setStyle(Paint.Style.STROKE);
							float scaleX = (float) getWidth()
									/ grayImage.width();
							float scaleY = (float) getHeight()
									/ grayImage.height();
							for (int i = 0; i < totalU; i++) {
								CvRect r = new CvRect(cvGetSeqElem(facesU, i));
								int x = r.x(), y = r.y(), w = r.width(), h = r
										.height();
								canvas.drawRect(x * scaleX, y * scaleY, (x + w)
										* scaleX, (y + h) * scaleY, paint);
							}
						}

					}
				} else if (V == 1) {
					if (facesV != null) {
						int totalV = facesV.total();
						if (totalV == 1) {
							Log.i("total V", "Total V: " + V);
							String hasil = "V";
							editText_isi.setText(hasil);
							paint.setStrokeWidth(2);
							paint.setStyle(Paint.Style.STROKE);
							float scaleX = (float) getWidth()
									/ grayImage.width();
							float scaleY = (float) getHeight()
									/ grayImage.height();
							for (int i = 0; i < totalV; i++) {
								CvRect r = new CvRect(cvGetSeqElem(facesV, i));
								int x = r.x(), y = r.y(), w = r.width(), h = r
										.height();
								canvas.drawRect(x * scaleX, y * scaleY, (x + w)
										* scaleX, (y + h) * scaleY, paint);
							}
						}

					}
				} else if (W == 1) {
					if (facesW != null) {
						int totalW = facesW.total();
						if (totalW == 1) {
							Log.i("total W", "Total W: " + W);
							String hasil = "W";
							editText_isi.setText(hasil);
							paint.setStrokeWidth(2);
							paint.setStyle(Paint.Style.STROKE);
							float scaleX = (float) getWidth()
									/ grayImage.width();
							float scaleY = (float) getHeight()
									/ grayImage.height();
							for (int i = 0; i < totalW; i++) {
								CvRect r = new CvRect(cvGetSeqElem(facesW, i));
								int x = r.x(), y = r.y(), w = r.width(), h = r
										.height();
								canvas.drawRect(x * scaleX, y * scaleY, (x + w)
										* scaleX, (y + h) * scaleY, paint);
							}
						}

					}
				} else if (X == 1) {
					if (facesX != null) {
						int totalX = facesX.total();
						if (totalX == 1) {
							Log.i("total X", "Total X: " + X);
							String hasil = "X";
							editText_isi.setText(hasil);
							paint.setStrokeWidth(2);
							paint.setStyle(Paint.Style.STROKE);
							float scaleX = (float) getWidth()
									/ grayImage.width();
							float scaleY = (float) getHeight()
									/ grayImage.height();
							for (int i = 0; i < totalX; i++) {
								CvRect r = new CvRect(cvGetSeqElem(facesX, i));
								int x = r.x(), y = r.y(), w = r.width(), h = r
										.height();
								canvas.drawRect(x * scaleX, y * scaleY, (x + w)
										* scaleX, (y + h) * scaleY, paint);
							}
						}

					}
				} else if (Y == 1) {
					if (facesY != null) {
						int totalY = facesY.total();
						if (totalY == 1) {
							Log.i("total Y", "Total Y: " + Y);
							String hasil = "Y";
							editText_isi.setText(hasil);
							paint.setStrokeWidth(2);
							paint.setStyle(Paint.Style.STROKE);
							float scaleX = (float) getWidth()
									/ grayImage.width();
							float scaleY = (float) getHeight()
									/ grayImage.height();
							for (int i = 0; i < totalY; i++) {
								CvRect r = new CvRect(cvGetSeqElem(facesY, i));
								int x = r.x(), y = r.y(), w = r.width(), h = r
										.height();
								canvas.drawRect(x * scaleX, y * scaleY, (x + w)
										* scaleX, (y + h) * scaleY, paint);
							}
						}

					}
				} else if (Z == 1) {
					if (facesZ != null) {
						int totalZ = facesZ.total();
						if (totalZ == 1) {
							Log.i("total Z", "Total Z: " + Z);
							String hasil = "Z";
							editText_isi.setText(hasil);
							paint.setStrokeWidth(2);
							paint.setStyle(Paint.Style.STROKE);
							float scaleX = (float) getWidth()
									/ grayImage.width();
							float scaleY = (float) getHeight()
									/ grayImage.height();
							for (int i = 0; i < totalZ; i++) {
								CvRect r = new CvRect(cvGetSeqElem(facesZ, i));
								int x = r.x(), y = r.y(), w = r.width(), h = r
										.height();
								canvas.drawRect(x * scaleX, y * scaleY, (x + w)
										* scaleX, (y + h) * scaleY, paint);
							}
						}

					}
				} else {
					Log.i("Kosong", "kosong");
				}

			}
		}
	}

	@Override
	public void onBackPressed() {
		Intent i = new Intent(getApplicationContext(), Home.class);
		startActivity(i);
		overridePendingTransition(R.drawable.scrool_up_enter,
				R.drawable.scroll_up_exit);
		finish();
	}
}
