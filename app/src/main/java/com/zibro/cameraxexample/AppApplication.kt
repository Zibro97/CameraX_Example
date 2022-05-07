package com.zibro.cameraxexample

import android.app.Application
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraXConfig

class AppApplication:Application(),CameraXConfig.Provider {
    //CameraX에 기본 설정값 설정
    override fun getCameraXConfig(): CameraXConfig = Camera2Config.defaultConfig()
}