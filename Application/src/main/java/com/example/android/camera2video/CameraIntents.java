package com.example.android.camera2video;

public class CameraIntents {
  // intents names from the Manifest file
  public static class Intents {
    public static final String RECORD_SLOW_MOTION =
        "com.example.android.camera2video.RECORD_SLOW_MOTION";
  }

  public static class Actions {
    public static final String CAPTURE_FPS = "CAPTURE_FPS";
    public static final String CAPTURE_VIDEO_HEIGHT = "CAPTURE_VIDEO_HEIGHT";
    public static final String CAPTURE_VIDEO_WIDTH = "CAPTURE_VIDEO_WIDTH";
    public static final String DURATION_TIME_SEC = "DURATION_TIME_SEC";
    public static final String RECORDING = "RECORDING";
    public static final String RECORDING_FILENAME = "RECORDING_FILENAME";
  }

  public static class Extras {
    public static final String START = "start";
    public static final String STOP = "stop";
  }

  public static class Constants {
    public static final long DURATION_TIME_INVALID = -1;
    public static final int CAPTURE_VIDEO_SIZE_INVALID = -1;
  }
}
