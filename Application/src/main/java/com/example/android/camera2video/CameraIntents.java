package com.example.android.camera2video;

public class CameraIntents {
  // intents names from the Manifest file
  public static class Intents {
    public static final String RECORD_SLOW_MOTION =
        "com.example.android.camera2video.RECORD_SLOW_MOTION";
  }

  public static class Actions {
    public static final String RECORDING = "RECORDING";
    public static final String DURATION_TIME_SEC = "DURATION_TIME_SEC";
  }

  public static class Extras {
    public static final String START = "start";
    public static final String STOP = "stop";
  }

  public static class Constants {
    public static final long DURATION_TIME_INVALID = -1;
  }
}
