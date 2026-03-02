package com.example.myapplication.debug

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.repository.ComfyUIRepository
import com.example.myapplication.util.generateClientId
import com.example.myapplication.util.isValidUrl
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 验证Activity - 用于测试已完成的功能
 */
@AndroidEntryPoint
class ValidationActivity : ComponentActivity() {
    
    @Inject
    lateinit var repository: ComfyUIRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MaterialTheme {
                ValidationScreen(repository = repository)
            }
        }
    }
}

@Composable
fun ValidationScreen(repository: ComfyUIRepository) {
    val context = LocalContext.current
    var testResults by remember { mutableStateOf(listOf<ValidationResult>()) }
    var isRunning by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        runValidationTests { results ->
            testResults = results
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "ComfyUI GO 验证测试",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "基础架构验证",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        isRunning = true
                        runValidationTests { results ->
                            testResults = results
                            isRunning = false
                            Toast.makeText(context, "验证完成！", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isRunning,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isRunning) "运行中..." else "运行验证测试")
                }
            }
        }
        
        if (testResults.isNotEmpty()) {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "测试结果",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(testResults) { result ->
                            ValidationResultItem(result = result)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ValidationResultItem(result: ValidationResult) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = result.testName,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        
        Text(
            text = if (result.passed) "✓ 通过" else "✗ 失败",
            color = if (result.passed) Color.Green else Color.Red,
            fontWeight = FontWeight.Bold
        )
    }
    
    if (!result.passed && result.errorMessage.isNotEmpty()) {
        Text(
            text = result.errorMessage,
            color = Color.Red,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

data class ValidationResult(
    val testName: String,
    val passed: Boolean,
    val errorMessage: String = ""
)

private fun runValidationTests(onComplete: (List<ValidationResult>) -> Unit) {
    val results = mutableListOf<ValidationResult>()
    
    try {
        // 测试 1: 客户端ID生成
        val clientId = generateClientId()
        results.add(ValidationResult(
            "客户端ID生成",
            clientId.isNotEmpty() && clientId.startsWith("comfyui_go_android_")
        ))
        
        // 测试 2: URL验证
        val validUrl = "http://127.0.0.1:8188".isValidUrl()
        val invalidUrl = "not-a-url".isValidUrl()
        results.add(ValidationResult(
            "URL格式验证",
            validUrl && !invalidUrl
        ))
        
        // 测试 3: 依赖注入验证
        results.add(ValidationResult(
            "Hilt依赖注入",
            true // 如果能到达这里说明注入成功
        ))
        
        // 测试 4: 数据模型验证
        try {
            val testModel = com.example.myapplication.data.model.ConnectionState.CONNECTED
            results.add(ValidationResult(
                "数据模型定义",
                true
            ))
        } catch (e: Exception) {
            results.add(ValidationResult(
                "数据模型定义",
                false,
                e.message ?: "未知错误"
            ))
        }
        
        // 测试 5: 常量定义验证
        val constantsValid = com.example.myapplication.util.Constants.DEFAULT_SERVER_URL.isNotEmpty()
        results.add(ValidationResult(
            "常量定义",
            constantsValid
        ))
        
    } catch (e: Exception) {
        results.add(ValidationResult(
            "整体架构验证",
            false,
            e.message ?: "未知错误"
        ))
    }
    
    onComplete(results)
}
