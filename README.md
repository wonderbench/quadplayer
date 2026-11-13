# QuadPlayer

QuadPlayer 是一款 Android 本地视频播放器，可在同一屏幕上同时播放最多 4 个视频。四宫格模式支持随时切换声音来源，也可以将所有画面的播放进度对齐到当前音频源，适合对比视频素材或查看同一事件的多个机位。

应用不申请网络访问权限，媒体文件在设备本地播放。

项目自身代码和文档采用 [MIT License](LICENSE)。第三方组件和素材仍遵循其各自的许可及署名要求。

## 下载与安装

**[下载 QuadPlayer v1.0 APK](https://github.com/wonderbench/quadplayer/releases/download/v1.0/QPapp-debug.apk)** · [查看 v1.0 发布页](https://github.com/wonderbench/quadplayer/releases/tag/v1.0)

v1.0 提供调试版 APK，适用于 Android 8.0（API 26）及以上设备。安装后首次启动时，授予视频读取权限即可浏览设备上的本地视频。

后续版本的变更见 [更新记录](CHANGELOG.md)。

## 构建发布版本

默认从 Google、Maven Central 和 Gradle 插件仓库下载依赖；如需优先使用国内镜像，可在构建命令中加 `-PuseChinaMirrors=true`。Gradle Wrapper 使用带 SHA-256 校验的 8.14 精简发行包。

Gradle 依赖校验清单记录构建、测试和发布所用组件的 SHA-256；依赖升级后需重新生成并审阅新增校验值。当前清单根据本机构建时解析的依赖生成（含可选镜像来源），可检测之后的内容变化，但首次生成的哈希本身不证明上游来源可信；更新时应核对发布方的来源及校验值。

应用设置中的「第三方开源许可」列出发布运行时组件、字体和对应许可声明。`verifyThirdPartyNotices` 对比发布运行时依赖与随包清单；依赖变化时应先检查组件的许可和 NOTICE，更新声明及 `app/src/main/assets/licenses/release_components.txt`，再运行该检查。此检查只核对组件清单，不会自动判断许可内容是否完整。

`assembleRelease` 启用代码和资源压缩。要生成正式签名包，请在安全环境中提供四个环境变量：`QP_RELEASE_STORE_FILE`（密钥库路径）、`QP_RELEASE_STORE_PASSWORD`、`QP_RELEASE_KEY_ALIAS`、`QP_RELEASE_KEY_PASSWORD`。不要把密钥或密码提交到仓库；务必备份并持续使用同一正式密钥。未配置时构建产物为未签名包，不能直接发布。`QP_VERSION_CODE` 可指定正整数版本号，`QP_VERSION_NAME` 可指定显示版本；每次正式发布应确保版本号递增。已安装的 v1.0 调试包与正式签名包之间不能直接覆盖安装，需事先考虑用户数据迁移。

正式发布工作流由推送 `vMAJOR.MINOR.PATCH` 标签触发：在仓库的 Actions secrets 中配置 `QP_RELEASE_KEYSTORE_BASE64`（正式密钥库文件的 Base64 内容）、`QP_RELEASE_STORE_PASSWORD`、`QP_RELEASE_KEY_ALIAS` 和 `QP_RELEASE_KEY_PASSWORD` 后，工作流会测试、构建并校验签名，然后创建 GitHub Release。版本号按 `MAJOR × 1000000 + MINOR × 1000 + PATCH` 计算（MINOR、PATCH 均不超过 999，结果不超过 2100000000），新标签必须生成比已发布版本更大的版本号。未配置密钥时工作流会失败，不会发布未签名包；应先在测试环境验证正式密钥和升级路径。

## 主要功能

系统备份和换机迁移仅包含应用设置；本机媒体索引、收藏、播放历史、播放列表、四格会话和预设不会迁移，避免旧设备的媒体地址指向新设备上的其他文件。

- **四宫格播放** — 在 2×2 网格中同时播放最多 4 个视频。双击画面或点击音频按钮切换音频源，可启用音频交叉淡入淡出。
- **四格预设** — 保存有名称的片源、进度、速度、布局、播放状态和音频源；加载前会确认替换当前会话，失效片源会跳过。循环设置不保存在预设中，视频文件或授权失效后需重新选择。
- **同步与独立控制** — 一键播放或暂停全部视频、统一调速，或把进度对齐到当前音频源。对齐会暂时暂停各路，等待其他视频定位就绪后恢复原先的播放状态；开启 A–B 区间循环时需先清除区间才能对齐。每个画面可单独暂停、调速、调整进度、更换视频，或设置整片及 A–B 区间循环（更换视频或退出四格后重置循环设置）；长按画面打开操作菜单并交换位置。区间循环按时间检查，边界并非逐帧精确。
- **单视频播放** — 支持 0.5× 至 3× 变速、多音轨选择、外挂字幕、手势控制和断点续播；从播放列表进入时可前后切换、自动连播或开启单曲循环。启用后台播放后，可通过系统媒体控件暂停或继续。
- **本地媒体库** — 自动扫描设备视频，支持按文件夹浏览、按名称搜索和排序，并可管理收藏、播放历史和自定义播放列表。

手动导入的视频可以从媒体库隐藏；也可以选择同时释放该文件的持久读取授权。释放前会提示可能受影响的播放和四格会话，原文件不会被删除；需要时可重新选择并授权。

在「设置 → 管理文件授权」中还可以查看并释放其他保留的读取授权，包括不再出现在媒体库里的文件、音轨和字幕；释放前请确认其是否仍用于播放或预设。

进入底部导航的「四格」页面，点击空白格即可添加视频。更多操作说明见 [使用指南](About.md#使用指南)。

应用内介绍与 `About.md` 的共享部分使用同一组字符串和分组。修改 `app/src/main/res/values/about_structure.xml` 或对应的中英文字符串后，运行 `:app:syncAboutDocs` 更新文档，并运行 `:app:verifyAboutDocs` 检查；技术架构和常见问题部分可单独维护。

## 性能测量

仓库提供按需运行的设备测试 `PlaybackPerformanceTest`。默认测试会跳过它；运行前请准备一段比采样时间至少长 5 秒的本地视频，并在专用测试设备上安装 Debug 包和测试包。以下示例中的序列号和 URI 必须替换为实际设备与视频；安装会更新设备上同包名的 Debug 应用。

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
adb -s <设备序列号> install -r -g app/build/outputs/apk/debug/app-debug.apk
adb -s <设备序列号> install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s <设备序列号> shell content query --uri content://media/external/video/media --projection _id:_display_name
adb -s <设备序列号> shell am instrument -w -r -e class com.quadplayer.benchmark.PlaybackPerformanceTest -e video_uri content://media/external/video/media/<视频ID> -e player_count 4 -e duration_seconds 600 com.quadplayer.test/androidx.test.runner.AndroidJUnitRunner
```

`player_count` 支持 1 或 4。结果在命令输出的 `benchmark` 字段和 `reportFile` 路径中，记录设备、系统、视频轨道、各路从加载到首帧的时间、采样窗口内渲染与丢帧、进程 PSS 峰值，以及系统热状态。时间戳偏差是按帧的显示计划时间与媒体时间估算，并非屏幕实拍同步误差；实际同步应另用带时间码素材和拍摄方法复核。PSS 包含应用和测试进程的其他工作，比较时应保持测试环境一致。

四路测量使用四格的 5–15 秒本地片源缓冲配置；下方旧版冒烟记录使用的是此前的默认缓冲配置，不能直接用于评价新配置的效果。

新配置在同一模拟器和低分辨率素材上的 12 秒流程验证：[原始记录](benchmarks/results/emulator-api36-mpeg4-4player-compact-buffer.json)。此记录不能代替 1080p 真机测试，也不能单独证明内存有所改善。

偏差采样需要每一路近期都有帧；缺帧或停帧时对应采样值为 `null`，不能当作零偏差。旧版工具生成的下方冒烟记录尚未检查帧是否过期，偏差数字仅保留作流程记录，不应作为同步性能证据。

首轮建议在两款性能不同的真机上，以相同的 1080p H.264 素材分别测单路与四路，四路运行 10 分钟，再测 HEVC 和 4K；记录素材、设备、系统版本、导航模式与环境条件。四路内存和首帧指标应与 PRD 目标对照，解码器报告的实例上限不代表保证可同时播放的路数。

工具冒烟结果（仅验证测量流程，不能代表真机能力）：Android 16 x86_64 模拟器、320×180 MPEG-4 Part 2、15 fps，采样 12 秒。单路首帧 272 ms、PSS 峰值 238350 KB、渲染 184 帧、丢帧 0；四路首帧分别为 346、286、358、374 ms，PSS 峰值 239769 KB，渲染帧数分别为 183、183、184、184，丢帧均为 0。四路第 10 秒的估算时间戳偏差为 66 ms。上述样本并非 1080p H.264，不能用于判定 PRD 指标。原始记录：[单路](benchmarks/results/emulator-api36-mpeg4-1player.json) · [四路](benchmarks/results/emulator-api36-mpeg4-4player.json)。
