# Android 示例

一个 Kotlin Demo，通过首页进入相机美颜、腾讯云 TRTC、声网 Agora、即构 ZEGO 四个例子。

## 构建与安装

- JDK 17、Android SDK 36，Android 8.0 / API 26 及以上、OpenGL ES 3.0。
- Kotlin 2.3.10、AGP 8.13.2、Gradle Wrapper 8.13。
- Gradle 从 Maven 仓库解析 `cn.snapbeauty:snapbeauty-android:1.3.0`；SDK 使用及再分发遵循其自身条款。
- 统一 APK 包含 arm64-v8a 和 armeabi-v7a，与本地美颜引擎的架构范围一致。
- 通过 `ANDROID_HOME` 或不提交的 `android/local.properties` 指定 Android SDK。

Android Studio 打开本目录，或在仓库根目录执行：

```powershell
.\android\gradlew.bat -p android :app:assembleDebug
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

Debug 产物为 `android/app/build/outputs/apk/debug/app-debug.apk`，版本 `0.3.6`。它覆盖原美颜美妆主版，保留该包数据；旧 `.preview`、`.trtc`、`.agora`、`.zego` 包需单独卸载，不再使用。

新版 SDK 启动时通过 `SampleApplication` 初始化。使用者须自行获取与自身包名/签名证书绑定的有效授权，并保存到忽略的 `android/.local/license-assets/snapbeauty.license`，构建时会将其加入 assets。仓库不提供授权文件。缺失或无效时直接返回原图，`setLicenseErrorListener` 在主线程回调具体错误状态，不添加水印。

## 相机体验包

官网体验包只包含相机美颜，启动后直接进入预览，不打包三个 RTC SDK；最低 Android 6.0 / API 23，低版本设备仍需实测。构建时启用相机模式（保留 arm64-v8a 和 armeabi-v7a）：

```powershell
.\android\gradlew.bat -p android -PcameraOnly=true :app:assembleRelease
```

产物默认未签名，公开安装前须使用应用签名证书签名。普通构建仍保留四个场景。

## 四个场景

| 首页入口    | 功能与配置                                                                                  |
| ----------- | ------------------------------------------------------------------------------------------- |
| 相机美颜    | 自有 Camera2 → GLES → 美颜引擎 → 屏幕预览，无需房间凭据                                     |
| 腾讯云 TRTC | 进入即自动仅预览，使用厂商采集与共享纹理美颜；加入时填写 SDKAppID、房间号、用户 ID、UserSig |
| 声网 Agora  | 自有相机美颜与自定义 RGBA 视频源；加入时填写 App ID、频道、UID、Token                       |
| 即构 ZEGO   | 自有相机美颜与自定义 RGBA 采集；加入时填写 AppID、房间、用户 ID、Token                      |

相机权限用于预览；麦克风默认关闭，只在用户开启时申请。切换场景先停止原页面采集和会话，各厂商引擎由各自页面管理，不自动进房。Token/UserSig 只在会话输入，不保存凭据。真实远端通话仍待有效凭据联调。

共用面板覆盖 11 项美肤、25 项美型、15 套整妆、本地 AAR 动态滤镜，以及 1–4 人脸与处理预算。支持独立强度、按住对比、一键重置、前后相机、点击动画收放和上下跟手拖动；TRTC 在“更多效果”打开共用控制。Activity 重建保留参数、页签和选中项。完整说明见 [效果清单](../docs/EFFECTS.md)。

妆容名称、SDK ID 和搭配滤镜由两端共用清单提供。选妆容自动带上滤镜，强度随妆容浓度变化；手动滤镜可独立覆盖，全部重置恢复dawn + rose3。详见 [效果清单](../docs/EFFECTS.md)。

## 代码与资源

实现均位于 `app/src/main/kotlin/ai/snapbeauty/samples/`：

| 文件                                                       | 职责                           |
| ---------------------------------------------------------- | ------------------------------ |
| `HomeActivity.kt`                                          | 单一首页与四个场景入口         |
| `MainActivity.kt`、`PreviewRenderer.kt`、`CameraSource.kt` | Camera2 与实时预览             |
| `CameraFrameTransform.kt`、`TextureBeautyPipeline.kt`      | 方向、比例与 RTC 纹理处理      |
| `ProcessorFactory.kt`、`SdkTextureBridge.kt`               | SDK 参数映射与纹理行方向转换   |
| `EffectPanel.kt`、`EffectState.kt`、`CollapsiblePanel.kt`  | 共用效果、状态与面板手势       |
| `Trtc*.kt`、`Agora*.kt`、`Zego*.kt`                        | 各场景界面、配置和会话生命周期 |

原创整妆资源位于 `app/src/main/assets/makeup/`，并记录 MD5/SHA-256；启动时校验资源完整性。SDK 二进制、模型和基础 LUT 由 Gradle 依赖提供，不随源码仓库提交。图标是本项目原创 Canvas / VectorDrawable。

宿主传入已旋正、未镜像的常规 GL 纹理，SDK 适配层前后转换行方向；全中性参数直接返回原纹理。前置镜像只在最终本地显示应用，发送画面不镜像。显示使用单一比例中心裁切，不拉伸。初始化、处理、跟踪和释放均在对应 GL 线程执行；RTC 使用隔离上下文保护宿主状态。

## 设备验证

```powershell
.\android\gradlew.bat -p android :app:assembleDebugAndroidTest :app:lintDebug
adb install -r -t android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w ai.snapbeauty.samples.test/ai.snapbeauty.samples.SmokeTest
```

同一测试进程依次验证四个首页入口、返回、相机与 GPU、美颜资源、面板，以及三家 SDK 的本地加载和采集生命周期。可用 `-e suite makeup`、`-e suite panel` 或 `-e suite trtc` 运行专项。人脸回归使用本地照片和可选五官区域数据。测试包用于开发验证，日常只需安装主 APK。

官方依赖、版本和验证边界见 [TRTC](../docs/TRTC.md)、[Agora](../docs/AGORA.md)、[ZEGO](../docs/ZEGO.md)。
