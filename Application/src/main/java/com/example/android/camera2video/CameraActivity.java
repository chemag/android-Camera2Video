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
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

public class CameraActivity extends Activity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_camera);
    if (null == savedInstanceState) {
      getFragmentManager()
      .beginTransaction()
      .replace(R.id.container, Camera2VideoFragment.newInstance())
      .commit();
    }

    if (ContextCompat.checkSelfPermission(
      this,
      Manifest.permission.WRITE_EXTERNAL_STORAGE)
        != PackageManager.PERMISSION_GRANTED) {
          ActivityCompat.requestPermissions(
              this,
              new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE
              },
              1);
    }

    if (ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.READ_EXTERNAL_STORAGE)
        != PackageManager.PERMISSION_GRANTED) {
          ActivityCompat.requestPermissions(
              this,
              new String[]{Manifest.permission.READ_EXTERNAL_STORAGE
              },
              2);
    }
  }
}
