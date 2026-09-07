# JIQU2

一款基于 Jetpack Compose 的 Android 媒体解析与下载工具。

## 当前版本

`2.0.6`（内部版本号：`2006`，递增自 `2.0.5 / 2005`）

## 功能

- 解析支持的平台链接并预览媒体
- 下载视频、图片和音频
- 下载历史记录
- 下载完成通知
- 深色模式、主题色与悬浮底栏设置
- 关于页面提供项目开源地址
- 启动时通过 CDN 加速访问 GitHub Releases 静默检查更新
- 发现新版本后可选择 GitHub 应用内下载安装，或复制蓝奏云提取码并在浏览器手动下载
- GitHub 应用内下载完成后会校验包名、版本号和签名，再打开系统安装器

## 构建

使用 Android Studio 打开项目，或在 Windows 终端执行：

```powershell
.\gradlew.bat testDebugUnitTest assembleRelease
```

R8 优化后的 APK 位于 `app/build/outputs/apk/release/`。项目需要在 `local.properties` 中配置 Android SDK；抖音接口密钥使用 `douyin.api.key` 配置，文件不会提交到 Git。

### Release 签名

当前项目使用 `com.jiqu.lite` 包名及 Release 证书，以支持 2.0.6 及后续版本覆盖升级。配置好 `JIQU_RELEASE_KEYSTORE`、`JIQU_RELEASE_KEY_ALIAS`、`JIQU_RELEASE_LINEAGE` 和 `JIQU_RELEASE_STORE_PASSWORD` 用户环境变量后执行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\sign-release.ps1
```

签名 APK 输出到 `app/build/outputs/apk/release/app-release.apk`。keystore、证书 lineage、密码和构建产物均不得提交到 Git。

## 开源地址

https://github.com/dhvbjvvb/JIQU2

蓝奏云更新地址：

https://wwbjl.lanzout.com/b01d77wdje（提取码：`ccvd`）

## 许可证

本项目采用 MIT License，详见 [LICENSE](LICENSE)。
