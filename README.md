# Camera2Video

App for recording high fps video. Only pixel devices supported

## Build
```
$ cd ~/proj/android-Camera2Video
$ ./gradlew build
```

## Install
```
$ ./gradlew installDebug
```

## Recording
```
$ adb shell am broadcast -a com.example.android.camera2video.RECORD_SLOW_MOTION --es RECORDING start
$ adb shell am broadcast -a com.example.android.camera2video.RECORD_SLOW_MOTION \
    --es RECORDING start \
    --el DURATION_TIME_SEC 2 \
    --es RECORDING_FILENAME recording.mp4 \
    --ei CAPTURE_FPS 30
```


