package com.example.myapplication.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import coil.ImageLoader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 应用程序级别的依赖注入模块
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
        name = "comfyui_go_preferences"
    )
    
    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }
    
    @Provides
    @Singleton
    fun provideApplicationContext(@ApplicationContext context: Context): Context {
        return context
    }
    
    @Provides
    @Singleton
    fun provideImageLoader(@ApplicationContext context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .crossfade(true)
            .build()
    }
    
    // WorkflowJsonManager 已通过 @Inject constructor 自动注入，无需手动提供
    // ImageDownloadManager 已通过 @Inject constructor 自动注入，无需手动提供
    
}
