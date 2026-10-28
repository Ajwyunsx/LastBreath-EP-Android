# UNDERTALE: Last Breath EP's Retake - Android

《UNDERTALE: Last Breath [EP's Retake]》的非官方 Android 移植版。项目使用原生 Android WebView 运行 TurboWarp 游戏，提供横屏显示、触屏摇杆和动作按键。

本仓库不是原游戏官方 Android 版本，与 Toby Fox、Team Last Breath 或 EP's Retake 团队没有隶属关系。

## 功能

- 原生 Android 横屏运行，最低 Android 6.0
- 白色像素风透明摇杆与 `Z / X / C` 按键
- 支持多点触控、斜向移动、WASD、方向键和空格确认
- 支持名字输入画面和软键盘
- 使用原游戏图标
- 游戏资源整合为加密的 `game.bin`
- 资源按需解密读取，支持并发加载和缓存
- 区分 Debug 与 Release 构建

## Debug 与 Release

### Debug

- 左上角显示黄色 `Debug` 按钮，无敌模式默认开启
- 点击按钮可开启或关闭无敌
- 开启时将 `HP저장` 持续恢复为 999，并清除 `피격` 受击状态
- 仅用于测试游戏流程

### Release

- 不显示 Debug 按钮
- 不启用无敌逻辑
- 发布时应使用签名后的 Release APK

## 下载

正式版本见 [Releases](https://github.com/Ajwyunsx/LastBreath-EP-Android/releases)。

## 拉取源码

`game-src/game.bin` 使用 Git LFS 保存，克隆前需要安装 Git LFS：

```bash
git lfs install
git clone https://github.com/Ajwyunsx/LastBreath-EP-Android.git
```

已有仓库可运行：

```bash
git lfs pull
```

`game-src` 只保存加密资源包。原始网页项目、Windows 安装包和解压缓存不包含在仓库中。

## 构建

要求：

- JDK 17 或兼容版本
- Android SDK Platform 36
- Android SDK Build-Tools 36 或兼容版本
- 已拉取完整的 Git LFS 文件

构建 Debug 与 Release：

```bash
./gradlew clean assembleDebug assembleRelease
```

输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release-unsigned.apk
```

Release 输出默认未签名，发布前需要使用自己的密钥库签名。CI 发布流程读取以下 GitHub Secrets：

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_PASSWORD`
- `ANDROID_KEY_ALIAS`

## 目录说明

```text
app/                 Android 应用与加密资源读取代码
game-src/game.bin    Git LFS 跟踪的完整加密游戏资源
tools/PackGame.java  资源打包与校验工具
.github/workflows/   Android 构建与发布工作流
```

## 来源与致谢

- 原游戏页面：[UNDERTALE: Last Breath EP's Retake - Game Jolt](https://gamejolt.com/games/EP_LastBreath/1013526)
- **UNDERTALE**：Toby Fox、Temmie Chang
- **UNDERTALE: Last Breath**：Team Last Breath
- **Last Breath ~Ep's Retake~**：Eupcha 及项目团队
- **程序**：Eupcha、PIXP、snowyland、USS
- **音乐**：Hydna（feat. Seotoo）
- **美术**：Coresync_、Hydna、TERRE
- **特别感谢**：Seotoo、wheedo07 及原项目致谢名单中的其他贡献者
- **运行技术**：TurboWarp、Scratch、Android WebView

原游戏及其角色、图片、音乐和剧情的权利归各自作者所有。本仓库仅提供非官方 Android 移植与触控适配，请保留原作者信息并遵循原项目的使用要求。
