# QuadPlayer

QuadPlayer 是一款 Android 本地视频播放器，可在同一屏幕上同时播放最多 4 个视频。四宫格模式支持随时切换声音来源，也可以将所有画面的播放进度对齐到当前音频源，适合对比视频素材或查看同一事件的多个机位。

## 下载与安装

**[下载 QuadPlayer v1.0 APK](https://github.com/wonderbench/quadplayer/releases/download/v1.0/QPapp-debug.apk)** · [查看 v1.0 发布页](https://github.com/wonderbench/quadplayer/releases/tag/v1.0)

v1.0 提供调试版 APK，适用于 Android 8.0（API 26）及以上设备。安装后首次启动时，授予视频读取权限即可浏览设备上的本地视频。

## 主要功能

- **四宫格播放** — 在 2×2 网格中同时播放最多 4 个视频。双击画面或点击音频按钮切换音频源，可启用音频交叉淡入淡出。
- **同步与独立控制** — 一键播放或暂停全部视频、统一调速，或把进度对齐到当前音频源。每个画面可单独暂停、调速、调整进度或更换视频；长按画面打开操作菜单并交换位置。
- **单视频播放** — 支持 0.5× 至 3× 变速、多音轨选择、外挂字幕、手势控制和断点续播；从播放列表进入时可前后切换、自动连播或开启单曲循环。启用后台播放后，可通过系统媒体控件暂停或继续。
- **本地媒体库** — 自动扫描设备视频，支持按文件夹浏览、按名称搜索和排序，并可管理收藏、播放历史和自定义播放列表。

进入底部导航的「四格」页面，点击空白格的 **+** 即可添加视频。更多操作说明见 [使用指南](About.md#使用指南)。

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

首轮建议在两款性能不同的真机上，以相同的 1080p H.264 素材分别测单路与四路，四路运行 10 分钟，再测 HEVC 和 4K；记录素材、设备、系统版本、导航模式与环境条件。四路内存和首帧指标应与 PRD 目标对照，解码器报告的实例上限不代表保证可同时播放的路数。

工具冒烟结果（仅验证测量流程，不能代表真机能力）：Android 16 x86_64 模拟器、320×180 MPEG-4 Part 2、15 fps，采样 12 秒。单路首帧 272 ms、PSS 峰值 238350 KB、渲染 184 帧、丢帧 0；四路首帧分别为 346、286、358、374 ms，PSS 峰值 239769 KB，渲染帧数分别为 183、183、184、184，丢帧均为 0。四路第 10 秒的估算时间戳偏差为 66 ms。上述样本并非 1080p H.264，不能用于判定 PRD 指标。原始记录：[单路](benchmarks/results/emulator-api36-mpeg4-1player.json) · [四路](benchmarks/results/emulator-api36-mpeg4-4player.json)。
