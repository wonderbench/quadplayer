# QuadPlayer 优化建议

审阅日期 2026 年 11 月 12 日，代码基线 `20016d3`。本文通读了全部 Kotlin 源码（约 6000 行）、构建脚本、应用清单和现有文档，按缺陷、播放性能、交互、功能、架构、构建发布、测试、安全和文档九个方面给出建议。

阅读前请注意两点：

- **本文是静态审阅。** 当前环境没有 Android SDK，未能构建、运行测试或在设备上复现。结论全部来自代码阅读，标注「待真机确认」的条目需要先验证再动手。
- **本文接续[上一轮开发审核](QuadPlayer_Development_Review.md)（基线 `58bbb7f`）。** 上一轮提出、至今未处理的事项只在第 2 节列出，不再展开。其余各节是本轮的新发现。

优先级含义：P0 拖得越久代价越高；P1 用户会直接遇到的缺陷；P2 体验与性能改进；P3 长期或可选。

## 1. 最该先做的九件事

| 优先级 | 问题 | 为什么要紧 | 详见 |
| --- | --- | --- | --- |
| P0 | 对外发布的是调试包 | 换成正式签名时，老用户必须卸载重装，收藏和历史全部丢失 | 8.1 |
| P1 | 四格切到后台或锁屏后继续播放出声 | 耗电，用户以为已经停止 | 3.1 |
| P1 | 四格选到无法解析的文件会崩溃 | 进程退出，四格里已装载的内容全部丢失 | 3.2 |
| P1 | 音量手势上滑几乎无效，下滑过于灵敏 | 核心手势实际不可用 | 3.3 |
| P1 | 看完的视频再次打开会停在片尾 | 每个看完的视频都会遇到 | 3.4 |
| P1 | 没有音频焦点处理，拔耳机不暂停，未设置屏幕常亮 | 与其他应用同时出声、外放、播放中熄屏 | 4.1 |
| P2 | 四格控制层挤占画面，且没有退出按钮 | 四格是产品的核心卖点 | 5.1 |
| P2 | 播放列表删不掉，首页标签返回后重置，列表没有缩略图 | 媒体库的日常使用体验 | 3.8、5.4 |
| P2 | 使用说明与实际手势不一致 | 用户照文档操作没有反应 | 9.3 |

## 2. 上一轮审核项的落地情况

**已落地。** 以下事项在当前代码中已能看到对应实现：

- 单视频横屏紧凑控制区，拖动进度时只在松手后定位一次，拖动期间暂停自动隐藏。
- 单视频横屏沉浸式全屏，回到前台时重新隐藏系统栏。
- API 26–28 的媒体库扫描兼容；扫描失败时保留原有缓存。
- 画中画入口崩溃。
- 四格换位保留进度和播放状态，移除时清空片源，全局播放只作用于有内容的格子。
- Android 14 及以上的部分媒体授权。
- 播放错误保留到重试或换片，四格逐格显示错误和重试入口。
- 续播保存改为有序写入队列；Room 开启 schema 导出并提供 1→2 迁移。
- `gradle.properties` 中的本机 JDK 路径已移除。

**仍未处理。** 建议继续按上一轮的描述推进，本文有补充的已注明：

| 事项 | 现状 |
| --- | --- |
| 四格播放器按需创建、状态轮询拆分 | 未动，见 4.2、4.3 的补充 |
| 并发解码能力实测与降级 | 未动，见 4.6 |
| 同步语义与偏差测量 | 未动，见 4.5 的补充 |
| 媒体库刷新合并 | 已加互斥锁串行执行，重复扫描和逐条写入仍在，见 4.7 |
| 四格单格播放控制、可见的音频源入口 | 未动 |
| 单视频播放队列 | 未动 |
| 1+3 焦点布局、单格放大 | 未动 |
| 四格会话恢复 | 未动 |
| 完整的后台播放 | 未动 |
| CI 检查、README 手势说明更新 | 未动，见 9.1、9.3 |

## 3. 缺陷与稳定性

### 3.1 四格切到后台或锁屏后继续播放（P1）

四格页没有监听生命周期。按 Home 键或锁屏后，已装载的播放器会继续播放并出声，「后台播放」开关对四格不起作用。单视频页有 `ON_STOP` 处理，四格页缺少对应逻辑。

建议在四格页 `ON_STOP` 时记录各格的播放状态并全部暂停，`ON_START` 时按记录恢复。以后若要支持四格后台出声，再交给前台服务处理。

