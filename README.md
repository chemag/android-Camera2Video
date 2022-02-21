# Camera2Video

App for recording high fps video using CLI. Only pixel devices supported.

# 1. Build

```
$ cd ~/proj/android-Camera2Video
$ ./gradlew build
...
BUILD SUCCESSFUL in 19s
54 actionable tasks: 13 executed, 41 up-to-date
```

In some cases, you may need to point to a java 8 distribution:
```
$ JAVA_HOME=/usr/lib/jvm/java-1.8.0 ./gradlew build
```

# 2. Install

```
$ ./gradlew installDebug
...
BUILD SUCCESSFUL in 3s
27 actionable tasks: 1 executed, 26 up-to-date
```

In some cases, you may need to point to a java 8 distribution:
```
$ JAVA_HOME=/usr/lib/jvm/java-1.8.0 ./gradlew installDebug
```

Once you have installed the app, you can add the required permissions either
by accepting in the menu, or using this command:

```
$ adb shell pm grant com.example.android.camera2video android.permission.CAMERA
$ adb shell pm grant com.example.android.camera2video android.permission.RECORD_AUDIO
$ adb shell pm grant com.example.android.camera2video android.permission.READ_EXTERNAL_STORAGE
$ adb shell pm grant com.example.android.camera2video android.permission.WRITE_EXTERNAL_STORAGE
```

# 3. Recording

Example 1: simple recording.
```
$ adb shell am broadcast \
    -a com.example.android.camera2video.RECORD_SLOW_MOTION \
    --es RECORDING start
```

Example 2: record to a specific filename, for 2 seconds, 1280x720 resolution
and using 30 fps.
```
$ adb shell am broadcast \
    -a com.example.android.camera2video.RECORD_SLOW_MOTION \
    --es RECORDING start \
    --el DURATION_TIME_SEC 2 \
    --es RECORDING_FILENAME foo.mp4 \
    --ei CAPTURE_VIDEO_WIDTH 1280 \
    --ei CAPTURE_VIDEO_HEIGHT 720 \
    --ei CAPTURE_FPS 30
```
