# 腾讯云 TRTC 美颜视频示例

示例代码已实现，TRTC 本地采集与美颜回调已在真机验证。尚未使用有效凭据完成双端进房、音频或远端画面验收。

## 版本与运行

- Android API 23 及以上、OpenGL ES 3.0；低版本设备上的 TRTC 场景待验证。
- TRTC：`com.tencent.liteav:LiteAVSDK_TRTC:13.5.0.20670`。
- 美颜引擎：Gradle 解析的 SnapBeauty 1.3.0，不随源码仓库提交。

```powershell
.\android\gradlew.bat -p android :app:assembleDebug
```

安装 `android/app/build/outputs/apk/debug/app-debug.apk`。打开应用，选择“腾讯云 TRTC”。

1. 进入后自动开启“仅预览”，首次使用允许相机权限后即可看到本地画面与美颜效果；不自动加入房间，麦克风默认关闭。手动停止后可点“仅预览”重新开启。
2. “磨皮”可直接调节，“更多效果”提供美白、红润、瘦脸、大眼及妆容选择和浓度。
3. 按住对比原图，松开恢复效果；支持前后摄像头切换。
4. 点“加入房间”，填入下表配置；另一台设备加入相同房间。
5. 需要语音时，加入前开启麦克风并允许权限。默认仅发送视频。
6. 点“停止”退出；切后台或离开页面也会停止并退出，不自动重新加入。

| 字段     | 要求                                                         |
| -------- | ------------------------------------------------------------ |
| SDKAppID | 对应 TRTC 应用的正整数 ID                                    |
| 房间号   | 本例支持 1–2147483647 的整数，两端相同                       |
| 用户 ID  | 1–32 位字母、数字、下划线或短横线，两端不同                  |
| UserSig  | 服务端为当前应用与用户生成的有效签名；每名用户使用自己的签名 |

UserSig 仅用于当前会话，不保存到偏好设置、Bundle 或代码。AppID、房间号与用户 ID 可在本机记住。不要在客户端生成签名或内置服务端密钥。SDK 自身的日志与数据处理遵循其官方说明。

本例显示一个远端视频。若有多人进入，显示首个可用远端视频；它离开后再选择下一个。此版本以双人通话体验为目标。

## 帧通路

```text
TRTC 相机采集（2D 纹理）
    → 同步交给美颜 GL 工作线程
    → 共享纹理、隔离 GL 状态
    → 按旋转信息转正 → 美颜处理 → 还原帧方向
    → 同步返回处理结果
    → TRTC 预览、编码与发送
```

通过 `setLocalVideoProcessListener` 请求 `TRTC_VIDEO_PIXEL_FORMAT_Texture_2D` 与 `TRTC_VIDEO_BUFFER_TYPE_TEXTURE`。本例在返回回调之前完成处理，不让借用的输入纹理留在后台继续使用。

美颜线程拥有独立 EGL 上下文，与 TRTC 共享纹理，不修改 TRTC 的 VAO、VBO 和像素传输状态。停止时也由这条线程释放引擎，避免依赖停止采集后可能不再到来的帧回调。第一版使用同步 GPU 完成操作保证纹理交接顺序，尚未针对通话帧率与功耗优化。

旋转按顺时针角度处理，90/180/270 度采用精确矩阵与最近邻采样进行像素重排。输出还原原始宽高和方向，保留帧时间戳、旋转与 EGL 共享信息。原图对比直接返回输入纹理。效果处理异常时回退原图并提示，不把异常描述中的会话数据直接显示或记录。

## 源码入口

| 文件                                                | 职责                                |
| --------------------------------------------------- | ----------------------------------- |
| `android/app/src/main/.../TrtcActivity.kt`          | 房间表单、权限、效果和通话界面      |
| `android/app/src/main/.../TrtcRoomConfig.kt`        | 联网前校验配置                      |
| `android/app/src/main/.../TrtcController.kt`        | 进房、退房、远端订阅与相机/音频控制 |
| `android/app/src/main/.../TrtcBeautyListener.kt`    | TRTC 视频纹理回调                   |
| `android/app/src/main/.../RtcBeautyWorker.kt`       | 同步跨线程处理及明确的释放生命周期  |
| `android/app/src/main/.../TextureBeautyPipeline.kt` | 方向处理、原图旁路与美颜处理        |
| `android/app/src/main/.../SharedGlContext.kt`       | EGL 共享与宿主上下文恢复            |

省略的包路径均为 `kotlin/ai/snapbeauty/samples`。



## 官方参考

- [Android SDK 获取与依赖配置](https://trtc.io/document/35093)
- [视频帧处理与 GL 生命周期回调](https://liteav.sdk.qcloud.com/doc/api/en/group__TRTCCloudListener__android.html)
- [Android 类型与旋转方向定义](https://trtc.io/zh/document/50768)
- [Android 接口概览](https://trtc.io/zh/document/35125)
