package com.example.myapplication.util

import com.example.myapplication.data.model.TextOperation
import com.example.myapplication.data.model.TextOperationType

/**
 * 预设的提示词模板
 */
object PromptTemplates {
    
    /**
     * 中译英
     */
    val TRANSLATE_ZH_TO_EN = TextOperation(
        type = TextOperationType.TRANSLATE_ZH_TO_EN,
        displayName = "中译英",
        icon = "🌐",
        systemPrompt = """你是专业的AI绘画提示词翻译助手。
你的任务是将中文提示词翻译成准确、地道的英文提示词。
翻译要求：
1. 保持提示词的结构和格式
2. 使用Stable Diffusion常用的英文词汇
3. 不要添加额外的解释，只返回翻译结果
4. 如果有质量标签（如"杰作"、"最佳质量"），翻译为"masterpiece"、"best quality"等标准词汇""",
        userPromptTemplate = "请将以下中文提示词翻译成英文：\n\n{text}",
        description = "将中文提示词翻译成英文"
    )
    
    /**
     * 英译中
     */
    val TRANSLATE_EN_TO_ZH = TextOperation(
        type = TextOperationType.TRANSLATE_EN_TO_ZH,
        displayName = "英译中",
        icon = "🌏",
        systemPrompt = """你是专业的AI绘画提示词翻译助手。
你的任务是将英文提示词翻译成准确、通顺的中文。
翻译要求：
1. 保持提示词的结构和格式
2. 使用中文的自然表达方式
3. 不要添加额外的解释，只返回翻译结果
4. 如果有质量标签（如"masterpiece"、"best quality"），翻译为"杰作"、"最佳质量"等""",
        userPromptTemplate = "请将以下英文提示词翻译成中文：\n\n{text}",
        description = "将英文提示词翻译成中文"
    )
    
    /**
     * 获取所有预设操作
     */
    fun getAllOperations(): List<TextOperation> {
        return listOf(
            TRANSLATE_ZH_TO_EN,
            TRANSLATE_EN_TO_ZH
        )
    }
    
    /**
     * 根据类型获取操作
     */
    fun getOperationByType(type: TextOperationType): TextOperation? {
        return getAllOperations().find { it.type == type }
    }
    
    /**
     * 替换模板中的占位符
     */
    fun fillTemplate(template: String, text: String): String {
        return template.replace("{text}", text)
    }
}

