package com.example.myapplication.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import com.example.myapplication.data.model.CustomRule
import com.example.myapplication.data.model.TextOperationType

// 服务商定义
object LLMProvider {
    const val ALIYUN = "aliyun"
    const val SILICON_FLOW = "silicon_flow"
}

// DataStore实例
private val Context.llmPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "llm_preferences"
)

/**
 * LLM配置管理
 * 使用DataStore存储API配置
 */
@Singleton
class LLMPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val dataStore = context.llmPreferencesDataStore
    
    companion object {
        // 服务商选择
        private val KEY_PROVIDER = stringPreferencesKey("llm_provider")

        // 阿里云百炼 (通义千问) 配置键
        private val KEY_ALIYUN_API_KEY = stringPreferencesKey("aliyun_api_key")
        private val KEY_ALIYUN_MODEL_NAME = stringPreferencesKey("aliyun_model_name")
        
        // 硅基流动 (SiliconFlow) 配置键
        private val KEY_SILICON_FLOW_API_KEY = stringPreferencesKey("silicon_flow_api_key")
        private val KEY_SILICON_FLOW_MODEL_NAME = stringPreferencesKey("silicon_flow_model_name")
        private val KEY_SILICON_FLOW_BASE_URL = stringPreferencesKey("silicon_flow_base_url")

        // 通用配置键
        private val KEY_TEMPERATURE = floatPreferencesKey("temperature")
        private val KEY_MAX_TOKENS = intPreferencesKey("max_tokens")
        
        // 预设管理
        private val KEY_DISABLED_PRESETS = stringSetPreferencesKey("disabled_presets") // enum name set
        private val KEY_CUSTOM_RULES_JSON = stringPreferencesKey("custom_rules_json")  // json array
        
        // 默认值
        const val DEFAULT_ALIYUN_MODEL = "qwen-turbo"
        const val DEFAULT_SILICON_FLOW_MODEL = "Qwen/Qwen2-7B-Instruct" // 硅基流动默认模型
        const val DEFAULT_TEMPERATURE = 0.7f
        const val DEFAULT_MAX_TOKENS = 2000
        const val DEFAULT_ALIYUN_BASE_URL = "https://dashscope.aliyuncs.com/"
        const val DEFAULT_SILICON_FLOW_BASE_URL = "https://api.siliconflow.cn/v1"
    }

    /**
     * 保存当前选择的服务商
     */
    suspend fun saveProvider(provider: String) {
        dataStore.edit { preferences ->
            preferences[KEY_PROVIDER] = provider
        }
    }

    /**
     * 获取当前选择的服务商
     */
    fun getProvider(): Flow<String> {
        return dataStore.data.map { preferences ->
            preferences[KEY_PROVIDER] ?: LLMProvider.ALIYUN // 默认阿里云
        }
    }
    
    /**
     * 保存API Key（根据服务商）
     */
    suspend fun saveApiKey(apiKey: String, provider: String) {
        val key = when (provider) {
            LLMProvider.ALIYUN -> KEY_ALIYUN_API_KEY
            LLMProvider.SILICON_FLOW -> KEY_SILICON_FLOW_API_KEY
            else -> throw IllegalArgumentException("Unsupported provider: $provider")
        }
        dataStore.edit { preferences ->
            preferences[key] = apiKey
        }
    }
    
    /**
     * 保存模型配置（根据服务商）
     */
    suspend fun saveModelConfig(modelName: String, temperature: Float, maxTokens: Int, provider: String) {
        dataStore.edit { preferences ->
            val modelKey = when (provider) {
                LLMProvider.ALIYUN -> KEY_ALIYUN_MODEL_NAME
                LLMProvider.SILICON_FLOW -> KEY_SILICON_FLOW_MODEL_NAME
                else -> throw IllegalArgumentException("Unsupported provider: $provider")
            }
            preferences[modelKey] = modelName
            preferences[KEY_TEMPERATURE] = temperature
            preferences[KEY_MAX_TOKENS] = maxTokens
        }
    }

    /**
     * 检查API Key是否已配置（根据服务商）
     */
    fun hasApiKey(provider: String): Flow<Boolean> {
        val key = when (provider) {
            LLMProvider.ALIYUN -> KEY_ALIYUN_API_KEY
            LLMProvider.SILICON_FLOW -> KEY_SILICON_FLOW_API_KEY
            else -> return kotlinx.coroutines.flow.flowOf(false)
        }
        return dataStore.data.map { preferences ->
            (preferences[key] ?: "").isNotBlank()
        }
    }
    
    /**
     * 清除所有LLM相关的配置
     */
    suspend fun clearLLMConfig() {
        dataStore.edit { preferences ->
            preferences.remove(KEY_PROVIDER)
            preferences.remove(KEY_ALIYUN_API_KEY)
            preferences.remove(KEY_ALIYUN_MODEL_NAME)
            preferences.remove(KEY_SILICON_FLOW_API_KEY)
            preferences.remove(KEY_SILICON_FLOW_MODEL_NAME)
            preferences.remove(KEY_SILICON_FLOW_BASE_URL)
            preferences.remove(KEY_TEMPERATURE)
            preferences.remove(KEY_MAX_TOKENS)
        }
    }
    
    /**
     * 获取完整配置
     */
    data class LLMConfig(
        val provider: String,
        val apiKey: String,
        val modelName: String,
        val temperature: Float,
        val maxTokens: Int,
        val baseUrl: String
    )
    
    /**
     * 获取完整配置的Flow
     */
    fun getConfig(): Flow<LLMConfig> {
        return dataStore.data.map { preferences ->
            val provider = preferences[KEY_PROVIDER] ?: LLMProvider.ALIYUN
            
            val apiKey = when (provider) {
                LLMProvider.ALIYUN -> preferences[KEY_ALIYUN_API_KEY] ?: ""
                LLMProvider.SILICON_FLOW -> preferences[KEY_SILICON_FLOW_API_KEY] ?: ""
                else -> ""
            }
            
            val modelName = when (provider) {
                LLMProvider.ALIYUN -> preferences[KEY_ALIYUN_MODEL_NAME] ?: DEFAULT_ALIYUN_MODEL
                LLMProvider.SILICON_FLOW -> preferences[KEY_SILICON_FLOW_MODEL_NAME] ?: DEFAULT_SILICON_FLOW_MODEL
                else -> DEFAULT_ALIYUN_MODEL
            }
            
            val baseUrl = when (provider) {
                LLMProvider.ALIYUN -> DEFAULT_ALIYUN_BASE_URL // 阿里云Base URL固定
                LLMProvider.SILICON_FLOW -> preferences[KEY_SILICON_FLOW_BASE_URL] ?: DEFAULT_SILICON_FLOW_BASE_URL
                else -> DEFAULT_ALIYUN_BASE_URL
            }

            LLMConfig(
                provider = provider,
                apiKey = apiKey,
                modelName = modelName,
                temperature = preferences[KEY_TEMPERATURE] ?: DEFAULT_TEMPERATURE,
                maxTokens = preferences[KEY_MAX_TOKENS] ?: DEFAULT_MAX_TOKENS,
                baseUrl = baseUrl
            )
        }
    }

    /**
     * 预设管理：禁用的内置预设类型（按枚举名保存）
     */
    fun getDisabledPresetTypes(): Flow<Set<TextOperationType>> {
        return dataStore.data.map { prefs ->
            (prefs[KEY_DISABLED_PRESETS] ?: emptySet()).mapNotNull {
                runCatching { TextOperationType.valueOf(it) }.getOrNull()
            }.toSet()
        }
    }

    suspend fun setDisabledPresetTypes(types: Set<TextOperationType>) {
        dataStore.edit { prefs ->
            prefs[KEY_DISABLED_PRESETS] = types.map { it.name }.toSet()
        }
    }

    /**
     * 预设管理：自定义规则（序列化为JSON数组）
     */
    fun getCustomRules(): Flow<List<CustomRule>> {
        return dataStore.data.map { prefs ->
            val jsonStr = prefs[KEY_CUSTOM_RULES_JSON] ?: "[]"
            runCatching { Json.decodeFromString<List<CustomRule>>(jsonStr) }.getOrElse { emptyList() }
        }
    }

    suspend fun addCustomRule(rule: CustomRule) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_CUSTOM_RULES_JSON] ?: "[]"
            val list = runCatching { Json.decodeFromString<List<CustomRule>>(current) }.getOrElse { emptyList() }
            val updated = (list + rule).distinctBy { it.id }
            prefs[KEY_CUSTOM_RULES_JSON] = Json.encodeToString(updated)
        }
    }

    suspend fun deleteCustomRule(id: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_CUSTOM_RULES_JSON] ?: "[]"
            val list = runCatching { Json.decodeFromString<List<CustomRule>>(current) }.getOrElse { emptyList() }
            val updated = list.filterNot { it.id == id }
            prefs[KEY_CUSTOM_RULES_JSON] = Json.encodeToString(updated)
        }
    }

    suspend fun updateCustomRule(rule: CustomRule) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_CUSTOM_RULES_JSON] ?: "[]"
            val list = runCatching { Json.decodeFromString<List<CustomRule>>(current) }.getOrElse { emptyList() }
            val updated = list.map { if (it.id == rule.id) rule else it }
            prefs[KEY_CUSTOM_RULES_JSON] = Json.encodeToString(updated)
        }
    }
}

