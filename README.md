# Camera2Video

App for recording high fps video. Only pixel 2 device supported

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
$ adb shell am broadcast -a com.example.camera2video.RECORD_SLOW_MOTION --es RECORDING start
```


