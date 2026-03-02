# ComfyUI GO - 视频功能改进总结

## 📝 改进概述

为 ComfyUI GO Android 应用添加了完整的**视频上传和播放**功能，使其能够处理视频工作流（如 AnimateDiff、SVD 等）。

---

## ✅ 已完成的改进

### 1. **数据模型扩展**
- ✅ 添加 `VIDEO` 参数类型到 `ParameterType` 枚举
- ✅ 创建 `GeneratedVideo` 数据类（包含文件名、时长、格式等）
- ✅ 添加 `VideoConfig` 参数配置类
- ✅ 在 `ExecutionRecord` 中添加 `localVideoPaths` 字段

**文件**: `app/src/main/java/com/example/myapplication/data/model/ComfyUIModels.kt`  
**文件**: `app/src/main/java/com/example/myapplication/data/model/ExecutionRecord.kt`

### 2. **视频工具类**
- ✅ 创建 `VideoUtils` 工具类，提供：
  - 视频文件读取（`uriToByteArray`）
  - 获取文件名、大小、时长
  - 文件大小格式化
  - 视频格式识别
  - 大文件检测（100MB 阈值）

**文件**: `app/src/main/java/com/example/myapplication/util/VideoUtils.kt`

### 3. **网络层增强**
- ✅ 在 `ComfyUIRepository` 接口中添加 `uploadVideo()` 方法
- ✅ 在 `ComfyUIRepositoryImpl` 中实现带进度回调的流式上传
- ✅ 在 `ComfyApiService` 中添加支持 `subfolder` 参数的 `uploadImage` 重载方法

**原理**: 复用 ComfyUI 的 `POST /upload/image` 端点（它支持任意文件上传到 input 目录）

**文件**:
- `app/src/main/java/com/example/myapplication/data/repository/ComfyUIRepository.kt`
- `app/src/main/java/com/example/myapplication/data/repository/ComfyUIRepositoryImpl.kt`
- `app/src/main/java/com/example/myapplication/data/api/ComfyApiService.kt`

### 4. **智能参数识别**
- ✅ 在 `WorkflowParser` 中添加 `isVideoUploadParameter()` 函数
- ✅ 支持多种识别规则：
  - **强信号**: API 节点中的 `video_upload` 标记
  - **强信号**: 已知节点（如 `LoadVideo`）
  - **强信号**: 类型关键字（`VIDEO`, `VHS_VIDEO`, `VIDEOINFO` 等）
  - **中信号**: UI 元数据中的 `video_upload: true`
  - **辅助信号**: 参数名匹配（`video`, `input_video` 等）

**文件**: `app/src/main/java/com/example/myapplication/domain/parser/WorkflowParser.kt`

### 5. **UI 组件**

#### 视频参数输入组件
- ✅ 创建 `VideoParameterInput` 组件
- ✅ 支持视频选择（系统文件选择器，MIME: `video/*`）
- ✅ 显示上传进度条
- ✅ 文件信息展示（文件名、上传进度）
- ✅ 重新选择/移除功能

**文件**: `app/src/main/java/com/example/myapplication/presentation/ui/components/parameter/VideoParameterInput.kt`

#### 视频播放器组件
- ✅ 基于 **Media3 ExoPlayer** 实现
- ✅ `VideoPlayer`: 基础播放器组件
- ✅ `VideoPlayerDialog`: 全屏播放对话框
- ✅ `VideoThumbnailCard`: 视频缩略图卡片（点击播放）
- ✅ 支持自动播放、控制条显示等配置

**文件**: `app/src/main/java/com/example/myapplication/presentation/ui/components/VideoPlayer.kt`

#### 参数编辑界面集成
- ✅ 在 `WorkflowParameterCard` 中添加 `UPLOAD_VIDEO` 分支
- ✅ 更新 `needsExternalTitle` 判断逻辑

**文件**: `app/src/main/java/com/example/myapplication/presentation/ui/components/WorkflowParameterCard.kt`

### 6. **依赖管理**
- ✅ 添加 Media3 ExoPlayer 依赖（v1.2.1）:
  - `androidx.media3:media3-exoplayer`
  - `androidx.media3:media3-ui`
  - `androidx.media3:media3-common`

**文件**: `app/build.gradle.kts`

---

## 🔧 工作原理

### 视频上传流程
```
1. 用户在参数编辑界面选择视频文件
   ↓
2. VideoUtils 读取文件元数据（文件名、大小、时长）
   ↓
3. Repository.uploadVideo() 调用 POST /upload/image
   - 使用流式上传（8KB 缓冲区）
   - 实时回调上传进度
   ↓
4. 服务器返回 {name, subfolder, type}
   ↓
5. 将返回值写入工作流 JSON 的对应参数
   ↓
6. 提交工作流执行
```

### 视频结果获取流程
```
1. 工作流执行完成（如 VHS_WriteMP4）
   ↓
2. 服务器将视频保存到 output 目录
   ↓
3. 客户端通过 GET /view?filename=xxx.mp4&type=output&subfolder=...
   ↓
4. 使用 VideoPlayer 组件播放
   或使用外部播放器（Intent.ACTION_VIEW）
```

