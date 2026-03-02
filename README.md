# ComfyUI GO - Android Client

A professional Android client application for ComfyUI, enabling mobile control and management of AI image/video generation workflows.

## Overview

ComfyUI GO is a native Android application that provides a complete mobile interface for ComfyUI servers. Built with modern Android development practices, it offers real-time workflow execution, parameter editing, and comprehensive media management capabilities.

## Key Features

### Workflow Management
- **JSON Import**: Import ComfyUI workflow files from local storage or clipboard
- **Intelligent Parsing**: Automatic detection and extraction of workflow parameters
- **Parameter Editor**: Visual interface for modifying workflow parameters before execution
- **Workflow Storage**: Save frequently used workflows locally for quick access
- **Execution History**: Track all workflow executions with results and parameters
![微信图片_20260302214314_533_34](https://github.com/user-attachments/assets/f89db756-62b0-4d85-905d-14ab4ca343b2)
![微信图片_20260302214324_545_34](https://github.com/user-attachments/assets/546b27a7-0451-4426-96c7-cae6b640dad6)
![微信图片_20260302214323_544_34](https://github.com/user-attachments/assets/e9c4d7c2-53ae-4ad4-845a-25eee0c7edf8)
![微信图片_20260302214322_543_34](https://github.com/user-attachments/assets/cb1d650a-3994-4c38-abcd-6cf136600c30)
![微信图片_20260302214321_542_34](https://github.com/user-attachments/assets/3e175e27-9b0a-4277-9b8d-ced443b96b99)
![微信图片_20260302214321_541_34](https://github.com/user-attachments/assets/8d31d43b-2b27-4142-a7e7-250aff305f73)
![微信图片_20260302214320_540_34](https://github.com/user-attachments/assets/319de277-cf48-4939-b7a2-91759230e05e)
![微信图片_20260302214319_539_34](https://github.com/user-attachments/assets/9d0e1843-dd81-44a4-a4b1-d55944063bf3)
![微信图片_20260302214318_538_34](https://github.com/user-attachments/assets/d48ebae8-da01-477a-b4e2-912bab5bb91d)
![微信图片_20260302214317_537_34](https://github.com/user-attachments/assets/57723eb9-7594-4be0-bddb-2383d8f1341a)
![微信图片_20260302214316_536_34](https://github.com/user-attachments/assets/7c8bdb8a-9df5-44af-b7aa-73970cfca04a)
![微信图片_20260302214316_535_34](https://github.com/user-attachments/assets/9bd60d08-bd7d-4417-b74c-ff41ff218871)
![微信图片_20260302214315_534_34](https://github.com/user-attachments/assets/1ae883be-ae80-41a5-8aec-00b7f222ca51)

### Media Support
- **Image Processing**: Upload, preview, and download generated images
- **Video Generation**: Full support for video workflows (AnimateDiff, SVD, etc.)
- **Audio Processing**: Audio file upload and playback capabilities
- **Mask Editor**: Built-in image mask editor for inpainting workflows
- **Multi-format Support**: PNG, JPEG, WebP, MP4, WebM, MP3, WAV

### Real-time Monitoring
- **WebSocket Integration**: Live progress updates during workflow execution
- **Preview Frames**: Real-time preview of generation progress
- **System Statistics**: Monitor server CPU, GPU, and memory usage
- **Notification Service**: Background notifications for long-running tasks

### LLM Integration
- **AI Assistant**: Generate workflows using natural language descriptions
- **Multi-provider Support**: Compatible with Alibaba Cloud Qwen and SiliconFlow
- **Custom Prompts**: Configurable prompt templates for workflow generation

### Advanced Capabilities
- **Dynamic Parameter Recognition**: Automatic identification of parameter types (text, number, image, video, audio)
- **Server Resource Discovery**: Fetch available models, samplers, and schedulers from server
- **Network State Management**: Automatic reconnection and error handling
- **Material Design 3**: Modern UI with dark mode support

## Technical Architecture

### Architecture Pattern
- **MVVM (Model-View-ViewModel)**: Clear separation of concerns
- **Clean Architecture**: Domain, data, and presentation layers
- **Repository Pattern**: Abstracted data access layer

### Technology Stack
- **Language**: Kotlin 1.9+
- **UI Framework**: Jetpack Compose with Material 3
- **Dependency Injection**: Hilt (Dagger)
- **Networking**: Retrofit + OkHttp + WebSocket
- **Database**: Room
- **Asynchronous**: Kotlin Coroutines + Flow
- **Serialization**: kotlinx.serialization
- **Media Playback**: Media3 ExoPlayer
- **Image Loading**: Coil

### Project Structure
```
app/src/main/java/com/example/myapplication/
├── data/                    # Data layer
│   ├── api/                # API service interfaces
│   ├── model/              # Data models
│   ├── repository/         # Repository implementations
│   └── websocket/          # WebSocket manager
├── domain/                  # Business logic layer
│   ├── execution/          # Workflow execution logic
│   ├── parser/             # Workflow parser
│   └── usecase/            # Use cases
├── presentation/            # UI layer
│   ├── ui/                 # Compose UI components
│   ├── viewmodel/          # ViewModels
│   └── navigation/         # Navigation configuration
└── util/                    # Utility classes
```

## System Requirements

### Minimum Requirements
- Android 9.0 (API Level 28) or higher
- 2GB RAM
- 100MB free storage

### Server Requirements
- ComfyUI server with API access
- Network connectivity (local or remote)
- CORS enabled for remote access

## Installation

### From Release
1. Download the latest APK from [Releases](https://github.com/huafitwjb/ComfyUI-GO-Mobile-app/releases)
2. Enable "Install from Unknown Sources" in Android settings
3. Install the APK file

### Build from Source
```bash
# Clone repository
git clone https://github.com/huafitwjb/ComfyUI-GO-Mobile-app.git
cd ComfyUI-GO-Mobile-app

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease
```

## Configuration

### Server Connection
The application supports any HTTP/HTTPS ComfyUI server:

- **Local Server**: `http://127.0.0.1:8188`
- **LAN Server**: `http://192.168.x.x:8188`
- **Remote Server**: `http://your-domain.com:8188`
- **HTTPS**: `https://your-domain.com:8188`

### Server Setup
For remote access, configure your ComfyUI server:

```bash
# Enable CORS and external access
python main.py --listen 0.0.0.0 --enable-cors-header "*"

# Increase upload size limit for large files
python main.py --max-upload-size 2048
```

### LLM Configuration
1. Navigate to Settings
2. Select LLM provider (Alibaba Cloud or SiliconFlow)
3. Enter API key and model name
4. Configure temperature and max tokens

## Usage

### Basic Workflow
1. **Connect to Server**: Enter ComfyUI server URL and connect
2. **Import Workflow**: Upload workflow JSON file or paste from clipboard
3. **Edit Parameters**: Modify parameters in the visual editor
4. **Execute**: Submit workflow for execution
5. **Monitor Progress**: View real-time progress and preview frames
6. **View Results**: Access generated media in execution history

### Advanced Features
- **Quick Parameters**: Mark frequently used parameters for quick access
- **Batch Execution**: Save workflows with different parameter sets
- **Media Gallery**: Browse all generated images and videos
- **Workflow Templates**: Create reusable workflow templates

## API Compatibility

### Supported ComfyUI Endpoints
- `GET /object_info` - Node and parameter information
- `POST /prompt` - Workflow execution
- `POST /upload/image` - File upload (images, videos, audio)
- `GET /view` - Media file retrieval
- `GET /history` - Execution history
- `GET /queue` - Queue status
- `POST /queue` - Queue management
- `GET /system_stats` - System statistics
- `WebSocket /ws` - Real-time updates

### Custom Node Support
The application automatically adapts to custom nodes by parsing the `object_info` response. Supported parameter types:
- String, Integer, Float, Boolean
- Combo (dropdown selections)
- Image Upload, Video Upload, Audio Upload
- Custom types with proper metadata

## Development

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 11 or higher
- Android SDK 28+
- Gradle 8.0+

### Setup
1. Clone the repository
2. Open in Android Studio
3. Create `local.properties`:
   ```properties
   sdk.dir=/path/to/android/sdk
   ```
4. Sync Gradle and build

### Testing
```bash
# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest
```

## Contributing

Contributions are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

### Development Guidelines
- Follow Kotlin coding conventions
- Use Jetpack Compose for UI components
- Write unit tests for business logic
- Update documentation for new features

## License

This project is open source. Please add an appropriate license file.

## Acknowledgments

- [ComfyUI](https://github.com/comfyanonymous/ComfyUI) - The powerful AI workflow engine
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - Modern Android UI toolkit
- [Material Design 3](https://m3.material.io/) - Design system

## Support

For issues, questions, or feature requests, please open an issue on GitHub.

## Changelog

### Version 1.0.0 (Initial Release)
- Complete workflow import and execution system
- Real-time progress monitoring via WebSocket
- Image, video, and audio support
- Built-in mask editor
- LLM assistant integration
- Material Design 3 UI
- Dark mode support
- Execution history with media gallery
- Workflow storage and management

## Technical Highlights

### Performance Optimizations
- Lazy loading for large workflow lists
- Image caching with Coil
- Efficient WebSocket connection management
- Background task handling with WorkManager

### Security Features
- Secure credential storage with DataStore
- HTTPS support for encrypted communication
- Input validation and sanitization
- Proper permission handling

### User Experience
- Intuitive Material Design 3 interface
- Smooth animations and transitions
- Responsive layout for different screen sizes
- Comprehensive error messages and recovery options

---

**Note**: This application requires a running ComfyUI server. It does not include AI models or generation capabilities itself.
