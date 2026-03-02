package com.example.myapplication.di

import com.example.myapplication.data.api.ComfyApiService
import com.example.myapplication.data.repository.ComfyUIRepository
import com.example.myapplication.data.repository.ComfyUIRepositoryImpl
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * 网络相关依赖注入模块
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {
    
    @Binds
    abstract fun bindComfyUIRepository(
        comfyUIRepositoryImpl: ComfyUIRepositoryImpl
    ): ComfyUIRepository
    
    companion object {
        
        @Provides
        @Singleton
        fun provideJson(): Json {
            return Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = false
                coerceInputValues = true
                explicitNulls = false
            }
        }
        
        @Provides
        @Singleton
        fun provideHttpLoggingInterceptor(): HttpLoggingInterceptor {
            return HttpLoggingInterceptor().apply {
                // 改为 HEADERS 避免读取大文件请求体（视频上传时会导致两次读取）
                level = HttpLoggingInterceptor.Level.HEADERS
            }
        }
        
        @Provides
        @Singleton
        fun provideOkHttpClient(
            loggingInterceptor: HttpLoggingInterceptor
        ): OkHttpClient {
            return OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                // 增加超时时间，ComfyUI的object_info响应可能很大
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS) // 读取超时增加到2分钟
                .writeTimeout(60, TimeUnit.SECONDS)
                // 允许重试
                .retryOnConnectionFailure(true)
                .build()
        }
        
        @Provides
        @Singleton
        fun provideRetrofit(
            okHttpClient: OkHttpClient,
            json: Json
        ): Retrofit {
            return Retrofit.Builder()
                .baseUrl("http://127.0.0.1:8188/") // 默认URL，后续会动态修改
                .client(okHttpClient)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
        }
        
        @Provides
        @Singleton
        fun provideComfyApiService(retrofit: Retrofit): ComfyApiService {
            return retrofit.create(ComfyApiService::class.java)
        }
    }
}
