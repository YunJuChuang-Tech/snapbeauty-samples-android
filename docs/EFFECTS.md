# 效果与参数清单

2026-10-21。以下为已实现的 Android 共用控制，适用于本地相机、Agora、ZEGO，以及 TRTC 的“更多效果”。应用与测试采用 Kotlin；美颜 SDK 单独获取，原创整妆资源随示例提供。

相机、Agora、ZEGO 页面底部可点击“收起面板”或在顶部把手向下滑动，腾出预览空间；点击“展开效果面板”或向上滑动恢复。收起保留全部效果、选中参数及页签，页面重建也恢复收起状态；收起时结束临时原图对比。TRTC 的“更多效果”使用弹窗，点“完成”关闭。

## 参数

面板值为 0–100，状态快照归一化到 0–1。SDK 1.0.0 使用 `engine.skin = SkinParams(...)` 与 `engine.face = FaceParams(...)`；磨皮传 `smoothing = smooth * 6`，其余值保持 0–1。面板沿用自己的状态 key，由 `ProcessorFactory` 映射到公开字段（如 `slim → faceSlimming`、`eyes → eyeScale`、`clarity → texture`）。iOS 使用对应的 `SnapBeautySkinParams`、`SnapBeautyFaceParams`。

“中性”指该单项无效果的位置；重置恢复默认组合：磨皮 35、美白 20、红润 10，其他美肤 0、美型各自中性。双向美型数值显示为 −50 到 +50，0 为中性；已调整的单项以描边标记，可单独恢复默认。

| 分组 | 界面名称 | 状态 key           | 中性值 |
| ---- | -------- | ------------------ | ------ |
| 美肤 | 磨皮     | `smooth`           | 0      |
| 美肤 | 美白     | `white`            | 0      |
| 美肤 | 红润     | `rosy`             | 0      |
| 美肤 | 锐化     | `sharpen`          | 0      |
| 美肤 | 清晰     | `clarity`          | 0      |
| 美肤 | 祛斑痘   | `delspot`          | 0      |
| 美肤 | 亮眼     | `eyeBright`        | 0      |
| 美肤 | 美牙     | `toothWhiten`      | 0      |
| 美肤 | 去黑眼圈 | `removePouch`      | 0      |
| 美肤 | 去法令纹 | `removeNasolabial` | 0      |
| 美肤 | 五官立体 | `faceThree`        | 0      |
| 美型 | 瘦脸     | `slim`             | 0      |
| 美型 | V 脸     | `cheekV`           | 0      |
| 美型 | 窄脸     | `cheekNarrow`      | 0      |
| 美型 | 短脸     | `cheekShort`       | 0      |
| 美型 | 小脸     | `cheekSmall`       | 0      |
| 美型 | 颧骨     | `cheekbones`       | 0      |
| 美型 | 下颌骨   | `lowerJaw`         | 0      |
| 美型 | 大眼     | `eyes`             | 0      |
| 美型 | 圆眼     | `eyeCircle`        | 0      |
| 美型 | 下巴     | `chin`             | 50     |
| 美型 | 额头     | `forehead`         | 50     |
| 美型 | 瘦鼻     | `nose`             | 0      |
| 美型 | 嘴型     | `mouth`            | 50     |
| 美型 | 唇厚     | `lipThick`         | 50     |
| 美型 | 眼睛高度 | `eyeHeight`        | 50     |
| 美型 | 开眼角   | `canthus`          | 0      |
| 美型 | 眼睑下至 | `eyeLid`           | 0      |
| 美型 | 眼距     | `eyeSpace`         | 50     |
| 美型 | 眼睛倾斜 | `eyeRotate`        | 50     |
| 美型 | 鼻长     | `longNose`         | 50     |
| 美型 | 人中     | `philtrum`         | 50     |
| 美型 | 微笑嘴角 | `smile`            | 0      |
| 美型 | 眉高     | `browHeight`       | 50     |
| 美型 | 眉间距   | `browSpace`        | 50     |
| 美型 | 眉毛粗细 | `browThick`        | 50     |

## 整套妆容

