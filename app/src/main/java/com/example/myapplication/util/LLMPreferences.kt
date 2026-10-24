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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import com.example.myapplication.data.model.CustomRule
import com.example.myapplication.data.model.TextOperationType

/**
 * 服务商：现在是单一"自定义"配置。
 * ALIYUN / SILICON_FLOW 仅保留用于旧版本数据迁移读取，不再作为可选项暴露。
 */
object LLMProvider {
    const val CUSTOM = "custom"
    const val ALIYUN = "aliyun"
    const val SILICON_FLOW = "silicon_flow"
}

/**
 * 接口协议格式
 * 现已统一为 OpenAI 兼容协议（百炼原生协议支持已移除）。
 * 保留该常量对象只为兼容既有调用点与历史数据。
 */
object LLMApiStyle {
    const val OPENAI = "openai"
}

// DataStore实例
private val Context.llmPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "llm_preferences"
)

/**
 * LLM配置管理（自定义 provider）
 */
@Singleton
class LLMPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val dataStore = context.llmPreferencesDataStore

    companion object {
        // 服务商标记（固定 custom）
        private val KEY_PROVIDER = stringPreferencesKey("llm_provider")

        // 自定义 provider 配置
        private val KEY_CUSTOM_BASE_URL = stringPreferencesKey("custom_base_url")
        private val KEY_CUSTOM_API_KEY = stringPreferencesKey("custom_api_key")
        private val KEY_CUSTOM_MODEL_NAME = stringPreferencesKey("custom_model_name")
        private val KEY_CUSTOM_API_STYLE = stringPreferencesKey("custom_api_style")

        // 旧版配置（仅用于一次性迁移读取）
        private val KEY_ALIYUN_API_KEY = stringPreferencesKey("aliyun_api_key")
        private val KEY_ALIYUN_MODEL_NAME = stringPreferencesKey("aliyun_model_name")
        private val KEY_SILICON_FLOW_API_KEY = stringPreferencesKey("silicon_flow_api_key")
        private val KEY_SILICON_FLOW_MODEL_NAME = stringPreferencesKey("silicon_flow_model_name")
        private val KEY_SILICON_FLOW_BASE_URL = stringPreferencesKey("silicon_flow_base_url")

        // 会话 ID（供要求 x-opencode-session 的端点使用，首次访问时生成并固定）
        private val KEY_SESSION_ID = stringPreferencesKey("llm_session_id")

        // 通用配置键
        private val KEY_TEMPERATURE = floatPreferencesKey("temperature")
        private val KEY_MAX_TOKENS = intPreferencesKey("max_tokens")

        // 预设管理
        private val KEY_DISABLED_PRESETS = stringSetPreferencesKey("disabled_presets")
        private val KEY_CUSTOM_RULES_JSON = stringPreferencesKey("custom_rules_json")

        // 默认值
        const val DEFAULT_TEMPERATURE = 0.7f
        const val DEFAULT_MAX_TOKENS = 2000
        const val DEFAULT_ALIYUN_BASE_URL = "https://dashscope.aliyuncs.com"
        const val DEFAULT_SILICON_FLOW_BASE_URL = "https://api.siliconflow.cn/v1"
        const val DEFAULT_ALIYUN_MODEL = "qwen-turbo"
    }

    /**
     * 保存当前选择的服务商（固定 custom）
     */
    suspend fun saveProvider(provider: String) {
        dataStore.edit { preferences ->
            preferences[KEY_PROVIDER] = LLMProvider.CUSTOM
        }
    }

    fun getProvider(): Flow<String> {
        return dataStore.data.map { LLMProvider.CUSTOM }
    }

    /**
     * 保存 API Key
     */
    suspend fun saveApiKey(apiKey: String, provider: String = LLMProvider.CUSTOM) {
        dataStore.edit { preferences ->
            preferences[KEY_CUSTOM_API_KEY] = apiKey
            preferences[KEY_PROVIDER] = LLMProvider.CUSTOM
        }
    }

    /**
     * 保存自定义 provider 完整配置
     */
    suspend fun saveCustomConfig(
        baseUrl: String,
        apiKey: String,
        modelName: String,
        apiStyle: String
    ) {
        dataStore.edit { preferences ->
            preferences[KEY_PROVIDER] = LLMProvider.CUSTOM
            preferences[KEY_CUSTOM_BASE_URL] = baseUrl.trim()
            preferences[KEY_CUSTOM_API_KEY] = apiKey
            preferences[KEY_CUSTOM_MODEL_NAME] = modelName.trim()
            // 百炼协议已移除，历史值统一归一到 OpenAI 兼容
            preferences[KEY_CUSTOM_API_STYLE] = LLMApiStyle.OPENAI
        }
    }

    /**
     * 保存模型配置（保留旧签名以兼容既有调用）
     */
    suspend fun saveModelConfig(
        modelName: String,
        temperature: Float,
        maxTokens: Int,
        provider: String = LLMProvider.CUSTOM
    ) {
        dataStore.edit { preferences ->
            preferences[KEY_CUSTOM_MODEL_NAME] = modelName.trim()
            preferences[KEY_TEMPERATURE] = temperature
            preferences[KEY_MAX_TOKENS] = maxTokens
            preferences[KEY_PROVIDER] = LLMProvider.CUSTOM
        }
    }

    /**
     * 获取稳定的会话 ID。
     *
     * OpenCode Go 一类端点要求每个对话带 x-opencode-session，缺失会返回
     * 400 MissingSessionID。这里生成一次并持久化，保证同一设备后续请求
     * 落在同一会话上；清空配置时会一并重置。
     */
    suspend fun getOrCreateSessionId(): String {
        val existing = dataStore.data.first()[KEY_SESSION_ID]
        if (!existing.isNullOrBlank()) return existing
        val generated = java.util.UUID.randomUUID().toString()
        dataStore.edit { prefs ->
            if (prefs[KEY_SESSION_ID].isNullOrBlank()) {
                prefs[KEY_SESSION_ID] = generated
            }
        }
        // 并发首启时以实际写入值为准
        return dataStore.data.first()[KEY_SESSION_ID] ?: generated
    }

    /**
     * 是否有 API Key
     */
    fun hasApiKey(provider: String = LLMProvider.CUSTOM): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            val key = preferences[KEY_CUSTOM_API_KEY]
                ?: preferences[KEY_ALIYUN_API_KEY]
                ?: preferences[KEY_SILICON_FLOW_API_KEY]
                ?: ""
            key.isNotBlank()
        }
    }

    /**
     * 清除所有LLM相关的配置
     */
    suspend fun clearLLMConfig() {
        dataStore.edit { preferences ->
            preferences.remove(KEY_PROVIDER)
            preferences.remove(KEY_CUSTOM_BASE_URL)
            preferences.remove(KEY_CUSTOM_API_KEY)
            preferences.remove(KEY_CUSTOM_MODEL_NAME)
            preferences.remove(KEY_CUSTOM_API_STYLE)
            preferences.remove(KEY_ALIYUN_API_KEY)
            preferences.remove(KEY_ALIYUN_MODEL_NAME)
            preferences.remove(KEY_SILICON_FLOW_API_KEY)
            preferences.remove(KEY_SILICON_FLOW_MODEL_NAME)
            preferences.remove(KEY_SILICON_FLOW_BASE_URL)
            preferences.remove(KEY_TEMPERATURE)
            preferences.remove(KEY_MAX_TOKENS)
            preferences.remove(KEY_SESSION_ID)
        }
    }

    /**
     * 完整配置
     */
    data class LLMConfig(
        val provider: String,
        val apiKey: String,
        val modelName: String,
        val temperature: Float,
        val maxTokens: Int,
        val baseUrl: String,
        val apiStyle: String = LLMApiStyle.OPENAI
    )

    /**
     * 获取完整配置的Flow
     *
     * 迁移策略：若新键为空，则回落到旧版阿里/硅基的数据，
     * 使升级后的用户不必重新填写即可继续使用。
     */
    fun getConfig(): Flow<LLMConfig> {
        return dataStore.data.map { preferences ->
            val legacyProvider = preferences[KEY_PROVIDER] ?: LLMProvider.ALIYUN

            val customKey = preferences[KEY_CUSTOM_API_KEY] ?: ""
            val customBase = preferences[KEY_CUSTOM_BASE_URL] ?: ""
            val customModel = preferences[KEY_CUSTOM_MODEL_NAME] ?: ""
            val customStyle = preferences[KEY_CUSTOM_API_STYLE] ?: ""

            val legacyKey = when (legacyProvider) {
                LLMProvider.SILICON_FLOW -> preferences[KEY_SILICON_FLOW_API_KEY] ?: ""
                else -> preferences[KEY_ALIYUN_API_KEY] ?: ""
            }
            val legacyModel = when (legacyProvider) {
                LLMProvider.SILICON_FLOW ->
                    preferences[KEY_SILICON_FLOW_MODEL_NAME] ?: ""
                else -> preferences[KEY_ALIYUN_MODEL_NAME] ?: LLMPreferences.DEFAULT_ALIYUN_MODEL
            }
            val legacyBase = when (legacyProvider) {
                LLMProvider.SILICON_FLOW ->
                    preferences[KEY_SILICON_FLOW_BASE_URL] ?: LLMPreferences.DEFAULT_SILICON_FLOW_BASE_URL
                else -> LLMPreferences.DEFAULT_ALIYUN_BASE_URL
            }

            val apiKey = customKey.ifBlank { legacyKey }
            val baseUrl = customBase.ifBlank { legacyBase }
            val modelName = customModel.ifBlank { legacyModel }
            // 百炼协议已移除：无论历史存的是什么，一律按 OpenAI 兼容处理
            val apiStyle = LLMApiStyle.OPENAI

            LLMConfig(
                provider = LLMProvider.CUSTOM,
                apiKey = apiKey,
                modelName = modelName,
                temperature = preferences[KEY_TEMPERATURE] ?: DEFAULT_TEMPERATURE,
                maxTokens = preferences[KEY_MAX_TOKENS] ?: DEFAULT_MAX_TOKENS,
                baseUrl = baseUrl,
                apiStyle = apiStyle
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
