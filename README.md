# SnapBeauty Android Samples

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](android/README.md)
[![API](https://img.shields.io/badge/API-23%2B-brightgreen.svg)](android/README.md)

本仓库提供 SnapBeauty 移动端美颜/美妆 SDK 在 Android 平台的接入示例，演示本地相机实时美颜以及三大主流实时音视频（RTC）方案的工程化集成。

---

## 包含场景

| 场景入口 | 采集与渲染方案 | 依赖 SDK | 场景文档 |
| :--- | :--- | :--- | :--- |
| **相机美颜** | Camera2 → GLES 3.0 → 美颜引擎 → 屏幕预览 | `cn.snapbeauty:snapbeauty-android:1.3.0` | [效果清单](docs/EFFECTS.md) |
| **腾讯云 TRTC** | TRTC 相机采集 → 共享 EGL 纹理美颜回调 → 发送 | `com.tencent.liteav:LiteAVSDK_TRTC:13.5.0.20670` | [TRTC 接入指南](docs/TRTC.md) |
| **声网 Agora** | 自定义 Camera2 采集 → 美颜 → RGBA 外部视频源推送 | `io.agora.rtc:full-rtc-basic:4.6.4` | [Agora 接入指南](docs/AGORA.md) |
| **即构 ZEGO** | 自定义 Camera2 采集 → 美颜 → 原始 RGBA 自定义采集推流 | `im.zego:express-video:3.25.0` | [ZEGO 接入指南](docs/ZEGO.md) |

---

## 特性亮点

- **全套效果支持**：涵盖 11 项美肤（磨皮/美白/红润/锐化等）、25 项微整形美型、15 套原创数学生成整妆以及 LUT 调色滤镜。详见 [效果与参数清单](docs/EFFECTS.md)。
- **严谨的 GL 上下文管理**：各场景采用隔离的 EGL 上下文与状态保护，避免修改宿主/RTC 的 VAO/VBO 与帧缓冲状态。
- **安全合规设计**：
  - 客户端零硬编码凭据，所有 RTC 房间号、Token、UserSig 仅在运行期会话输入，不持久化保存。
  - 所有内置美妆纹理掩码均通过数学公式程序化生成，无第三方图片版权争议。

---

## 快速上手

### 环境要求

- JDK 17
- Android SDK 36 (Build Tools 36.0.0, Compile SDK 36, Min SDK 23)
- Gradle 8.13 / AGP 8.13.2 / Kotlin 2.3.10
- 支持 OpenGL ES 3.0 的 Android 6.0+ 物理设备（推荐 Android 8.0+）

### 获取授权文件 (License)

美颜 SDK 在启动时通过 `SampleApplication` 进行初始化：
- 使用者须获取与自身应用包名（ApplicationId）及签名证书绑定的有效授权文件。
- 将授权文件保存至忽略路径 `android/.local/license-assets/snapbeauty.license`，构建系统会自动将其打包到 assets 中。
- **注**：仓库不随代码提供商业授权。若缺少授权或授权无效，SDK 会自动旁路直出原图并在主线程回调错误状态，不影响工程编译运行。

### 构建与安装

使用 Android Studio 打开 `android` 目录，或在根目录下执行：

```powershell
# 编译全功能 Debug APK（包含相机与三家 RTC 演示）
.\android\gradlew.bat -p android :app:assembleDebug

# 安装到连接的设备
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

#### 仅相机体验包 (Camera Only)

若仅需体验基础相机美颜，无需打包三家 RTC SDK，可指定 `-PcameraOnly=true` 构建精简包：

```powershell
.\android\gradlew.bat -p android -PcameraOnly=true :app:assembleRelease
```

---

## 目录结构

```text
snapbeauty-samples-android/
├── android/               # Android 完整工程根目录
│   ├── app/               # 示例主应用模块
│   └── README.md          # Android 工程构建、架构与设备验证详解
├── certificates/          # 示例签名证书（公钥）
├── docs/                  # 各厂商接入与效果技术文档
│   ├── EFFECTS.md         # 美颜/美型/整妆参数清单与联动规则
│   ├── TRTC.md            # 腾讯云 TRTC 纹理回调方案
│   ├── AGORA.md           # 声网 Agora 自定义 RGBA 推流方案
│   └── ZEGO.md            # 即构 ZEGO 原始数据推流方案
└── LICENSE                # 开源许可证 (Apache 2.0)
```

更多细节请查阅 [Android 工程详细说明](android/README.md)。

---

## 许可证与法律声明

1. **示例工程源码**：本项目除第三方组件外的示例代码遵循 [Apache License 2.0](LICENSE) 协议开源。
2. **底层 SDK 说明**：`cn.snapbeauty:snapbeauty-android` 为商业专有美颜 SDK，从 Maven 仓库公开解析，其使用与商业分发须遵循 SnapBeauty 官方商业授权协议。
3. **第三方 SDK**：腾讯云 TRTC、声网 Agora 与即构 ZEGO SDK 属于各厂商所有，受其各自官方开发者协议约束。
