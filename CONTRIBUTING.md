# 贡献指南

感谢你对 ComfyUI GO 的关注！

## 🚀 开发环境

- Android Studio Hedgehog | 2023.1.1 或更高版本
- JDK 11
- Android SDK 28+
- Kotlin 1.9+

## 📦 构建项目

1. Clone 项目
```bash
git clone https://github.com/你的用户名/ComfyUI-GO.git
cd ComfyUI-GO
```

2. 创建 `local.properties` 文件
```properties
sdk.dir=/path/to/your/Android/sdk
```

3. 构建
```bash
./gradlew assembleDebug
```

## 🔧 代码规范

- 使用 Kotlin 官方代码风格
- 遵循 MVVM + Clean Architecture
- 提交前运行 `./gradlew ktlintCheck`

## 🐛 提交 Issue

- 使用清晰的标题
- 提供复现步骤
- 附上设备信息和日志

## 💡 提交 Pull Request

1. Fork 项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

## 📝 提交信息规范

```
feat: 新功能
fix: 修复 bug
docs: 文档更新
style: 代码格式调整
refactor: 重构
test: 测试相关
chore: 构建/工具链相关
```

## 🤝 行为准则

- 尊重所有贡献者
- 保持友好和专业
- 接受建设性批评
