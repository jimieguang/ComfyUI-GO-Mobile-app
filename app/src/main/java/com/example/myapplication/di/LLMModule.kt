package com.example.myapplication.di

import android.util.Log
import com.example.myapplication.data.api.BaiLianApiService
import com.example.myapplication.util.LLMPreferences
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * LLM相关依赖注入模块
 */
@Module
@InstallIn(SingletonComponent::class)
object LLMModule {

    /**
     * 动态认证和URL切换的Interceptor
     */
    @Provides
    @Singleton
    @BaiLianClient
    fun provideAuthInterceptor(preferences: LLMPreferences): Interceptor {
        return Interceptor { chain ->
            // 使用 runBlocking 是因为 Interceptor 的接口是同步的，
            // 而我们需要从 DataStore (Flow) 异步获取最新的配置。
            // 这是一个在DI层面处理动态配置的常见模式。
            val config = runBlocking { preferences.getConfig().first() }

            val originalRequest = chain.request()

            // 添加 Authorization 头
            val authRequest = originalRequest.newBuilder()
                .header("Authorization", "Bearer ${config.apiKey}")
                .build()

            chain.proceed(authRequest)
        }
    }

    /**
     * LLM专用的OkHttpClient
     */
    @Provides
    @Singleton
    @BaiLianClient
    fun provideBaiLianOkHttpClient(
        @BaiLianClient authInterceptor: Interceptor
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            Log.d("LLM_API", message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(loggingInterceptor)
            .addInterceptor(authInterceptor) // 添加认证拦截器
            .build()
    }

    /**
     * LLM专用的Retrofit实例
     */
    @Provides
    @Singleton
    @BaiLianRetrofit
    fun provideBaiLianRetrofit(
        @BaiLianClient okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit {
        // BaseUrl 设置一个占位符即可，因为实际的请求URL将是动态的
        return Retrofit.Builder()
            .baseUrl("http://placeholder.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    /**
     * LLM API服务
     */
    @Provides
    @Singleton
    fun provideBaiLianApiService(
        @BaiLianRetrofit retrofit: Retrofit
    ): BaiLianApiService {
        return retrofit.create(BaiLianApiService::class.java)
    }
}

/**
 * 限定符：LLM专用的OkHttpClient
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BaiLianClient

/**
 * 限定符：LLM专用的Retrofit
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BaiLianRetrofit

