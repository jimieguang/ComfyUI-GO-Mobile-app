package com.example.myapplication

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * ComfyUI GO 应用程序入口
 * 使用 Hilt 进行依赖注入
 */
@HiltAndroidApp
class ComfyUIGoApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        // 初始化应用程序级别的组件
    }
}
