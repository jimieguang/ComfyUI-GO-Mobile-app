package com.example.myapplication.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.myapplication.R

/**
 * ComfyUI GO 应用的导航目标
 * 对应Web版的三个主要页面
 */
enum class ComfyUIDestination(
    val route: String,
    val icon: ImageVector,
    @StringRes val titleRes: Int
) {
    /**
     * 工作流执行页面 - 对应Web版的主页面
     * 包含服务器连接、工作流导入、参数设置、执行功能
     */
    WORKFLOW_GENERATOR(
        route = "workflow_generator",
        icon = Icons.Filled.Send,  // 纸飞机图标（类似Telegram）
        titleRes = R.string.title_home
    ),
    
    /**
     * 执行历史页面 - 对应Web版的执行历史
     * 显示过往执行记录、结果图片、重新执行等功能
     */
    EXECUTION_HISTORY(
        route = "execution_history", 
        // 圆圈历史记录图标
        icon = Icons.Filled.History,
        titleRes = R.string.title_dashboard
    ),
    
    /**
     * 工作流存储页面 - 对应Web版的工作流存储
     * 管理保存的工作流、导入导出、分类管理等功能
     */
    WORKFLOW_STORAGE(
        route = "workflow_storage",
        // 储存图标
        icon = Icons.Filled.Save, 
        titleRes = R.string.title_notifications
    ),
    
    /**
     * 参数编辑页面 - 专门的参数编辑界面
     * 不在底部导航中显示，通过按钮跳转
     */
    PARAMETER_EDIT(
        route = "parameter_edit",
        icon = Icons.Filled.Build, // 不会在导航中使用
        titleRes = R.string.title_home // 不会在导航中使用
    ),
    
    /**
     * 设置页面 - 统一设置界面
     * 不在底部导航中显示，通过设置按钮跳转
     */
    SETTINGS(
        route = "settings",
        icon = Icons.Filled.Build, // 不会在导航中使用
        titleRes = R.string.title_home // 不会在导航中使用
    ),
    
    /**
     * 历史图片瀑布流页面 - 独立视图，不在底部导航中展示
     */
    WATERFALL_GALLERY(
        route = "execution_history_waterfall",
        icon = Icons.Filled.History,
        titleRes = R.string.title_notifications
    );
    
    companion object {
        /**
         * 获取所有底部导航页面
         */
        fun getBottomNavDestinations() = listOf(
            WORKFLOW_GENERATOR,
            EXECUTION_HISTORY, 
            WORKFLOW_STORAGE
        )
    }
}
