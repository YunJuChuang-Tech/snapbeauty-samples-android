# 声网 Agora 美颜视频示例

Android 接入代码已实现并可构建。当前验证范围是本地相机、美颜、RGBA 转换、Agora 帧封装及原生库加载；真实频道加入、SDK 推帧接收、远端视频、音频及网络异常尚待有效凭据联调。

## 使用

使用 JDK 17、Android SDK 36：

```powershell
.\android\gradlew.bat -p android :app:assembleDebug
```

安装 `android/app/build/outputs/apk/debug/app-debug.apk`。首页选择“声网 Agora”，允许相机权限后可先在本地调节美颜、美型、美妆与原图对比。

点击“加入频道”后填写：

| 字段 | 本例要求 |
| --- | --- |
| App ID | 32 位十六进制字符；两端相同 |
| 频道名 | 1–64 位字母、数字、下划线或短横线；两端相同 |
| UID | 1–2147483647；两端不同 |
| Token | 与当前 App ID、频道及 UID 对应的有效会话 Token |

本例要求提供 Token，不演示客户端生成 Token。服务端凭据不进入客户端；Token 不保存到偏好设置或 Bundle。麦克风默认关闭，加入前可按需申请权限。退出频道后继续本地预览；切后台自动退出并释放相机。显示一个远端视频，多人频道以首个可用用户为准。

## 处理路径

```text
自定义 Camera2 采集
  → 转正后的 2D 纹理 → 美颜 SDK
  → 原图/效果选择 → GLES 读取 RGBA → 翻成顶行优先的紧凑字节数组
  → AgoraVideoFrame → pushExternalVideoFrameById(frame, 0)
  → Agora 自定义视频轨道与编码发送
```

Agora 固定使用 `io.agora.rtc:full-rtc-basic:4.6.4`，随统一 Demo 打包，只在该场景初始化。按官方自定义视频采集文档启用外部视频源，`publishCameraTrack=false`、`publishCustomVideoTrack=true`、`customVideoTrackId=0`，避免 SDK 再次开启内部相机。

使用 `AgoraVideoFrame.FORMAT_RGBA`，stride 的单位为像素，rotation 为 0，时间戳为采集纳秒时间换算出的毫秒。前置镜像只影响本地显示，发送的图像保持未镜像。每帧使用独立字节数组，不让 SDK 借用美颜引擎下一帧可能覆盖的纹理。推帧上限为 15 fps。

这一版有 GPU 读回与 CPU 拷贝成本，尚未做功耗、长时间性能与 GPU 纹理直传优化。数据转换、视频推送及引擎销毁有独立的生命周期边界，初始化与销毁串行执行，停止发送后才销毁引擎。

## 源码

- `AgoraActivity`：复用相机/效果界面，补充频道表单、远端画面与麦克风。
- `AgoraController`：SDK 初始化、加入/退出、自定义视频轨道推送与远端订阅。
- `AgoraFrameSink`：15 fps 节流、RGBA 帧封装与推送入口。
- `RgbaFrameReader`：GL 线程读回、行方向转换与宿主 pack/FBO 状态恢复。

前三个文件位于 `android/app/src/main/kotlin/ai/snapbeauty/samples/`，公共读取器位于对应的 `src/main/kotlin/` 包路径。

## 验证

2026-10-20，一加 PHK110 / Android 16 / arm64：迁移后的 Kotlin Agora 版 65 项设备检查通过。

- 真实 Agora 原生库可加载，返回 SDK 4.6.4 版本。
- 合成奇数宽度纹理逐字节验证 RGBA 通道、顶行方向，以及 pack alignment、row length、skip rows/pixels、PBO 和读/写 FBO 的恢复。
- 实际相机及美颜输出能转换为完整的竖屏帧；暂停时释放读取器，恢复后重建。
- 帧的像素 stride、尺寸、RGBA 标识、时间戳和无旋转约定通过检查。
- 空 Token、无效 App ID、频道名和 UID 在初始化 SDK 前被拒绝。

真实相机推帧测试使用记录接收端，未加入网络频道，也没有把“记录接收成功”算作 Agora SDK 已接收或远端已看到。SDK 连接、音频、退出连接、Token 失效、网络重连、远端效果与真人妆效仍待验收。

```powershell
.\android\gradlew.bat -p android :app:assembleDebugAndroidTest
adb shell am instrument -w ai.snapbeauty.samples.test/ai.snapbeauty.samples.SmokeTest
```

## 官方参考

- [自定义视频采集与默认轨道配置](https://doc.shengwang.cn/doc/rtc/android/advanced-features/custom-video-source)
- [自定义视频采集 API](https://doc.shengwang.cn/api-ref/rtc/android/API/toc_video_custom_capturenrendering)
- [AgoraVideoFrame 格式及 stride 定义](https://doc.shengwang.cn/api-ref/rtc/android/API/class_externalvideoframe)
