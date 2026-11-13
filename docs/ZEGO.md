# 即构 ZEGO 美颜推拉流示例

Android Kotlin 示例已实现。当前验证覆盖本地相机、美颜、RGBA 转换、原始采集参数与原生库加载；真实房间鉴权、SDK 收帧、远端推拉流、音频及重连仍需有效凭据联调。

## 使用

准备 JDK 17、Android SDK 36：

```powershell
.\android\gradlew.bat -p android :app:assembleDebug
```

安装 `android/app/build/outputs/apk/debug/app-debug.apk`，首页选择“即构 ZEGO”。允许相机权限即可先调节本地效果；点击“加入房间”填写：

| 字段    | 本例要求                                                       |
| ------- | -------------------------------------------------------------- |
| AppID   | 1–4294967295 的整数，两端相同                                  |
| 房间名  | 1–64 位字母、数字、下划线或短横线，两端相同                    |
| 用户 ID | 1–32 位上述字符，两端不同                                      |
| Token   | 服务端为当前用户生成，并具有当前房间及推流所需权限的有效 Token |

客户端不包含 AppSign 或服务端密钥；Token 仅在当前会话输入，不保存到偏好设置或 Bundle。只保存非敏感的 AppID、房间名和用户 ID。麦克风默认关闭，加入前可按需申请录音权限。

退出房间后继续本地预览；切到后台自动停止发送并释放相机。示例订阅首个可用远端流，忽略自己的流。

## 处理路径

```text
Camera2 → OES 转正为未镜像的二维纹理 → 美颜 SDK
  → 选择原图或效果 → GL 读回 → 顶行优先 RGBA
  → 直接 ByteBuffer → sendCustomVideoCaptureRawData
  → ZEGO 编码、推流与远端播放
```

固定依赖 `im.zego:express-video:3.25.0`，从即构官方 Maven 仓库获取，与 TRTC 和 Agora 同包，各场景分别管理引擎生命周期。

引擎使用 `STANDARD_VIDEO_CALL` 场景，启用 `RAW_DATA` 自定义视频采集。仅在房间登录成功且收到主通道自定义采集 `onStart` 后提交帧；`onStop` 或退出时停止提交。使用自定义采集时保留 SDK 所需的视频采集开关，不另启 SDK 相机预览。

`ZegoVideoFrameParam` 为 `RGBA32`，`strides[0]` 使用**每行字节数 width × 4**，rotation 为 0。按 API 要求，把相机的单调采集时间映射成 Unix 毫秒时间，保留采集到提交的时间差。前置镜像只用于本地显示。直接缓冲区在提交返回后复用；采集提交上限为 15 fps。GPU 读回及 CPU 拷贝仍有开销，长时间性能和功耗尚未验收。

引擎创建和销毁在独立串行执行器处理，帧提交与销毁使用同一把锁。ZEGO 原生销毁异步完成后，才允许该会话重新创建引擎。鉴权和连接错误仅展示数字代码，避免把 SDK 附带的会话数据放进诊断文案。

## Kotlin 源码

厂商代码位于 `android/app/src/main/kotlin/ai/snapbeauty/samples/`：

- `ZegoActivity.kt`：房间配置与场景入口。
- `ZegoController.kt`：Token 登录、推拉流、采集启停、麦克风与销毁。
- `ZegoFrameSink.kt`：RGBA 缓冲区封装、节流和提交入口。
- `ZegoRoomConfig.kt`：联网前校验，凭据不进入 `toString()`。

公共 Kotlin 代码位于 `src/main/kotlin/`：`MainActivity` 提供相机和效果面板，`RawRtcActivity` 共用房间控件，`RgbaFrameReader` 负责 GL 读取及宿主状态恢复。

##

## 官方参考

- [集成 SDK](https://doc-zh.zego.im/real-time-video-android-java/quick-start/integrating-sdk)
- [自定义视频采集](https://doc-zh.zego.im/real-time-video-android-java/video/custom-video-capture)
- [引擎配置](https://doc-zh.zego.im/real-time-video-android-java/client-sdk/api/class/zegoengineprofile)
- [Token 鉴权升级说明](https://doc-zh.zego.im/faq/express-token-upgrade)
