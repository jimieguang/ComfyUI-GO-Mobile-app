package com.example.myapplication.presentation.ui.main

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import com.example.myapplication.presentation.navigation.ComfyUIDestination
import com.example.myapplication.presentation.ui.screens.ExecutionHistoryScreen
import com.example.myapplication.presentation.ui.screens.ParameterEditScreen
import com.example.myapplication.presentation.ui.screens.SettingsScreen
import com.example.myapplication.presentation.ui.screens.WorkflowGeneratorScreen
import com.example.myapplication.presentation.ui.screens.WorkflowStorageScreen
import com.example.myapplication.presentation.viewmodel.WorkflowParserViewModel
import com.example.myapplication.presentation.viewmodel.SavedWorkflowsViewModel
import com.example.myapplication.data.model.SavedWorkflow

/**
 * 获取目标页面的索引位置（用于判断动画方向）
 */
private fun getDestinationIndex(route: String?): Int {
    return when (route) {
        ComfyUIDestination.WORKFLOW_GENERATOR.route -> 0
        ComfyUIDestination.EXECUTION_HISTORY.route -> 1
        ComfyUIDestination.WORKFLOW_STORAGE.route -> 2
        else -> -1
    }
}

/**
 * ComfyUI GO 主界面
 * 包含底部导航栏和页面内容区域
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    // 在MainScreen级别创建共享的ViewModel实例
    val sharedParserViewModel: WorkflowParserViewModel = hiltViewModel()
    val sharedImportViewModel: com.example.myapplication.presentation.viewmodel.WorkflowImportViewModel = hiltViewModel()
    val sharedServerViewModel: com.example.myapplication.presentation.viewmodel.ServerConnectionViewModel = hiltViewModel()
    val sharedLLMViewModel: com.example.myapplication.presentation.viewmodel.LLMAssistantViewModel = hiltViewModel()
    
    // 获取遮罩编辑器状态
    val parserUiState by sharedParserViewModel.uiState.collectAsStateWithLifecycle()
    
    // 判断当前是否在参数编辑页面、设置页面或遮罩编辑器显示时（不显示底部导航栏）
    val isParameterEditScreen = currentDestination?.route == ComfyUIDestination.PARAMETER_EDIT.route
    val isSettingsScreen = currentDestination?.route == ComfyUIDestination.SETTINGS.route
    val shouldHideBottomBar = isParameterEditScreen || isSettingsScreen || parserUiState.showMaskEditor
    
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // 只在非参数编辑页面、非设置页面且遮罩编辑器未显示时显示底部导航栏
            if (!shouldHideBottomBar) {
                NavigationBar(
                    modifier = Modifier.height(64.dp),
                    windowInsets = WindowInsets(0, 0, 0, 0)
                ) {
                ComfyUIDestination.getBottomNavDestinations().forEach { destination ->
                    NavigationBarItem(
                        icon = { 
                            // 对于工作流执行页面，使用自定义Telegram图标
                            if (destination.route == ComfyUIDestination.WORKFLOW_GENERATOR.route) {
                                Icon(
                                    painter = painterResource(id = com.example.myapplication.R.drawable.ic_telegram),
                                    contentDescription = stringResource(destination.titleRes)
                                )
                            } else {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = stringResource(destination.titleRes)
                                )
                            }
                        },
                        label = { 
                            Text(stringResource(destination.titleRes)) 
                        },
                        selected = currentDestination?.hierarchy?.any { 
                            it.route == destination.route 
                        } == true,
                        onClick = {
                            navController.navigate(destination.route) {
                                // Pop up to the start destination of the graph to
                                // avoid building up a large stack of destinations
                                // on the back stack as users select items
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                // Avoid multiple copies of the same destination when
                                // reselecting the same item
                                launchSingleTop = true
                                // Restore state when reselecting a previously selected item
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = androidx.compose.ui.graphics.Color(0xFF5AA9E6).copy(alpha = 0.15f),  // 品牌蓝色系选中效果
                            selectedIconColor = androidx.compose.ui.graphics.Color(0xFF5AA9E6),  // 选中图标使用品牌蓝色
                            selectedTextColor = androidx.compose.ui.graphics.Color(0xFF5AA9E6)  // 选中文字使用品牌蓝色
                        )
                    )
                }
                }
            }
        }
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        val contentPadding = PaddingValues(
            start = innerPadding.calculateLeftPadding(layoutDirection),
            top = innerPadding.calculateTopPadding(),
            end = innerPadding.calculateRightPadding(layoutDirection),
            bottom = 0.dp
        )
        NavHost(
            navController = navController,
            startDestination = ComfyUIDestination.WORKFLOW_GENERATOR.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            // 工作流生成器页面
            composable(
                route = ComfyUIDestination.WORKFLOW_GENERATOR.route,
                enterTransition = {
                    // 从参数编辑或设置页面返回时无动画（作为背景保持不动）
                    if (initialState.destination.route == ComfyUIDestination.PARAMETER_EDIT.route ||
                        initialState.destination.route == ComfyUIDestination.SETTINGS.route) {
                        EnterTransition.None
                    } else {
                        val targetIndex = getDestinationIndex(targetState.destination.route)
                        val initialIndex = getDestinationIndex(initialState.destination.route)
                        if (targetIndex < initialIndex) {
                            slideInHorizontally(animationSpec = tween(300)) { -it }
                        } else {
                            slideInHorizontally(animationSpec = tween(300)) { it }
                        }
                    }
                },
                exitTransition = {
                    // 跳转到参数编辑或设置页面时无动画（作为背景保持不动）
                    if (targetState.destination.route == ComfyUIDestination.PARAMETER_EDIT.route ||
                        targetState.destination.route == ComfyUIDestination.SETTINGS.route) {
                        ExitTransition.None
                    } else {
                        val targetIndex = getDestinationIndex(targetState.destination.route)
                        val initialIndex = getDestinationIndex(initialState.destination.route)
                        if (targetIndex > initialIndex) {
                            slideOutHorizontally(animationSpec = tween(300)) { -it }
                        } else {
                            slideOutHorizontally(animationSpec = tween(300)) { it }
                        }
                    }
                }
            ) {
                WorkflowGeneratorScreen(
                    onNavigateToParameterEdit = {
                        navController.navigate(ComfyUIDestination.PARAMETER_EDIT.route)
                    },
                    onNavigateToSettings = {
                        navController.navigate(ComfyUIDestination.SETTINGS.route)
                    },
                    serverViewModel = sharedServerViewModel,
                    importViewModel = sharedImportViewModel,
                    parserViewModel = sharedParserViewModel
                )
            }
            
            // 参数编辑页面（从底部滑入/滑出）
            composable(
                route = ComfyUIDestination.PARAMETER_EDIT.route,
                enterTransition = {
                    slideInVertically(
                        initialOffsetY = { it },  // 从底部开始（it = 屏幕高度）
                        animationSpec = tween(300)
                    )
                },
                exitTransition = {
                    slideOutVertically(
                        targetOffsetY = { it },  // 滑到底部（it = 屏幕高度）
                        animationSpec = tween(300)
                    )
                }
            ) {
                ParameterEditScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    parserViewModel = sharedParserViewModel
                )
            }
            
            // 设置页面（从底部滑入/滑出）
            composable(
                route = ComfyUIDestination.SETTINGS.route,
                enterTransition = {
                    slideInVertically(
                        initialOffsetY = { it },  // 从底部开始（it = 屏幕高度）
                        animationSpec = tween(300)
                    )
                },
                exitTransition = {
                    slideOutVertically(
                        targetOffsetY = { it },  // 滑到底部（it = 屏幕高度）
                        animationSpec = tween(300)
                    )
                }
            ) {
                SettingsScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    serverViewModel = sharedServerViewModel,
                    llmViewModel = sharedLLMViewModel
                )
            }
            
            // 执行历史页面（包含卡片视图和瀑布流视图）
            composable(
                route = ComfyUIDestination.EXECUTION_HISTORY.route,
                enterTransition = {
                    val targetIndex = getDestinationIndex(targetState.destination.route)
                    val initialIndex = getDestinationIndex(initialState.destination.route)
                    if (targetIndex < initialIndex) {
                        slideInHorizontally(animationSpec = tween(300)) { -it }
                    } else {
                        slideInHorizontally(animationSpec = tween(300)) { it }
                    }
                },
                exitTransition = {
                    val targetIndex = getDestinationIndex(targetState.destination.route)
                    val initialIndex = getDestinationIndex(initialState.destination.route)
                    if (targetIndex > initialIndex) {
                        slideOutHorizontally(animationSpec = tween(300)) { -it }
                    } else {
                        slideOutHorizontally(animationSpec = tween(300)) { it }
                    }
                }
            ) {
                val importUi by sharedImportViewModel.uiState.collectAsState()
                 ExecutionHistoryScreen(
                    viewModel = androidx.hilt.navigation.compose.hiltViewModel(),
                     onUseWorkflow = { workflowJson, name, quickParams ->
                        // 将工作流设置到导入VM，并跳转至生成器页
                        // 先写入名称与来源，再导入，避免被后续覆盖
                        sharedImportViewModel.applyWorkflowNameFromHistory(name)
                        sharedImportViewModel.updateJsonInputText(workflowJson.toString())
                        // ✅ 先恢复快速参数配置（在导入之前）
                        sharedParserViewModel.restoreQuickParams(quickParams)
                        sharedImportViewModel.importFromCurrentText()
                        navController.navigate(ComfyUIDestination.WORKFLOW_GENERATOR.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            
            // 工作流存储页面
            composable(
                route = ComfyUIDestination.WORKFLOW_STORAGE.route,
                enterTransition = {
                    val targetIndex = getDestinationIndex(targetState.destination.route)
                    val initialIndex = getDestinationIndex(initialState.destination.route)
                    if (targetIndex < initialIndex) {
                        slideInHorizontally(animationSpec = tween(300)) { -it }
                    } else {
                        slideInHorizontally(animationSpec = tween(300)) { it }
                    }
                },
                exitTransition = {
                    val targetIndex = getDestinationIndex(targetState.destination.route)
                    val initialIndex = getDestinationIndex(initialState.destination.route)
                    if (targetIndex > initialIndex) {
                        slideOutHorizontally(animationSpec = tween(300)) { -it }
                    } else {
                        slideOutHorizontally(animationSpec = tween(300)) { it }
                    }
                }
            ) {
                val savedVm: SavedWorkflowsViewModel = hiltViewModel()
                WorkflowStorageScreen(
                    viewModel = savedVm,
                    onUseWorkflow = { item: SavedWorkflow ->
                        // 将保存的工作流应用到导入VM，并跳转生成器
                        // 先写入名称与来源，再导入，避免被后续覆盖
                        sharedImportViewModel.applyWorkflowNameFromStorage(item.name)
                        sharedImportViewModel.updateJsonInputText(item.json.toString())
                        // ✅ 先恢复快速参数配置（在导入之前）
                        sharedParserViewModel.restoreQuickParams(item.quickParams)
                        sharedImportViewModel.importFromCurrentText()
                        navController.navigate(ComfyUIDestination.WORKFLOW_GENERATOR.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onImportFromFile = { uri -> savedVm.importFromFileAndSave(uri) },
                    onImportFromClipboard = { savedVm.importFromClipboardAndSave() }
                )
            }
        }
    }
}