[单视频的 ON_STOP 处理](app/src/main/java/com/quadplayer/feature/player/PlayerScreen.kt#L128-L139) · [四格页入口](app/src/main/java/com/quadplayer/feature/quadplayer/QuadPlayerScreen.kt#L59-L109)

### 3.2 四格选到无法解析的文件会崩溃（P1）

`inspectImportedVideo` 调用 `MediaMetadataRetriever.setDataSource`，文件损坏或格式不支持时会抛异常。函数里只有 `finally`，没有 `catch`。调用方 `addVideo` 在 `viewModelScope.launch` 里直接调用，异常没有人接住，进程会崩溃。导入返回 `null` 时也只是静默返回，用户看不到任何提示。

建议元数据读取失败时降级为默认值，是否能播放交给播放器判断；确实无法导入时，在对应格子显示「无法读取该文件」和重新选择的入口。

[导入元数据读取](app/src/main/java/com/quadplayer/core/data/MediaStoreVideoScanner.kt#L83-L115) · [四格添加视频](app/src/main/java/com/quadplayer/feature/quadplayer/QuadPlayerViewModel.kt#L106-L115)

### 3.3 音量手势上滑几乎无效，下滑过于灵敏（P1）

每个拖动事件都用 `(current + delta * max).toInt()` 计算目标音量。单个事件的位移通常只相当于零点几格音量。上滑时小数部分被截掉，音量不变。下滑时 `9.93` 被截成 `9`，每个事件都降一格。结果是下滑几十像素就静音，上滑要划得很快才有反应。

建议用浮点数累计位移，累计满一格再调整一次系统音量，并在画面上显示音量和亮度的指示条。

[音量与亮度手势](app/src/main/java/com/quadplayer/feature/player/PlayerScreen.kt#L592-L611)

### 3.4 看完的视频再次打开会停在片尾（P1）

续播位置每 5 秒保存一次，退出时再保存一次，都不判断是否已经播完。视频播完后保存的是片尾位置，下次打开会直接跳到片尾并立即结束。

建议位置落在最后几秒（或超过时长的 95%）时保存为 0。首页「最近播放」可以给这类条目显示「已看完」。

[读取续播位置](app/src/main/java/com/quadplayer/feature/player/PlayerViewModel.kt#L89-L91) · [保存位置](app/src/main/java/com/quadplayer/feature/player/PlayerViewModel.kt#L164-L176)

### 3.5 长按 2 倍速不会恢复，亮度调整不会还原（P2）

- **长按加速。** 长按画面把倍速设为 2.0x，松手后不恢复。界面上也没有当前倍速的标识，误触后用户不知道画面为什么变快。建议改为按住期间临时加速、松手恢复原倍速，并在画面上显示提示。
- **亮度。** 左侧上下滑动修改的是窗口亮度，离开播放页后没有还原，整个应用会一直保持调过的亮度。建议在播放页销毁时还原为系统默认值。

[长按回调](app/src/main/java/com/quadplayer/feature/player/PlayerScreen.kt#L194-L202) · [亮度调整](app/src/main/java/com/quadplayer/feature/player/PlayerScreen.kt#L595-L604)

### 3.6 外挂字幕的类型识别与自动加载（P2）

- **类型识别。** 字幕类型靠 URI 字符串的扩展名判断，识别不出就按 WebVTT 处理。从「下载」或网盘类来源选到的文件，URI 里通常没有扩展名，SRT 和 ASS 字幕会被当成 WebVTT 而解析失败。建议查询 `DISPLAY_NAME` 取文件名来判断。
- **同名字幕自动加载。** 这段逻辑依赖已废弃的 `DATA` 列和 `File.exists()`。Android 11 起，只有媒体读取权限的应用看不到 `.srt` 这类非媒体文件，新系统上基本不会命中（待真机确认）。建议删掉，或改为用户授权视频所在目录后再查找。

[字幕配置](app/src/main/java/com/quadplayer/feature/player/PlayerController.kt#L315-L326) · [同名字幕检测](app/src/main/java/com/quadplayer/feature/player/PlayerController.kt#L374-L383)

### 3.7 性能警告关不掉，温度读取基本无效（P2）

- **重复提示。** 监控每 2 秒检查一次，条件成立就再发一次警告，ViewModel 每次都重新写入提示。用户点了「关闭提示」，2 秒后又会出现。建议只在状态由正常转为异常时提示一次。
- **温度来源。** 温度读自 `/sys/class/thermal/thermal_zone0/temp`。这个节点在不同机型上含义不同，多数新系统上应用也读不到（待真机确认）。建议改用 `PowerManager` 的热状态接口，API 29 起可用。
- **内存阈值。** 低内存判断可以直接用 `MemoryInfo.lowMemory`，不必写死 200 MB。

[性能监控](app/src/main/java/com/quadplayer/core/util/PerformanceMonitor.kt#L29-L58) · [警告写入](app/src/main/java/com/quadplayer/feature/quadplayer/QuadPlayerViewModel.kt#L90-L98)

### 3.8 首页从播放页返回后回到第一个标签（P2）

`selectedTab` 用 `remember` 保存。进入播放页时首页离开组合，返回后状态重置为「媒体库」。从「最近播放」或「收藏」点进视频再返回，都会跳回第一个标签。改用 `rememberSaveable` 即可。

[首页标签状态](app/src/main/java/com/quadplayer/feature/home/HomeScreen.kt#L66)

### 3.9 四格选过的视频在媒体库里重复出现，且无法清理（P2）

四格通过系统文件选择器选片，拿到的是文档 URI，和媒体库扫描得到的 MediaStore URI 不同。同一个文件因此被写成两条记录，一条在原文件夹，一条在「手动导入」。手动导入的记录不参与扫描时的失效标记，文件删除后依然显示，界面上也没有移除入口。

另外，每次选片都会申请持久化 URI 权限，从不释放。系统对每个应用的持久授权数量有上限（旧版本 128 个，Android 11 起 512 个）。

建议导入时先用 `MediaStore.getMediaUri()`（API 29 起可用）换成媒体库 URI，复用已有记录。确实对应不上的才作为手动导入，并提供移除入口，移除时释放持久权限。

[四格选片](app/src/main/java/com/quadplayer/feature/quadplayer/QuadPlayerScreen.kt#L98-L109) · [导入逻辑](app/src/main/java/com/quadplayer/core/data/Repositories.kt#L104-L111) · [失效标记](app/src/main/java/com/quadplayer/core/database/Daos.kt#L57-L58)

## 4. 播放内核与性能

### 4.1 音频焦点、耳机拔出和屏幕常亮（P1）

构建 ExoPlayer 时没有设置音频属性。其他应用开始播放或来电时，QuadPlayer 不会自动暂停，可能同时出声。拔出耳机后声音会从扬声器外放。代码里也没有设置屏幕常亮，长视频播放中屏幕可能按系统超时熄灭（待真机确认）。

建议：

- 单视频用 `setAudioAttributes(…, handleAudioFocus = true)` 和 `setHandleAudioBecomingNoisy(true)`。
- 四格有四个播放器，不能各自申请焦点。由引擎统一申请一次，失去焦点或耳机拔出时全部暂停。
- 播放期间给播放视图设置 `keepScreenOn`，暂停后取消。

[播放器构建](app/src/main/java/com/quadplayer/feature/player/PlayerController.kt#L236-L245)

### 4.2 状态轮询让整屏持续重组（P2）

每个 `PlayerController` 每 300 ms 重建一次完整状态，包括遍历轨道并生成标签。单视频页整屏每秒重组约 3 次，四格页四路叠加后约 13 次，控制层隐藏或视频暂停时也不停。上一轮已提出这个问题，这里补充拆分方式：

- 轨道列表只在 `onTracksChanged` 时重建。
- 播放状态和倍速由监听器事件驱动。
- 进度单独放一个流，只在进度条可见且正在播放时轮询，由进度条组件自己订阅。

[轮询循环](app/src/main/java/com/quadplayer/feature/player/PlayerController.kt#L102-L110) · [状态重建](app/src/main/java/com/quadplayer/feature/player/PlayerController.kt#L328-L349) · [四格状态合并](app/src/main/java/com/quadplayer/feature/quadplayer/QuadPlaybackEngine.kt#L29-L49)

### 4.3 四格的缓冲内存（P2）

进入四格页就创建四个播放器，上一轮已建议按需创建。本轮补充一点：每个播放器使用默认的 `LoadControl`。默认值面向网络流，缓冲时长约 50 秒，单个播放器的缓冲上限在百兆字节量级（以所用 Media3 版本的默认值为准）。本地文件读取很快，不需要这么大的缓冲。

建议给四格配置较小的缓冲（例如 5–15 秒），然后实测内存，对照 PRD 的 800 MB 目标。

[四格控制器创建](app/src/main/java/com/quadplayer/feature/quadplayer/QuadPlaybackEngine.kt#L24) · [PRD 性能指标](QuadPlayer_PRD.md#L280-L292)

### 4.4 换位可以不重新加载（P2）

换位现在是清空两个播放器，各自重新加载并定位，画面会黑一下并重新缓冲。可以改为交换格子与控制器之间的对应关系：播放器本身不动，只把两个播放视图绑定的播放器对调。Media3 的 `PlayerView.switchTargetView` 就是为这种切换准备的。这样换位没有中断，进度和播放状态也不需要手动搬运。

[换位实现](app/src/main/java/com/quadplayer/feature/quadplayer/QuadPlaybackEngine.kt#L61-L81)

### 4.5 同步的做法（P2）

一键同步读取的是界面状态里的进度，最多滞后 300 ms。之后对正在播放的各格逐个定位，各格恢复播放的时刻不同，对齐后的误差取决于各自的定位耗时。建议：

- 读取音频源播放器的实时位置。
- 先全部暂停，逐格定位，等所有格子回到就绪状态后同时开始播放。
- 音频源自身不需要定位，避免声音卡顿。

这个改动不大，效果可以用上一轮建议的时间码素材测量。

[同步入口](app/src/main/java/com/quadplayer/feature/quadplayer/QuadPlayerViewModel.kt#L139-L144) · [引擎定位](app/src/main/java/com/quadplayer/feature/quadplayer/QuadPlaybackEngine.kt#L91-L97)

### 4.6 设备能力检测（P3）

硬解数量靠解码器名称是否包含 `sw` 判断，容易误判，统计的也是解码器条目数而不是并发路数。API 29 起可以用 `isHardwareAccelerated`，并发上限用 `getMaxSupportedInstances()`（上一轮已提）。检测目前在 ViewModel 初始化时跑在主线程，可以挪到后台线程。

[能力检测](app/src/main/java/com/quadplayer/core/util/DeviceCapabilityChecker.kt#L17-L41) · [调用位置](app/src/main/java/com/quadplayer/feature/quadplayer/QuadPlayerViewModel.kt#L81-L88)

### 4.7 媒体库刷新（P2）

- **重复触发。** 每次进入首页或文件夹页都会触发两次全量刷新：一次来自 `LaunchedEffect`，一次来自刚注册的生命周期观察者收到的 `ON_RESUME`。从播放页返回也一样。
- **逐条读写。** 每次刷新先把全部扫描记录标为不可访问，再逐条查询、逐条写回。N 个视频就是 N 次查询加 N 次写入。即使没有任何变化，`videos` 表也被整表改写，所有列表查询都会重新执行。

建议：

- 一次读出现有记录，在内存中比对，只写有变化的行。
- 用 `MediaStore.getGeneration()`（API 30 起可用）或 `ContentObserver` 判断媒体库是否变化，没变就跳过。
- 去掉重复的刷新入口，并设置最短刷新间隔。

[刷新触发](app/src/main/java/com/quadplayer/core/ui/MediaAccess.kt#L49-L59) · [刷新实现](app/src/main/java/com/quadplayer/core/data/Repositories.kt#L78-L102)

### 4.8 列表过滤与排序（P3）

文件夹页的过滤和排序在主线程执行，比较器每次比较都调用 `lowercase()` 生成新字符串。视频数量到千级后有卡顿风险。建议把计算放到 `Dispatchers.Default`，排序键预先算好。

排序目前按字符编码进行。中文文件名不按拼音排列，「第10集」会排在「第2集」前面。建议用 `Collator`，并加入自然数字排序。

[排序实现](app/src/main/java/com/quadplayer/feature/filebrowser/FileBrowserViewModel.kt#L89-L106) · [状态计算](app/src/main/java/com/quadplayer/feature/filebrowser/FileBrowserViewModel.kt#L144-L172)

### 4.9 启动与包体（P2）

- **R8。** 开启后，未使用的依赖和 `material-icons-extended` 里用不到的图标会被移除，安装包会明显变小（见 8.1）。
- **Logo。** 首页 Logo 是一张 940×982、约 0.9 MB 的 PNG，显示尺寸只有 56 dp。仓库根目录还有一份完全相同的 `logo.png`。建议应用内改用小尺寸 WebP 或矢量图。
- **Baseline Profile。** release 版本可以加入，用来改善冷启动和列表滑动。

[首页 Logo](app/src/main/java/com/quadplayer/feature/home/HomeScreen.kt#L100-L105)

## 5. 交互与界面

### 5.1 四格页

- **控制层挤占画面。** 控制层展开时，网格上方留出 92 dp，下方留出 124 dp。手机横屏的高度一般在 360–430 dp，画面区域只剩一半左右，视频还会跟着缩放（估算，待真机确认）。建议控制层浮在画面上方，不改变网格尺寸，并把两行按钮压成一行。
- **没有退出入口。** `onBack` 参数传入后没有被使用，只能靠系统返回手势退出。顶部的「关闭」按钮实际作用是收起控制层，容易误解。建议加一个明确的返回按钮，把「关闭」改成「收起」或换成图标。
- **只能用系统文件选择器选片。** 不能从应用内的媒体库、收藏或播放列表里选，也不能在媒体库里把视频发送到四格。这是四格模式和媒体库之间最大的断点，补上后还能顺带解决 3.9 的重复记录。
- **点按有延迟。** 格子同时注册了单击和双击，单击要等双击超时后才触发，点空格选片会慢约 0.3 秒。空格不需要双击，只注册单击即可。
- **音频源标识。** 说明文档写的是边框高亮，实际是左上角的文字标签。建议加边框，并提供可见的切换按钮（上一轮已提）。

[网格留白](app/src/main/java/com/quadplayer/feature/quadplayer/QuadPlayerScreen.kt#L116-L122) · [顶部按钮](app/src/main/java/com/quadplayer/feature/quadplayer/QuadPlayerScreen.kt#L229-L231) · [格子手势](app/src/main/java/com/quadplayer/feature/quadplayer/QuadPlayerScreen.kt#L420-L441) · [音频源标签](app/src/main/java/com/quadplayer/feature/quadplayer/QuadPlayerScreen.kt#L493-L507)

### 5.2 选中状态的表现不统一

同样是多选一，现在有三种表现：

- 设置页的主题、解码模式和默认倍速用「✓」前缀。
- 文件夹排序把当前选中项置灰禁用，视觉上像是不可用。
- 单视频倍速、四格倍速和设置里的双击秒数完全不显示当前值。

建议统一使用 `FilterChip` 的选中态。

[设置页选项](app/src/main/java/com/quadplayer/feature/settings/SettingsScreen.kt#L461-L481) · [双击秒数](app/src/main/java/com/quadplayer/feature/settings/SettingsScreen.kt#L98-L103) · [文件夹排序](app/src/main/java/com/quadplayer/feature/filebrowser/FileBrowserScreen.kt#L154-L167) · [单视频倍速](app/src/main/java/com/quadplayer/feature/player/PlayerScreen.kt#L361-L366) · [四格倍速](app/src/main/java/com/quadplayer/feature/quadplayer/QuadPlayerScreen.kt#L354-L362)

### 5.3 播放控件

- 单视频控制区全部是文字按钮（播放、暂停、返回），竖屏时倍速选项占一整行。建议换成图标按钮，倍速收进一个显示当前值的按钮。
- 横屏的「⋮」是写死的字符，没有无障碍描述。
- 播放出错时只显示「播放失败」。可以按 `PlaybackException` 的错误码区分文件不存在、格式不支持和没有权限，分别给出下一步操作。

[横屏控制区](app/src/main/java/com/quadplayer/feature/player/PlayerScreen.kt#L266-L300) · [竖屏控制区](app/src/main/java/com/quadplayer/feature/player/PlayerScreen.kt#L301-L377)

### 5.4 媒体库

- **没有缩略图。** 列表全是文字卡片。可以用 `ContentResolver.loadThumbnail`（API 29 起可用）或 Coil 的视频帧解码。Coil 已经在依赖里，但还没有使用。
- **播放列表管理不完整。** 仓库层有重命名和删除，界面没有入口，播放列表建了就删不掉。详情页标题固定是「播放列表」而不是列表名称。上移、下移、删除是塞在 48 dp 图标按钮里的文字。没有「播放全部」。
- **刷新没有反馈。** `isRefreshing` 已经算出来，界面没有使用。首页没有手动刷新入口，可以加下拉刷新。
- **历史记录不可管理。** 不能删除单条，也不能清空。
- **最近播放。** 只显示续播时间点，可以加进度条。

[首页列表](app/src/main/java/com/quadplayer/feature/home/HomeScreen.kt#L205-L279) · [播放列表详情](app/src/main/java/com/quadplayer/feature/playlist/PlaylistScreen.kt#L42-L85) · [重命名与删除](app/src/main/java/com/quadplayer/core/data/Repositories.kt#L172-L181) · [刷新状态](app/src/main/java/com/quadplayer/feature/home/HomeViewModel.kt#L72-L78)

### 5.5 大屏与横屏

媒体库页面在平板和横屏下仍是单列卡片。可以按窗口宽度切换为多列网格，底部导航换成侧边导航栏。

### 5.6 无障碍与多语言

- **无障碍。** 四格的双击切声和长按菜单没有等价的无障碍操作，可以用 `semantics` 的自定义操作补上。收藏按钮的描述不随收藏状态变化。进度条没有描述。
- **多语言。** 字符串只有中文一套。数据层和关于页还有写死的中文，包括默认播放列表名、「本地视频」、「手动导入」和整份关于内容。如果面向非中文用户，建议默认资源用英文，中文放进 `values-zh`。

[默认播放列表名](app/src/main/java/com/quadplayer/core/data/Repositories.kt#L165) · [扫描器中的文案](app/src/main/java/com/quadplayer/core/data/MediaStoreVideoScanner.kt#L59-L68) · [关于页内容](app/src/main/java/com/quadplayer/feature/settings/AboutSoftwareContent.kt)

## 6. 功能建议

四格模式的定位是素材对比和多机位查看，下表前半部分围绕这个场景，后半部分是单视频播放器的常见能力。

| 方向 | 建议 | 备注 |
| --- | --- | --- |
| 四格 | **偏移锁定与联动拖动。** 多机位素材的起点通常不同，把所有格子跳到同一时间点用处有限。建议记住各格之间的相对偏移，拖动任一格时其余格按相同增量移动 | 新增，价值最高 |
| 四格 | **循环、A-B 循环和逐帧步进。** 对比素材时最常用的三个操作 | 新增 |
| 四格 | 单格播放、暂停和倍速 | 上一轮已提 |
| 四格 | 会话保存与命名预设 | 上一轮已提 |
| 四格 | 1+3 焦点布局与单格放大 | 上一轮已提。`QuadLayoutManager` 目前只有一个取值 |
| 单视频 | **注册为系统的视频打开方式。** 清单里没有 `ACTION_VIEW` 过滤器，在文件管理器里无法选择用 QuadPlayer 打开 | 新增 |
| 单视频 | **字幕样式。** `SubtitleStyle` 已有存储，但没有设置界面，也没有应用到播放视图。要么补齐，要么删掉 | 新增 |
| 单视频 | 记住每个视频选过的音轨和字幕 | 新增 |
| 单视频 | 横向滑动快进快退、控件锁定 | 新增 |
| 单视频 | **画中画完善。** 设置宽高比，Android 12 起支持自动进入，小窗内提供播放和暂停按钮 | 新增 |
| 单视频 | 播放队列、上一条和下一条 | 上一轮已提 |
| 单视频 | 完整的后台播放 | 上一轮已提。`media3-session` 依赖已加入但未使用 |

[应用清单](app/src/main/AndroidManifest.xml#L18-L29) · [字幕样式存储](app/src/main/java/com/quadplayer/core/data/SettingsRepository.kt#L97-L103) · [画中画入口](app/src/main/java/com/quadplayer/feature/player/PlayerScreen.kt#L290-L295)

## 7. 架构与代码质量

- **文档描述与实际结构不符。** About 和技术文档写的是「Clean Architecture + 功能模块化」，实际是单模块，ViewModel 直接调用仓库。以现在的规模，这个结构是合适的。建议改文档，不必改代码。
- **播放器无法替换，难以测试。** `PlayerViewModel` 和 `QuadPlaybackEngine` 直接创建 `PlayerController`，后者内部直接构建 ExoPlayer。建议注入一个播放器工厂，四格的同步和换位逻辑就能用假播放器做单元测试。
- **ViewModel 里取字符串。** 三个 ViewModel 用 `context.getString` 生成提示文本，切换系统语言后不会更新，也让 ViewModel 依赖 Context。可以改为传资源 ID，由界面解析。
- **模型层依赖 Compose。** `SubtitleStyle` 的默认值用到 `androidx.compose.ui.graphics.Color`，可以改为 ARGB 常量。
- **叶子组件直接接收 ViewModel。** `QuadSlotContent`、`BottomOverlay` 和 `PlayerSurface` 接收整个 ViewModel，无法预览，也无法单独测试。`PlayerScreen` 主函数约 330 行，横竖屏两套控制区写在一起。建议拆成只接收状态和回调的小组件。
- **启动画面。** `MainActivity` 调用了 `installSplashScreen()`，但主题没有继承 `Theme.SplashScreen`。要么按库的要求配置启动主题，要么去掉这个依赖（待真机确认实际表现）。

**未使用的代码。** 建议删除，或按括号里的说明补齐：

- `QuadLayoutManager` 和只有一个取值的 `LayoutMode`
- `VideoRepository.search`、`VideoRepository.videosByFolder`、`VideoDao.upsertAll`、`VideoFile.toEntity`
- `PlaybackState.PAUSED`（从未产生）
- `HomeUiState.isRefreshing`（应接到界面，见 5.4）
- `renamePlaylist`、`deletePlaylist`（应补界面入口，见 5.4）
- `QuadPlayerScreen` 的 `onBack` 参数（应接到返回按钮，见 5.1）
- `SubtitleStyle` 相关设置（见第 6 节）

**数据层健壮性。**

- **DataStore。** 读取时没有处理 `IOException` 和文件损坏，枚举用 `valueOf` 还原，遇到未知值会崩溃。建议加 `catch` 和损坏处理器，枚举解析失败时回退到默认值。
- **播放历史。** `play_history.video_id` 没有唯一约束，靠先查后写来保证每个视频一条记录。建议加唯一索引，用一条 upsert 完成。
- **播放列表事务。** `removeVideo` 的删除、重排和更新时间不在同一个事务里。`createPlaylistFromCurrentView` 逐条调用 `addVideo`，每次都读取整个列表。建议提供批量添加，并放进事务。
- **失效记录。** 不可访问的视频记录永远不会删除。建议定期清理既不可访问、又没有收藏、历史和播放列表引用的记录。
- **协程作用域。** `PlayerController.release()` 只取消了轮询任务，没有取消自己的作用域。

[设置读取](app/src/main/java/com/quadplayer/core/data/SettingsRepository.kt#L47-L63) · [播放历史表](app/src/main/java/com/quadplayer/core/database/Entities.kt#L35-L52) · [播放列表写入](app/src/main/java/com/quadplayer/core/data/Repositories.kt#L183-L214) · [从当前视图生成播放列表](app/src/main/java/com/quadplayer/feature/filebrowser/FileBrowserViewModel.kt#L224-L239) · [控制器释放](app/src/main/java/com/quadplayer/feature/player/PlayerController.kt#L224-L228)

## 8. 构建、依赖与发布

### 8.1 发布包（P0）

README 的下载链接指向 `QPapp-debug.apk`。调试包有三个问题：

- **签名。** 调试包用调试密钥签名，而调试密钥是每台机器各自生成的。换一台机器构建，连下一个调试包都无法覆盖安装。以后换成正式签名时，已安装的用户必须先卸载，收藏、历史和播放列表都会丢失。这个代价随用户数增长，建议尽早固定一个正式签名并妥善保管。
- **性能。** 调试包可调试，没有经过 R8 优化。Compose 在调试包里明显更慢，播放器这类应用体感差异较大。
- **体积。** 调试包包含全部依赖的全部代码。

建议：

- 配置 release 签名。密钥通过环境变量或 CI 密钥注入，不放进仓库。
- 打开 `isMinifyEnabled` 和 `isShrinkResources`，补充必要的保留规则，然后做一轮完整回归。
- `versionCode` 目前是 1。建议和 Git 标签挂钩，每次发布递增。

[构建类型](app/build.gradle.kts#L25-L33) · [版本号](app/build.gradle.kts#L19-L20) · [README 下载链接](README.md#L5-L9)

### 8.2 未使用的依赖

- `coil-compose`、`accompanist-permissions` 和 `media3-session` 在源码中没有引用。
- 测试依赖里的 Robolectric、Turbine 和 `room-testing` 也还没有用到。按第 9 节补测试后会用上。
- `buildConfig = true` 没有对应的引用。minSdk 是 26，`vectorDrawables.useSupportLibrary` 不需要。

[依赖声明](app/build.gradle.kts#L67-L112)

### 8.3 依赖版本与管理

多数库停在 2024 年的版本：Compose BOM 2024.09、Media3 1.5.1、Kotlin 2.0.21、Hilt 2.52、Navigation 2.8.2，Room 2.6.1 更早。建议：

- 引入 `gradle/libs.versions.toml` 集中管理版本。技术文档里原本就是这样规划的。
- 配置 Renovate 或 Dependabot，自动提交升级 PR。
- Room 和 Hilt 从 kapt 迁到 KSP，构建会更快。
- 优先升级 Media3。新版本提供了拖动预览模式等和本项目直接相关的能力（以发行说明为准）。

### 8.4 仓库与构建配置

- **镜像顺序。** `settings.gradle.kts` 把腾讯云和阿里云镜像排在 `google()` 和 `mavenCentral()` 之前。国内构建更快，但海外 CI 会变慢或失败，依赖也会优先从第三方镜像解析。建议镜像改为按开关启用，并开启依赖校验（`verification-metadata.xml`）。
- **Gradle Wrapper。** 现在用的是 `-all` 包，没有 `distributionSha256Sum`。建议换成 `-bin` 并加上校验值。
- **构建内存。** `gradle.properties` 把堆内存设为 1 GB，工作线程限制为 2，开启 R8 后容易内存不足。建议提到 2 GB 以上，并开启构建缓存和配置缓存。如果限制是为低配开发机设的，可以放到个人的 `~/.gradle/gradle.properties`。
- **clean 任务。** 根目录 `build.gradle.kts` 手动注册的 `clean` 是旧模板遗留，可以删除。

[仓库配置](settings.gradle.kts#L1-L21) · [Gradle 属性](gradle.properties#L1-L6) · [Wrapper 配置](gradle/wrapper/gradle-wrapper.properties)

### 8.5 目标 API

compileSdk 和 targetSdk 都是 35。升到 36 时注意三点：

- 大屏设备会忽略应用的方向和尺寸限制。四格页的强制横屏在平板上不再生效，布局需要能适应竖屏。
- 预测性返回默认开启。
- 边到边显示不能再退出。

如果计划上架 Google Play，还需要核对当年的目标 API 要求。主题里的 `statusBarColor` 和 `navigationBarColor` 在 API 35 已废弃，可以删除。

[SDK 版本](app/build.gradle.kts#L13-L18) · [主题](app/src/main/res/values/themes.xml)

## 9. 测试、CI、安全与文档

### 9.1 测试与 CI

现状是 4 个单元测试文件（过滤排序、音频源切换、默认布局、历史写入队列）和 1 个设备端迁移测试。仓库没有 CI 配置。建议按收益排序推进：

1. **CI。** 用 GitHub Actions 在每个 PR 上运行 `assembleDebug`、`testDebugUnitTest` 和 `lintDebug`。打标签时构建签名的 release 包并上传到 Release。
2. **Lint 基线。** 上一轮记录有 53 个警告。先生成 baseline，把新增警告设为失败，再逐步清理存量。可以加 ktlint 或 detekt 统一代码风格。
3. **仓库层测试。** 用 Robolectric 加内存 Room 覆盖三类场景：刷新时保留收藏和手动导入标记、部分授权下的可见性、播放列表的增删与换序。
4. **四格引擎测试。** 引入播放器工厂后（见第 7 节），用假播放器覆盖换位、移除、全部播放与暂停、同步。
5. **本文缺陷的回归测试。** 音量累计、片尾续播归零、导入失败不崩溃。
6. **迁移测试。** 现在的测试手写了 v1 的建表语句。schema 已经导出，可以改用 `MigrationTestHelper` 直接从 `schemas/1.json` 建库，以后每次迁移加一个用例即可。
7. **界面测试。** Compose 测试依赖已加入但未使用。可以先覆盖权限引导、标签切换和播放列表增删。

[现有单元测试](app/src/test/java/com/quadplayer) · [迁移测试](app/src/androidTest/java/com/quadplayer/core/database/DatabaseMigrationTest.kt)

### 9.2 安全与隐私

- **保持无网络权限。** 清单里没有 `INTERNET` 权限，应用完全离线。这是优点，建议写进 README，以后引入任何联网依赖前先评估。
- **备份规则。** `allowBackup="true"`，没有配置备份规则。数据库里存的是本机的 MediaStore URI。恢复到另一台设备后，旧记录可能失效，也可能因为 ID 相同而对应到另一个视频，收藏和续播位置会错位。建议用 `dataExtractionRules` 只备份设置，或在恢复后首次启动时重建媒体库。
- **调试包对外分发。** 可调试的包允许通过 adb 读取应用的私有数据（见 8.1）。
- **持久化 URI 权限只增不减。** 见 3.9。
- **依赖来源。** 镜像优先且没有校验，见 8.4。

[应用清单](app/src/main/AndroidManifest.xml#L10-L17)

### 9.3 文档与仓库

**使用说明与实际行为不一致。** README、About.md 和应用内关于页有多处说法和代码对不上：

| 文档说法 | 实际行为 |
| --- | --- |
| 点按或单击画面切换音频源 | 双击才切换。单击是显示该格的进度条 |
| 被选中的画面边框高亮 | 左上角显示「当前音源」文字标签 |
| 点击画面上的关闭按钮可移除视频 | 没有关闭按钮。需要长按打开菜单再移除 |
| 长按视频可交换画面位置 | 长按打开菜单，选「开始交换」，再长按目标格，选「与当前选择交换」 |
| 每个画面可单独暂停 | 没有单格暂停 |
| 历史超出 100 条自动清理 | 只在查询时限制 100 条，没有清理逻辑 |
| Clean Architecture + 功能模块化；图片加载用 Coil | 单模块；Coil 未使用 |

应用内关于页的内容写死在 `AboutSoftwareContent.kt` 里，和 `About.md` 是两份独立的副本，加上 README 共三处说明，很容易继续走样。建议以一处为准。

其他建议：

- **技术文档已偏离实现。** `QuadPlayer_TechDoc.md` 有 2300 多行，描述的包结构和模块划分与现在的代码不同。建议标注为初始设计、仅供参考，或删减到与实现一致的部分。
- **文档归档。** 根目录有 6 个 Markdown 文件。建议 README 留在根目录，其余移入 `docs/`。
- **缺少 LICENSE。** 仓库提供了公开下载，但没有许可证文件，其他人无法确定能否使用或贡献。还应附上第三方依赖的许可声明。
- **缺少 CHANGELOG** 和发布说明。

[README](README.md) · [About](About.md#L70-L80) · [应用内关于页](app/src/main/java/com/quadplayer/feature/settings/AboutSoftwareContent.kt) · [技术文档](QuadPlayer_TechDoc.md)

## 10. 建议的推进顺序

1. **发布前。** 正式签名与 R8（8.1）；四个 P1 缺陷（3.1–3.4）；音频焦点与屏幕常亮（4.1）；最小的 CI（9.1 第 1 项）。
2. **体验。** 其余缺陷（3.5–3.9）；四格控制层、退出入口和应用内选片（5.1）；选中状态（5.2）；缩略图与播放列表管理（5.4）；纠正使用说明（9.3）。
3. **性能。** 轮询拆分（4.2）；按需创建与缓冲（4.3）；无中断换位（4.4）；齐播同步（4.5）；刷新去重（4.7）。配合上一轮建议的性能基线一起测量。
4. **功能。** 系统打开方式、偏移锁定与联动拖动、循环与逐帧、播放队列、会话保存、后台播放（第 6 节）。
