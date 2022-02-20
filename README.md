# Camera2Video

App for recording high fps video using CLI. Only pixel devices supported.

# 1. Build

```
$ cd ~/proj/android-Camera2Video
$ ./gradlew build
Starting a Gradle Daemon, 1 incompatible Daemon could not be reused, use --status for details

> Configure project :Application
NDK is missing a "platforms" directory.
If you are using NDK, verify the ndk.dir is set to a valid NDK directory.  It is currently set to /opt/android_sdk/ndk-bundle.
If you are not using NDK, unset the NDK variable from ANDROID_NDK_HOME or local.properties to remove this warning.

WARNING: Configuration 'compile' is obsolete and has been replaced with 'implementation' and 'api'.
It will be removed at the end of 2018. For more information see: http://d.android.com/r/tools/update-dependency-configurations.html
Observed package id 'build-tools;29.0.2' in inconsistent location '/opt/android_sdk/build-tools/latest' (Expected '/opt/android_sdk/build-tools/29.0.2')
Already observed package id 'build-tools;29.0.2' in '/opt/android_sdk/build-tools/29.0.2'. Skipping duplicate at '/opt/android_sdk/build-tools/latest'

> Task :Application:compileDebugJavaWithJavac
Note: android-Camera2Video/Application/src/main/java/com/example/android/camera2video/Camera2VideoFragment.java uses unchecked or unsafe operations.
Note: Recompile with -Xlint:unchecked for details.

> Task :Application:compileReleaseJavaWithJavac
Note: android-Camera2Video/Application/src/main/java/com/example/android/camera2video/Camera2VideoFragment.java uses unchecked or unsafe operations.
Note: Recompile with -Xlint:unchecked for details.

> Task :Application:lint
Ran lint on variant debug: 21 issues found
Ran lint on variant release: 21 issues found
Wrote HTML report to file://android-Camera2Video/Application/build/reports/lint-results.html
Wrote XML report to file://android-Camera2Video/Application/build/reports/lint-results.xml


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
> Configure project :Application
NDK is missing a "platforms" directory.
If you are using NDK, verify the ndk.dir is set to a valid NDK directory.  It is currently set to /opt/android_sdk/ndk-bundle.
If you are not using NDK, unset the NDK variable from ANDROID_NDK_HOME or local.properties to remove this warning.

WARNING: Configuration 'compile' is obsolete and has been replaced with 'implementation' and 'api'.
It will be removed at the end of 2018. For more information see: http://d.android.com/r/tools/update-dependency-configurations.html

> Task :Application:installDebug
Installing APK 'Application-debug.apk' on 'Smart TV Pro - 11' for Application:debug
Installed on 1 device.


BUILD SUCCESSFUL in 3s
27 actionable tasks: 1 executed, 26 up-to-date
```

In some cases, you may need to point to a java 8 distribution:
```
$ JAVA_HOME=/usr/lib/jvm/java-1.8.0 ./gradlew installDebug
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
