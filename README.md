# JIQU2

一款基于 Jetpack Compose 的 Android 媒体解析与下载工具。

## 当前版本

`2.0.2`（内部版本号：`202`）

## 功能

- 解析支持的平台链接并预览媒体
- 下载视频、图片和音频
- 下载历史记录
- 下载完成通知
- 深色模式、主题色与悬浮底栏设置
- 关于页面提供项目开源地址

## 构建

使用 Android Studio 打开项目，或在 Windows 终端执行：

```powershell
.\gradlew.bat testDebugUnitTest assembleRelease
```

R8 优化后的 APK 位于 `app/build/outputs/apk/release/`。项目需要在 `local.properties` 中配置 Android SDK；抖音接口密钥使用 `douyin.api.key` 配置，文件不会提交到 Git。

## 开源地址

https://github.com/dhvbjvvb/JIQU2

## 许可证

本项目采用 MIT License，详见 [LICENSE](LICENSE)。
