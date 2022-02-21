/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.camera2video;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaCodec;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v13.app.FragmentCompat;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class Camera2VideoFragment extends Fragment
    implements View.OnClickListener,
               FragmentCompat.OnRequestPermissionsResultCallback {
  private static final String TAG = Camera2VideoFragment.class.getSimpleName();
  private static final int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
  private static final int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;
  private static final SparseIntArray DEFAULT_ORIENTATIONS =
      new SparseIntArray();
  private static final SparseIntArray INVERSE_ORIENTATIONS =
      new SparseIntArray();
  private static final Queue<Size> CAPTURE_VIDEO_RESOLUTIONS = new LinkedList<>();

  private static final int REQUEST_VIDEO_PERMISSIONS = 1;
  private static final String FRAGMENT_DIALOG = "dialog";
  private static final long RECORDING_DURATION_TIME_MS = 800;
  private static final int PREVIEW_WIDTH = 1280;
  private static final int PREVIEW_HEIGHT = 720;
  private static final int CAPTURE_FPS = 30;
  private static final int VIDEO_ENCODING_BITRATE_BPS = 20000000;

  private static final String[] VIDEO_PERMISSIONS = {
      Manifest.permission.CAMERA,
      Manifest.permission.RECORD_AUDIO,
  };

  static {
    DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90);
    DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0);
    DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270);
    DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180);
  }

  static {
    INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
    INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
    INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
    INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
  }

  static {
    //CAPTURE_VIDEO_RESOLUTIONS.add(new Size(640, 480));
    //CAPTURE_VIDEO_RESOLUTIONS.add(new Size(720, 480));
    CAPTURE_VIDEO_RESOLUTIONS.add(new Size(1280, 720));
  }

  private AutoFitTextureView mTextureView;
  private CameraDevice mCameraDevice;
  private CameraCaptureSession mPreviewSession;
  private CameraCaptureSession mCaptureSession;
  //private CameraConstrainedHighSpeedCaptureSession mPreviewSessionHighSpeed;
  private Size mCameraSize;
  private Size mPreviewSize;
  private Size mVideoSize;
  private MediaRecorder mMediaRecorder;
  private boolean mIsRecordingVideo;
  private HandlerThread mBackgroundThread;
  private Handler mBackgroundHandler;
  private final Semaphore mCameraOpenCloseLock = new Semaphore(1);
  private Range<Integer>[] mAvailableHighSpeedFps;
  private Range<Integer>[] mAvailableFps;
  private Size[] mAvailableResolutions;
  private Integer mSensorOrientation;
  private CaptureRequest.Builder mPreviewBuilder;
  private IntentFilter mRecordIntent;
  private File mOutputVideoFile;

  private final TextureView.SurfaceTextureListener mSurfaceTextureListener =
      new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(
            SurfaceTexture surfaceTexture, int width, int height) {
          openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(
            SurfaceTexture surfaceTexture, int width, int height) {
          configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(
            SurfaceTexture surfaceTexture) {
          return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {}
      };

  private final CameraDevice.StateCallback mStateCallback =
      new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
          mCameraDevice = cameraDevice;
          startPreview();
          mCameraOpenCloseLock.release();
          if (null != mTextureView) {
            configureTransform(
                mTextureView.getWidth(), mTextureView.getHeight());
          }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
          mCameraOpenCloseLock.release();
          cameraDevice.close();
          mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
          mCameraOpenCloseLock.release();
          cameraDevice.close();
          mCameraDevice = null;
          Activity activity = getActivity();
          if (null != activity) {
            activity.finish();
          }
        }
      };

  private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      Log.i(TAG, "Broadcast received");

      final String recordingEvent =
          intent.getStringExtra(CameraIntents.Actions.RECORDING);
      final long durationTimeSec = intent.getLongExtra(
          CameraIntents.Actions.DURATION_TIME_SEC,
          CameraIntents.Constants.DURATION_TIME_INVALID);
      final String recordingFilename =
          intent.getStringExtra(CameraIntents.Actions.RECORDING_FILENAME);
      final int captureFps =
          intent.getIntExtra(CameraIntents.Actions.CAPTURE_FPS, CAPTURE_FPS);
      final int captureVideoHeight =
          intent.getIntExtra(
              CameraIntents.Actions.CAPTURE_VIDEO_HEIGHT,
              CameraIntents.Constants.CAPTURE_VIDEO_SIZE_INVALID);
      final int captureVideoWidth =
          intent.getIntExtra(
              CameraIntents.Actions.CAPTURE_VIDEO_WIDTH,
              CameraIntents.Constants.CAPTURE_VIDEO_SIZE_INVALID);

      final long recordingTimeoutMillis =
          durationTimeSec == CameraIntents.Constants.DURATION_TIME_INVALID
          ? RECORDING_DURATION_TIME_MS
          : TimeUnit.SECONDS.toMillis(durationTimeSec);

      Log.i(TAG, "Receive event: " + recordingEvent);

      if (CameraIntents.Extras.START.equals(recordingEvent)) {
        if (!mIsRecordingVideo) {
          Log.i(TAG, "Start recording: ok");
          startRecordingVideo(
              createVideoFile(recordingFilename),
              captureFps,
              captureVideoHeight,
              captureVideoWidth,
              recordingTimeoutMillis);
        } else {
          Log.e(TAG, "Start recording: failed, already recording");
        }
      } else if (CameraIntents.Extras.STOP.equals(recordingEvent)) {
        if (mIsRecordingVideo) {
          Log.i(TAG, "Stop recording: ok");
          stopRecordingVideo();
        }
      }
    }
  };

  public static Camera2VideoFragment newInstance() {
    return new Camera2VideoFragment();
  }

  // choose a video size with 3x4 aspect ratio. Also, we don't use sizes
  // larger than 1080p, since MediaRecorder cannot handle such a high-resolution
  // video.
  private static Size chooseVideoSize(Size[] availableSizes) {
    for (Size size : availableSizes) {
      if (size.getWidth() == size.getHeight() * 4 / 3 &&
          size.getWidth() <= 1080) {
        return size;
      }
    }
    Log.e(TAG, "Couldn't find any suitable video size");
    return availableSizes[availableSizes.length - 1];
  }

  private static Size
  getRequestedVideoSize(Size[] choices, int width, int height) {
    for (Size choice : choices) {
      if (height == choice.getHeight() && width == choice.getWidth()) {
        Log.i(
            TAG, "Video size: " + choice.getWidth() + "x" + choice.getHeight());
        return choice;
      }
    }

    Log.e(TAG, "Couldn't find reguested size: " + width + "x" + height);
    return choices[choices.length - 1];
  }

  private static Size
  chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
    final int previewWidth = PREVIEW_WIDTH;
    final int previewHeight = PREVIEW_HEIGHT;

    for (Size choice : choices) {
      if (previewHeight == choice.getHeight() &&
          previewWidth == choice.getWidth()) {
        Log.i(
            TAG,
            "Preview Video size: " + choice.getWidth() + "x" +
                choice.getHeight());
        return choice;
      }
    }

    Log.e(TAG, "Couldn't find size: " + previewWidth + "x" + previewHeight);

    // If not working, choosee optimal size: collect the supported resolutions
    // that are at least as big as the preview Surface
    List<Size> bigEnough = new ArrayList<>();
    int w = aspectRatio.getWidth();
    int h = aspectRatio.getHeight();
    for (Size option : choices) {
      if (option.getHeight() == option.getWidth() * h / w &&
          option.getWidth() >= width && option.getHeight() >= height) {
        bigEnough.add(option);
      }
    }

    // Pick the smallest of those, assuming we found any
    if (bigEnough.size() > 0) {
      return Collections.min(bigEnough, new CompareSizesByArea());
    } else {
      Log.e(TAG, "Couldn't find any suitable preview size");
      return choices[0];
    }
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mRecordIntent = new IntentFilter(CameraIntents.Intents.RECORD_SLOW_MOTION);
    getActivity().registerReceiver(mBroadcastReceiver, mRecordIntent);
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater,
      ViewGroup container,
      Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_camera2_video, container, false);
  }

  @Override
  public void onViewCreated(final View view, Bundle savedInstanceState) {
    mTextureView = (AutoFitTextureView)view.findViewById(R.id.texture);
    view.findViewById(R.id.info).setOnClickListener(this);
  }

  @Override
  public void onResume() {
    super.onResume();
    startBackgroundThread();
    if (mTextureView.isAvailable()) {
      openCamera(mTextureView.getWidth(), mTextureView.getHeight());
    } else {
      mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
    }
  }

  @Override
  public void onPause() {
    closeCamera();
    stopBackgroundThread();
    super.onPause();
  }

  @Override
  public void onClick(View view) {
    switch (view.getId()) {
      case R.id.info: {
        Activity activity = getActivity();
        if (null != activity) {
          new AlertDialog.Builder(activity)
              .setMessage(R.string.intro_message)
              .setPositiveButton(android.R.string.ok, null)
              .show();
        }
        break;
      }
    }
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    getActivity().unregisterReceiver(mBroadcastReceiver);
  }

  private void startBackgroundThread() {
    mBackgroundThread = new HandlerThread("CameraBackground");
    mBackgroundThread.start();
    mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
  }

  private void stopBackgroundThread() {
    mBackgroundThread.quitSafely();
    try {
      mBackgroundThread.join();
      mBackgroundThread = null;
      mBackgroundHandler = null;
    } catch (InterruptedException e) {
      Log.e(TAG, Log.getStackTraceString(e));
    }
  }

  // Gets whether you should show UI with rationale for requesting permissions.
  private boolean shouldShowRequestPermissionRationale(String[] permissions) {
    for (String permission : permissions) {
      if (FragmentCompat.shouldShowRequestPermissionRationale(
              this, permission)) {
        return true;
      }
    }
    return false;
  }

  private void requestVideoPermissions() {
    if (shouldShowRequestPermissionRationale(VIDEO_PERMISSIONS)) {
      new ConfirmationDialog().show(getChildFragmentManager(), FRAGMENT_DIALOG);
    } else {
      FragmentCompat.requestPermissions(
          this, VIDEO_PERMISSIONS, REQUEST_VIDEO_PERMISSIONS);
    }
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode,
      @NonNull String[] permissions,
      @NonNull int[] grantResults) {
    Log.d(TAG, "onRequestPermissionsResult");
    if (requestCode == REQUEST_VIDEO_PERMISSIONS) {
      if (grantResults.length == VIDEO_PERMISSIONS.length) {
        for (int result : grantResults) {
          if (result != PackageManager.PERMISSION_GRANTED) {
            ErrorDialog.newInstance(getString(R.string.permission_request))
                .show(getChildFragmentManager(), FRAGMENT_DIALOG);
            break;
          }
        }
      } else {
        ErrorDialog.newInstance(getString(R.string.permission_request))
            .show(getChildFragmentManager(), FRAGMENT_DIALOG);
      }
    } else {
      super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
  }

  private boolean hasPermissionsGranted(String[] permissions) {
    for (String permission : permissions) {
      if (ActivityCompat.checkSelfPermission(getActivity(), permission) !=
          PackageManager.PERMISSION_GRANTED) {
        return false;
      }
    }
    return true;
  }

  private Size getHighSpeedResolution(final Size[] highSpeedVideoResolutions) {
    if (highSpeedVideoResolutions == null) {
      throw new NullPointerException("High speed resolutions list is null");
    }
    if (highSpeedVideoResolutions.length == 0) {
      throw new IllegalArgumentException("Empty high speed resolutions list");
    }
    Iterator<Size> iterator = CAPTURE_VIDEO_RESOLUTIONS.iterator();
    final List<Size> availableResolutions = Arrays.asList(highSpeedVideoResolutions);
    while (iterator.hasNext()) {
      final Size res = iterator.next();
      if (availableResolutions.contains(res)) {
        Log.d(
            TAG,
            "Capture video resolution: " + res.getWidth() + "x" +
                res.getHeight());
        return res;
      }
    }
    final Size res = highSpeedVideoResolutions[0];
    Log.e(
        TAG,
        "Capture video resolution not found, use default: " + res.getWidth() +
            "x" + res.getHeight());
    return res;
  }

  @SuppressWarnings("MissingPermission")
  private void openCamera(int width, int height) {
    if (!hasPermissionsGranted(VIDEO_PERMISSIONS)) {
      requestVideoPermissions();
      return;
    }
    final Activity activity = getActivity();
    if (null == activity || activity.isFinishing()) {
      return;
    }
    CameraManager manager =
        (CameraManager)activity.getSystemService(Context.CAMERA_SERVICE);

    try {
      Log.d(TAG, "tryAcquire");
      if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
        throw new RuntimeException("Time out waiting to lock camera opening.");
      }
      String cameraId = manager.getCameraIdList()[0];

      // Choose the sizes for camera preview and video recording
      CameraCharacteristics characteristics =
          manager.getCameraCharacteristics(cameraId);

      mAvailableFps = characteristics.get(
          CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
      Log.i(TAG, "Fps available: " + Arrays.toString(mAvailableFps));

      StreamConfigurationMap map = characteristics.get(
          CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
      StreamConfigurationMap configs = characteristics.get(
          CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

      mAvailableResolutions = configs.getOutputSizes(MediaCodec.class);
      Log.i(TAG, "Available capture resolutions: " + Arrays.toString(mAvailableResolutions));
      mAvailableHighSpeedFps = map.getHighSpeedVideoFpsRanges();
      final Size highSpeedRes = getHighSpeedResolution(mAvailableResolutions);

      mCameraSize = getRequestedVideoSize(
          map.getOutputSizes(SurfaceTexture.class),
          highSpeedRes.getWidth(),
          highSpeedRes.getHeight());

      mSensorOrientation =
          characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
      if (map == null) {
        throw new RuntimeException("Cannot get available preview/video sizes");
      }
      mVideoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder.class));
      mPreviewSize = chooseOptimalSize(
          map.getOutputSizes(SurfaceTexture.class), width, height, mVideoSize);

      int orientation = getResources().getConfiguration().orientation;
      if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
        mTextureView.setAspectRatio(
            mPreviewSize.getWidth(), mPreviewSize.getHeight());
      } else {
        mTextureView.setAspectRatio(
            mPreviewSize.getHeight(), mPreviewSize.getWidth());
      }
      configureTransform(width, height);
      mMediaRecorder = new MediaRecorder();

      manager.openCamera(cameraId, mStateCallback, null);
    } catch (CameraAccessException e) {
      Toast.makeText(activity, "Cannot access the camera.", Toast.LENGTH_SHORT)
          .show();
      activity.finish();
    } catch (NullPointerException e) {
      // Currently an NPE is thrown when the Camera2API is used but not
      // supported on the device this code runs.
      ErrorDialog.newInstance(getString(R.string.camera_error))
          .show(getChildFragmentManager(), FRAGMENT_DIALOG);
    } catch (InterruptedException e) {
      throw new RuntimeException(
          "Interrupted while trying to lock camera opening.");
    }
  }

  private void closeCamera() {
    try {
      mCameraOpenCloseLock.acquire();
      closePreviewSession();
      if (null != mCameraDevice) {
        mCameraDevice.close();
        mCameraDevice = null;
      }
      if (null != mMediaRecorder) {
        mMediaRecorder.release();
        mMediaRecorder = null;
      }
    } catch (InterruptedException e) {
      throw new RuntimeException(
          "Interrupted while trying to lock camera closing.");
    } finally {
      mCameraOpenCloseLock.release();
    }
  }

  private void startPreview() {
    if (null == mCameraDevice || !mTextureView.isAvailable() ||
        null == mPreviewSize) {
      return;
    }
    try {
      closePreviewSession();
      SurfaceTexture texture = mTextureView.getSurfaceTexture();
      assert texture != null;
      texture.setDefaultBufferSize(
          mPreviewSize.getWidth(), mPreviewSize.getHeight());
      mPreviewBuilder =
          mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

      Surface previewSurface = new Surface(texture);
      mPreviewBuilder.addTarget(previewSurface);

      mCameraDevice.createCaptureSession(
          Collections.singletonList(previewSurface),
          new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession session) {
              mPreviewSession = session;
              updatePreview();
            }

            @Override
            public void onConfigureFailed(
                @NonNull CameraCaptureSession session) {
              Activity activity = getActivity();
              if (null != activity) {
                Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show();
              }
            }
          },
          mBackgroundHandler);
    } catch (CameraAccessException e) {
      Log.e(TAG, Log.getStackTraceString(e));
    }
  }

  private void updatePreview() {
    if (null == mCameraDevice) {
      return;
    }
    try {
      HandlerThread thread = new HandlerThread("CameraHighSpeedPreview");
      thread.start();

      if (mIsRecordingVideo) {
        setUpCaptureRequestBuilder(mPreviewBuilder);
        //List<CaptureRequest> mPreviewBuilderBurst =
        //    mPreviewSessionHighSpeed.createHighSpeedRequestList(
        //        mPreviewBuilder.build());
        //mPreviewSessionHighSpeed.setRepeatingBurst(
        //    mPreviewBuilderBurst, null, mBackgroundHandler);
      mCaptureSession.setRepeatingRequest(mPreviewBuilder.build(),
          null, mBackgroundHandler);

      } else {
        mPreviewSession.setRepeatingRequest(
            mPreviewBuilder.build(), null, mBackgroundHandler);
      }
    } catch (CameraAccessException e) {
      Log.e(TAG, Log.getStackTraceString(e));
    }
  }

  private Range<Integer> getHighestFpsRange(Range<Integer>[] fpsRanges) {
    Range currentFpsMax = fpsRanges[0];

    // find max sum of fps ranges (min, max), for pixel should be: [240,240]
    for (Range<Integer> fpsValue : fpsRanges) {
      final int maxLower =
          Integer.parseInt(currentFpsMax.getLower().toString());
      final int maxUpper =
          Integer.parseInt(currentFpsMax.getUpper().toString());

      final int actualLower = Integer.parseInt(fpsValue.getLower().toString());
      final int actualUpper = Integer.parseInt(fpsValue.getUpper().toString());

      final int max = (maxUpper + maxLower);
      final int actual = (actualLower + actualUpper);

      if (max < actual) {
        currentFpsMax = fpsValue;
      }
    }

    Log.i(
        TAG,
        "Max Fps : " + currentFpsMax.getLower() + " " +
            currentFpsMax.getUpper());
    return currentFpsMax;
  }

  private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
   // Range<Integer> fpsRange = getHighestFpsRange(mAvailableHighSpeedFps);
   // builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
  }

  // Configures the necessary {@link android.graphics.Matrix} transformation to
  // {@link mTextureView}. This method should not to be called until the camera
  // preview size is determined in openCamera, or until the size of
  // `mTextureView` is fixed.
  private void configureTransform(int viewWidth, int viewHeight) {
    Activity activity = getActivity();
    if (null == mTextureView || null == mPreviewSize || null == activity) {
      return;
    }
    int rotation =
        activity.getWindowManager().getDefaultDisplay().getRotation();
    Matrix matrix = new Matrix();
    RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
    RectF bufferRect =
        new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
    float centerX = viewRect.centerX();
    float centerY = viewRect.centerY();
    if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
      bufferRect.offset(
          centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
      matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
      float scale = Math.max(
          (float)viewHeight / mPreviewSize.getHeight(),
          (float)viewWidth / mPreviewSize.getWidth());
      matrix.postScale(scale, scale, centerX, centerY);
      matrix.postRotate(90 * (rotation - 2), centerX, centerY);
    }
    mTextureView.setTransform(matrix);
  }

  private String createVideoFile(String videoName) {
    final String videoPath =
        Environment
            .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .getAbsolutePath();
    if (videoName == null || videoName.isEmpty()) {
      videoName = "recording" + System.currentTimeMillis() + ".mp4";
    }
    mOutputVideoFile = new File(videoPath, videoName);

    if (mOutputVideoFile.exists()) {
      mOutputVideoFile.delete();
    }
    return mOutputVideoFile.getAbsolutePath();
  }

  private boolean isFpsSupported(int captureFps) {
    for (Range<Integer> fpsValue : mAvailableFps) {
      if (captureFps == fpsValue.getLower() || captureFps == fpsValue.getUpper()) {
        return true;
      }
    }
    Log.e(TAG, "Capture fps [" + captureFps + "] not supported");
    return false;
  }

  private boolean isResolutionSupported(int width, int height) {
    for (Size res : mAvailableResolutions) {
      if (Math.max(width, height) == Math.max(res.getWidth(), res.getHeight())
          && Math.min(width, height) == Math.min(res.getWidth(), res.getHeight())) {
        return true;
      }
    }
    Log.e(TAG, "Resolution [" + width + "x" + height + "] not supported");
    return false;
  }

  private void setUpMediaRecorder(
      final String recordingFilename,
      final int captureFps,
      final int captureVideoWidth,
      final int captureVideoHeight) throws IOException {
    final Activity activity = getActivity();
    if (null == activity) {
      return;
    }
    mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
    mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
    mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

    mMediaRecorder.setOutputFile(recordingFilename);
    mMediaRecorder.setVideoEncodingBitRate(VIDEO_ENCODING_BITRATE_BPS);
    Log.i(TAG, "Capture fps " + captureFps);
    isFpsSupported(captureFps);
    mMediaRecorder.setVideoFrameRate(captureFps);

    int width = mCameraSize.getWidth();
    int height = mCameraSize.getHeight();
    if (captureVideoWidth != CameraIntents.Constants.CAPTURE_VIDEO_SIZE_INVALID &&
      captureVideoHeight != CameraIntents.Constants.CAPTURE_VIDEO_SIZE_INVALID) {
      width = captureVideoWidth;
      height = captureVideoHeight;
    }

    Log.i(TAG, "Capture resolution: " + width + "x" + height);
    isResolutionSupported(width, height);
    mMediaRecorder.setVideoSize(width, height);
    mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
    mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

    int rotation =
        activity.getWindowManager().getDefaultDisplay().getRotation();
    switch (mSensorOrientation) {
      case SENSOR_ORIENTATION_DEFAULT_DEGREES:
        mMediaRecorder.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation));
        break;
      case SENSOR_ORIENTATION_INVERSE_DEGREES:
        mMediaRecorder.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation));
        break;
    }
    mMediaRecorder.prepare();
  }

  private void startRecordingVideo(
      final String recordingFilename,
      final int captureFps,
      final int captureVideoHeight,
      final int captureVideoWidth,
      final long timeoutMillis) {
    mIsRecordingVideo = true;
    if (null == mCameraDevice || !mTextureView.isAvailable() ||
        null == mPreviewSize) {
      return;
    }
    Log.i(TAG, "Recording time " + timeoutMillis + " ms");
    try {
      closePreviewSession();
      setUpMediaRecorder(
          recordingFilename,
          captureFps,
          captureVideoHeight,
          captureVideoWidth);
      SurfaceTexture texture = mTextureView.getSurfaceTexture();
      assert texture != null;
      texture.setDefaultBufferSize(
          mPreviewSize.getWidth(), mPreviewSize.getHeight());
      mPreviewBuilder =
          mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
      List<Surface> surfaces = new ArrayList<>();

      // Set up Surface for the camera preview
      Surface previewSurface = new Surface(texture);
      surfaces.add(previewSurface);
      mPreviewBuilder.addTarget(previewSurface);

      // Set up Surface for the MediaRecorder
      Surface recorderSurface = mMediaRecorder.getSurface();
      surfaces.add(recorderSurface);
      mPreviewBuilder.addTarget(recorderSurface);

      // Start a capture session
      // Once the session starts, we can update the UI and start recording
      //mCameraDevice.createConstrainedHighSpeedCaptureSession(
      mCameraDevice.createCaptureSession(
          surfaces, new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(
                @NonNull CameraCaptureSession cameraCaptureSession) {
              mPreviewSession = cameraCaptureSession;
              mCaptureSession = mPreviewSession;
              updatePreview();
              getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                  mMediaRecorder.start();

                  new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                      stopRecordingVideo();
                    }
                  }, timeoutMillis);
                }
              });
            }

            @Override
            public void onConfigureFailed(
                @NonNull CameraCaptureSession cameraCaptureSession) {
              Activity activity = getActivity();
              if (null != activity) {
                Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show();
              }
            }
          }, mBackgroundHandler);
    } catch (CameraAccessException | IOException e) {
      Log.e(TAG, Log.getStackTraceString(e));
    }
  }

  private void closePreviewSession() {
    if (mPreviewSession != null) {
      mPreviewSession.close();
      mPreviewSession = null;
    }
  }

  private void stopRecordingVideo() {
    mIsRecordingVideo = false;
    mMediaRecorder.pause();
    // Close the session before we stop the MediaRecorder and kill the input surface.
    closePreviewSession();
    mMediaRecorder.stop();
    mMediaRecorder.reset();
    Activity activity = getActivity();
    if (null != activity) {
      Toast.makeText(activity, "Video recorded", Toast.LENGTH_SHORT).show();
      Log.i(TAG, "Video recorded");
    }
    startPreview();
  }

  static class CompareSizesByArea implements Comparator<Size> {
    @Override
    public int compare(Size lhs, Size rhs) {
      // We cast here to ensure the multiplications won't overflow
      return Long.signum(
          (long)lhs.getWidth() * lhs.getHeight() -
          (long)rhs.getWidth() * rhs.getHeight());
    }
  }

  public static class ErrorDialog extends DialogFragment {
    private static final String ARG_MESSAGE = "message";

    public static ErrorDialog newInstance(String message) {
      ErrorDialog dialog = new ErrorDialog();
      Bundle args = new Bundle();
      args.putString(ARG_MESSAGE, message);
      dialog.setArguments(args);
      return dialog;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      final Activity activity = getActivity();
      return new AlertDialog.Builder(activity)
          .setMessage(getArguments().getString(ARG_MESSAGE))
          .setPositiveButton(
              android.R.string.ok,
              new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                  activity.finish();
                }
              })
          .create();
    }
  }

  public static class ConfirmationDialog extends DialogFragment {
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      final Fragment parent = getParentFragment();
      return new AlertDialog.Builder(getActivity())
          .setMessage(R.string.permission_request)
          .setPositiveButton(
              android.R.string.ok,
              new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                  FragmentCompat.requestPermissions(
                      parent, VIDEO_PERMISSIONS, REQUEST_VIDEO_PERMISSIONS);
                }
              })
          .setNegativeButton(
              android.R.string.cancel,
              new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                  parent.getActivity().finish();
                }
              })
          .create();
    }
  }
}