使用公开 `setMakeup(style, intensity)`，无妆传入 `null`。SDK 没有公开单独唇、眉、眼调节接口，面板仅提供整套风格与浓度，不显示无效的单妆开关。首次进入默认选中第一套完整整妆（资源不完整时为无妆），浓度预设 40；切换风格保留浓度。展示名直接使用当前发布 SDK 的公开妆容 ID；下列名称、SDK 妆容 ID 和搭配滤镜统一记录在共用的 `sample_makeup/manifest.json`，Android、iOS 直接读取。SDK 的 `setMakeup` 与 `setFilter` 仍是独立调用；整妆联动由本项目状态层实现。

| 展示名称 | SDK 妆容 ID | 搭配滤镜 ID |
| -------- | ----------- | ----------- |
| dawn     | `dawn`      | `rose3`     |
| glow     | `glow`      | `cool4`     |
| dew      | `dew`       | `fresh1`    |
| breeze   | `breeze`    | `fresh3`    |
| dusk     | `dusk`      | `cool11`    |
| neon     | `neon`      | `bright4`   |
| autumn   | `autumn`    | `bright3`   |
| ripple   | `ripple`    | `rose2`     |
| velvet   | `velvet`    | `warm2`     |
| haze     | `haze`      | `rose8`     |
| ember    | `ember`     | `fresh2`    |
| frost    | `frost`     | `warm2`     |
| cloud    | `cloud`     | `fresh3`    |
| shade    | `shade`     | `bright2`   |
| ink      | `ink`       | `mono1`     |

切换妆容会应用对应滤镜，初始及重置浓度为 40%，妆容和自动搭配滤镜共用该浓度；设为 0 时二者都关闭。切换妆容保留当前妆容浓度。选择“无妆”会清除自动搭配滤镜。

在滤镜页手动选择或调节强度后，滤镜转为独立模式，后续调整妆容浓度不会覆盖它；选择“原色”可保留局部妆效并关闭全局调色。再次选择妆容会重新启用它的搭配滤镜。选择“无妆”保留用户手动设置的独立滤镜。Android 重建保存联动状态；全部重置恢复dawn + rose3、浓度 40%。

妆容和滤镜的 ID 关系按 SDK 1.1.1 的现有场景配置核对，ink 补配 `mono1`。这份接入元数据与原创妆容素材的来源、制作方式分别记录；配置本身不改变 SDK 算法或资源字节。

## 滤镜

从当前 AAR 的 `assets/snapbeauty/luts/*.png` 动态发现并传给 `setFilter(key, intensity)`，不在源码硬编码资源数量。当前本地验证包为 27 个 LUT，另有“原色”（`null`）。自动搭配时强度跟随妆容浓度；手动选择或调节后可独立控制。选原色时禁用浓度控件，全部重置恢复默认妆容的搭配滤镜。

| SDK key 前缀 | 中文系列 |
| ------------ | -------- |
| `bright`     | 明亮     |
| `rose`       | 玫瑰     |
| `stylized`   | 个性     |
| `mono`       | 黑白     |
| `cool`       | 冷调     |
| `peach`      | 蜜桃     |
| `warm`       | 暖调     |
| `fresh`      | 清新     |
| `muted`      | 柔灰     |
| `natural`    | 自然     |

保留 key 的数字后缀区分同系列资源；未知系列显示“光谱”与稳定短标识，不直接暴露内部 key。缺少 LUT 时只显示原色。

## 人脸、重置与恢复

Android “人脸设置”提供最多 1–4 人脸，预算为 16.7、33.3、66.7 ms 或不限制（SDK 值 0）；默认 1 人脸和约 33.3 ms。通过 SDK `maxFaces` 与 `frameBudgetMs` 设置。iOS 1.0.0 的公开接口暂未提供这些设置，只提供单人处理说明。数量是处理上限，实际检测数和耗时依赖场景；未用多张真人面孔验证处理质量或性能预算。

切换单项不会丢弃其他参数；全部参数、整妆、滤镜、多人脸设置通过不可变快照传给 GL/RTC。Activity 重建保存完整快照；相机页额外恢复页签、选中单项与滚动位置。重置恢复默认组合与整妆搭配滤镜，恢复默认人脸设置；按住对比原图及取消触摸会恢复正常显示。

##