---

## 🎯 支持的节点类型

### 官方节点
- `LoadVideo` - 加载视频
- `SaveVideo` - 保存视频

### VHS (Video Helper Suite) 扩展
- `VHS_LoadVideo` - 加载视频
- `VHS_VideoCombine` - 合并视频帧
- `VHS_SaveVideo` - 保存视频
- `VHS_WriteMP4` - 写入 MP4
- `VHS_WriteWEBM` - 写入 WEBM
- `VHS_WriteGIF` - 写入 GIF

### 其他自定义节点
- 任何使用 `video_upload` 标记的 API 节点
- 任何类型包含 `VIDEO` 关键字的参数

---

## 📱 用户体验

### 选择视频
1. 在参数编辑界面识别到视频参数
2. 点击"选择视频文件"按钮
3. 系统文件选择器打开（过滤视频文件）
4. 选择后自动触发上传

### 上传进度
- 显示文件名
- 实时进度条（0-100%）
- 上传中禁用操作按钮

### 查看结果
- 执行历史中显示视频结果
- 点击缩略图卡片
- 全屏播放器打开
- 支持播放控制（播放/暂停、进度条、音量等）

---

## 🚀 未来可扩展功能

### MVP 版本（当前）
- ✅ 视频选择和上传
- ✅ 上传进度显示
- ✅ 内置播放器

### 增强版本（可选）
- ⏳ 视频缩略图生成（使用 MediaMetadataRetriever 提取第一帧）
- ⏳ 上传任务队列（支持批量上传）
- ⏳ 断点续传（大文件上传失败自动重试）
- ⏳ Wi-Fi 限制（仅在 Wi-Fi 下上传大文件）
- ⏳ 视频压缩（上传前自动压缩）
- ⏳ 历史记录中的视频自动下载和本地缓存

---

## ⚙️ 服务器端要求

### ComfyUI 配置
1. **安装视频节点**（推荐）:
   ```bash
   cd ComfyUI/custom_nodes
   git clone https://github.com/Kosinkadink/ComfyUI-VideoHelperSuite
   ```

2. **安装 ffmpeg**（视频编解码必需）:
   - **Windows**: 从 [ffmpeg.org](https://ffmpeg.org) 下载并添加到 PATH
   - **Linux**: `sudo apt install ffmpeg`
   - **macOS**: `brew install ffmpeg`

3. **调整上传大小限制**（可选，用于大视频文件）:
   ```bash
   python main.py --max-upload-size 2048  # 单位：MB
   ```

4. **设置 CORS**（如果从手机访问）:
   ```bash
   python main.py --enable-cors-header "*"
   ```

---

## 📂 修改的文件清单

### 新增文件 (3)
1. `app/src/main/java/com/example/myapplication/util/VideoUtils.kt`
2. `app/src/main/java/com/example/myapplication/presentation/ui/components/parameter/VideoParameterInput.kt`
3. `app/src/main/java/com/example/myapplication/presentation/ui/components/VideoPlayer.kt`

### 修改文件 (7)
1. `app/build.gradle.kts` - 添加 Media3 依赖
2. `app/src/main/java/com/example/myapplication/data/model/ComfyUIModels.kt` - 扩展数据模型
3. `app/src/main/java/com/example/myapplication/data/model/ExecutionRecord.kt` - 添加视频路径字段
4. `app/src/main/java/com/example/myapplication/data/repository/ComfyUIRepository.kt` - 添加接口
5. `app/src/main/java/com/example/myapplication/data/repository/ComfyUIRepositoryImpl.kt` - 实现上传
6. `app/src/main/java/com/example/myapplication/data/api/ComfyApiService.kt` - 添加 API 重载
7. `app/src/main/java/com/example/myapplication/domain/parser/WorkflowParser.kt` - 智能识别
8. `app/src/main/java/com/example/myapplication/presentation/ui/components/WorkflowParameterCard.kt` - UI 集成

---

## 🧪 测试建议

### 单元测试
- VideoUtils 的文件大小计算
- VideoUtils 的格式识别
- WorkflowParser 的视频参数识别

### 集成测试
1. 选择小视频文件（< 10MB）测试上传
2. 选择大视频文件测试进度回调
3. 测试不同格式（MP4、WEBM、MOV）
4. 测试网络中断后的错误处理
5. 测试播放器的播放/暂停/进度

### UI 测试
- 参数编辑界面正确显示视频选择按钮
- 上传进度条正确更新
- 全屏播放器正常打开和关闭

---

## 📞 技术支持

如需进一步扩展功能或遇到问题，可参考：
- [ComfyUI 官方文档](https://github.com/comfyanonymous/ComfyUI)
- [Media3 ExoPlayer 文档](https://developer.android.com/guide/topics/media/media3)
- [VideoHelperSuite 扩展](https://github.com/Kosinkadink/ComfyUI-VideoHelperSuite)

---

**改进完成日期**: 2025年10月14日  
**版本**: 1.1.0 (添加视频功能)

