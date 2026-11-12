# QuadPlayer 开发审核与优化计划

审核日期为 2026 年 11 月 12 日，代码基线为 `58bbb7f`。本轮覆盖单视频与四格播放、媒体库、持久化和发布验证。建议下一版先修复单视频的横屏控制区和沉浸式全屏，再处理四格播放器的资源与状态问题。1+3 焦点布局和四格会话恢复可作为后续功能。 [产品需求](QuadPlayer_PRD.md) · [技术文档](QuadPlayer_TechDoc.md)

## 当前开发情况

| 模块 | 仓库现状 |
| --- | --- |
| 单视频 | 已接入本地播放、变速、比例切换、手势、音轨与字幕选择、播放记录和断点续播。画中画已有操作入口，仍需补齐 Activity 配置。 [播放器界面](app/src/main/java/com/quadplayer/feature/player/PlayerScreen.kt#L74-L347) · [播放状态与记录](app/src/main/java/com/quadplayer/feature/player/PlayerViewModel.kt#L71-L176) |
| 四格 | 已接入 2×2 布局、逐格选片和定位、换位、音频源切换与交叉淡化，以及全局启停、倍速和手动对齐。当前对齐会执行一次定位，持续播放中的偏移需要另行测量。 [四格界面](app/src/main/java/com/quadplayer/feature/quadplayer/QuadPlayerScreen.kt#L59-L199) · [四格状态与操作](app/src/main/java/com/quadplayer/feature/quadplayer/QuadPlayerViewModel.kt#L104-L168) · [播放引擎](app/src/main/java/com/quadplayer/feature/quadplayer/QuadPlaybackEngine.kt#L60-L109) |
| 媒体库与设置 | 已接入本地扫描、文件夹浏览、搜索排序、收藏、历史、播放列表和 DataStore 偏好。项目配置支持 API 26 起安装，低版本媒体库路径仍需兼容性回归。 [导航入口](app/src/main/java/com/quadplayer/navigation/QuadPlayerApp.kt#L47-L108) · [数据仓库](app/src/main/java/com/quadplayer/core/data/Repositories.kt) · [偏好设置](app/src/main/java/com/quadplayer/core/data/SettingsRepository.kt) · [构建配置](app/build.gradle.kts#L11-L23) |

## 首批修复项

### P1 单视频横屏进度区占用过大

- **复现结果**　在 Android 16 手机模拟器播放 15 秒本地视频，点击「横屏」，再点击画面展开控制区。横屏分辨率为 2400 × 1080 px，界面层级中进度滑块高 116 px，底部控制区高 621 px，约占屏高的 58%。
- **代码定位**　横竖屏共用全宽底部面板，内边距为 16 dp，行间距为 12 dp。标题与操作、默认样式的 `Slider`、时间标签和倍速控制分行排列。横屏可用高度减少后，这些元素叠加，占用较多画面空间。拖动时每次 `onValueChange` 都会触发定位，自动隐藏计时也未感知拖动状态。 [单视频控制区](app/src/main/java/com/quadplayer/feature/player/PlayerScreen.kt#L204-L305) · [自动隐藏逻辑](app/src/main/java/com/quadplayer/feature/player/PlayerScreen.kt#L136-L141)
- **设计建议**　按可用高度切换紧凑布局。横屏把时间、轨道和常用播放操作压为两行以内，倍速和画面比例收进更多菜单。进度轨道可先试 2–4 dp，交互区保留至少 48 dp 高，并支持 TalkBack 调整进度。拖动时显示本地预览时间并暂停自动隐藏，松手后提交一次定位，再恢复计时。四格逐格进度条已有松手提交逻辑，可复用其交互方式。 [四格定位实现](app/src/main/java/com/quadplayer/feature/quadplayer/QuadPlayerScreen.kt#L494-L510) · [Compose 无障碍触控尺寸](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)
- **验收**　用同一模拟器、默认字体与片源对比横屏截图，紧凑布局在两行内显示主要操作，建议控制区低于屏高的三分之一。在小屏横屏、反向横屏和大字体下检查时间与按钮的裁切、误触和可读性。连续拖动超过原有 3 秒计时仍能完成操作，松手只定位一次，旋转后保留当前播放位置与播放状态。

### P1 全屏时顶部状态栏仍可见

- **复现结果**　同一模拟器中，单视频切入横屏后，状态栏和导航栏均可见。对照进入四格页时两条系统栏均隐藏，返回后恢复可见。
- **代码定位**　单视频页的方向按钮只写入 `requestedOrientation`。`MainActivity.enableEdgeToEdge()` 让内容延伸至系统栏区域。仓库已有隐藏状态栏与导航栏的 `ImmersiveFullscreen()`，当前由四格页调用。单视频进入横屏的路径缺少隐藏系统栏的调用。若四格页也出现该现象，需记录发生在旋转、文件选择器返回或应用回到前台的哪个阶段，现有沉浸式逻辑只在进入组合时请求隐藏。 [单视频方向切换](app/src/main/java/com/quadplayer/feature/player/PlayerScreen.kt#L121-L156) · [方向按钮](app/src/main/java/com/quadplayer/feature/player/PlayerScreen.kt#L204-L222) · [Activity](app/src/main/java/com/quadplayer/MainActivity.kt#L18-L27) · [四格入口](app/src/main/java/com/quadplayer/feature/quadplayer/QuadPlayerScreen.kt#L59-L65) · [沉浸式实现](app/src/main/java/com/quadplayer/core/ui/OrientationLock.kt#L28-L60)
- **设计建议**　将沉浸状态与播放路由绑定，并单独管理屏幕方向。单视频进入横屏全屏及四格进入全屏时复用系统栏控制，通过 `WindowInsetsControllerCompat` 请求隐藏状态栏和导航栏。退出时恢复原有系统栏可见性、交互方式及方向设置。窗口恢复焦点时按当前路由恢复沉浸状态，保留边缘滑动临时唤出系统栏的行为。视频可铺满窗口，控件与错误提示按刘海和横屏两侧安全区排布。播放路由同时检查外层 `Scaffold` 的 `innerPadding`，避免视频画布沿用普通页面的系统栏留白。 [导航容器](app/src/main/java/com/quadplayer/navigation/QuadPlayerApp.kt#L47-L95) · [Android 沉浸模式指南](https://developer.android.com/develop/ui/views/layout/immersive)
- **验收**　单视频从竖屏进入横屏全屏后，两条系统栏默认隐藏。边缘滑动可短暂唤出，随后自动隐藏。切回竖屏或返回媒体库后系统栏正常恢复。四格进入和退出、左右横屏旋转、打开和关闭音轨或字幕弹窗、从系统文件选择器返回及切后台再回来均纳入回归。分别用手势导航、三键导航和带刘海的手机检查可见性与点击区域。

## 播放稳定性与兼容性

| 优先级 | 发现与建议 | 验收 |
| --- | --- | --- |
| P1 | **API 26–28 媒体库兼容。** 扫描查询无条件投影 API 29 才加入的 `RELATIVE_PATH`，旧设备可能在授权后查询失败。按版本构建投影，旧版本用可用的路径或媒体分组信息。查询异常与成功返回空结果应分别处理，避免误清缓存。 [扫描器](app/src/main/java/com/quadplayer/core/data/MediaStoreVideoScanner.kt#L20-L79) · [缓存刷新](app/src/main/java/com/quadplayer/core/data/Repositories.kt#L73-L81) · [Android MediaStore 列定义](https://developer.android.com/reference/android/provider/MediaStore.MediaColumns#RELATIVE_PATH) | API 26、28 和 29 均能扫描并按文件夹打开已有视频。模拟查询失败时保留原媒体库。 |
| P1 | **画中画入口会导致崩溃。** Android 16 模拟器点击「画中画」后应用退出，日志记录 `IllegalStateException`。单视频直接调用 `enterPictureInPictureMode()`，Activity 未声明 `supportsPictureInPicture`。补齐清单和设备能力判断，根据 PiP 状态收起控制层。 [入口](app/src/main/java/com/quadplayer/feature/player/PlayerScreen.kt#L296-L304) · [应用清单](app/src/main/AndroidManifest.xml#L17-L25) · [Android 画中画指南](https://developer.android.com/develop/ui/views/picture-in-picture) | 支持 PiP 的设备能进入、展开和关闭小窗。设备不支持时隐藏入口，点击路径无崩溃。 |
| P1 | **四格换位与移除。** 换位通过重新加载片源完成，`load()` 默认从零并开始播放。移除只暂停并回到零，播放器仍保留旧片源，而全局播放遍历所有控制器。应明确换位时片源、进度、播放状态和音频源的归属。清空格子时清除媒体项和相关状态，全局操作仅作用于有内容的格子。 [四格引擎](app/src/main/java/com/quadplayer/feature/quadplayer/QuadPlaybackEngine.kt#L49-L94) · [换位入口](app/src/main/java/com/quadplayer/feature/quadplayer/QuadPlayerViewModel.kt#L154-L168) · [播放器加载](app/src/main/java/com/quadplayer/feature/player/PlayerController.kt#L121-L133) | 用一段暂停、一段播放的素材在不同时间点换位。进度和播放状态随视频移动，音频源按既定规则更新。移除后再点全部播放，空格无画面、声音或后台解码。 |
| P2 | **Android 14 及以上的部分媒体授权。** 当前只检查整类视频的读取权限，Lint 已提示部分授权未处理。媒体库应支持只显示授权的视频，返回前台时复核权限，并区分授权范围缩小和文件实际删除，避免清除收藏或列表引用。 [应用权限](app/src/main/AndroidManifest.xml#L4-L7) · [缓存刷新](app/src/main/java/com/quadplayer/core/data/Repositories.kt#L73-L81) · [Android 部分媒体授权指南](https://developer.android.com/about/versions/14/changes/partial-photo-video-access) | 分别验证全量、部分和拒绝授权。修改授权范围后返回应用，列表仅显示可访问视频，历史和收藏可在恢复授权后继续使用。 |
| P2 | **播放失败与恢复。** `onPlayerError` 写入错误后，常规刷新会清空 `errorMessage`。四格状态也未传递逐格错误。保留错误至重试或切换媒体，提供当前格重试、更换文件或重新授权的入口。 [播放器错误与刷新](app/src/main/java/com/quadplayer/feature/player/PlayerController.kt#L73-L109) · [刷新写入](app/src/main/java/com/quadplayer/feature/player/PlayerController.kt#L306-L328) · [四格状态映射](app/src/main/java/com/quadplayer/feature/quadplayer/QuadPlaybackEngine.kt#L11-L47) | 无法读取或解码的片源显示可操作提示，四格其余视频可继续播放，替换成功后错误消失。 |
| P2 | **续播保存与数据库升级。** 播放进度每 5 秒写入，退出时再向即将清理的 `viewModelScope` 发起保存，最后一次写入有丢失风险。将暂停、离开等事件的保存交给可完成的写入任务并防止较早的快照覆盖最终记录。首次变更 Room 结构前，启用 schema 导出与显式迁移，覆盖收藏、历史和列表关系。 [历史保存](app/src/main/java/com/quadplayer/feature/player/PlayerViewModel.kt#L150-L176) · [Room 配置](app/src/main/java/com/quadplayer/core/database/QuadPlayerDatabase.kt#L6-L15) · [数据库初始化](app/src/main/java/com/quadplayer/di/AppModule.kt#L24-L32) | 进入后 5 秒内退出再打开，可从最近保存的位置续播。用含收藏、历史和列表的 v1 数据库升级，记录与顺序保留。 |

## 性能优化与测量

1. **按需创建与释放四格播放器。** 四格初始化即创建四个控制器，每个控制器每 300 ms 重建完整播放状态，包括音轨和字幕选项。建议按槽位装载创建播放器，清空或离开播放场景时按生命周期释放。轨道变化依赖事件，进度按需刷新，仅让相关进度控件订阅高频值。比较空四格、一路和四路的播放器实例数、主线程耗时、内存及首次显示时间。 [四格引擎](app/src/main/java/com/quadplayer/feature/quadplayer/QuadPlaybackEngine.kt#L20-L47) · [状态轮询](app/src/main/java/com/quadplayer/feature/player/PlayerController.kt#L102-L109) · [轨道状态构建](app/src/main/java/com/quadplayer/feature/player/PlayerController.kt#L306-L350)
2. **实测并发解码与降级。** 当前并发数由解码器条目数量推算，温度监测读取固定的系统文件。建议按实际片源的编解码类型、分辨率和帧率评估，使用 `getMaxSupportedInstances()` 作上限参考，结合解码初始化失败、掉帧和系统热状态提示。遇到单轨本地文件过重时，可以提示减少同时播放路数、暂停非焦点格或换低码率素材。 [设备能力](app/src/main/java/com/quadplayer/core/util/DeviceCapabilityChecker.kt#L17-L40) · [性能监测](app/src/main/java/com/quadplayer/core/util/PerformanceMonitor.kt#L29-L58) · [Android 解码并发能力](https://developer.android.com/reference/android/media/MediaCodecInfo.CodecCapabilities#getMaxSupportedInstances())
3. **测量并明确同步语义。** 一键同步取自音频源的界面进度快照，再逐格定位。先改用操作瞬间的播放器时钟，明确短视频到达末尾及暂停格的行为。用带时间码的同源素材记录对齐后、10 秒后和 60 秒后的画面时间差，再决定是否加入按偏差纠正的同步模式。 [同步入口](app/src/main/java/com/quadplayer/feature/quadplayer/QuadPlayerViewModel.kt#L139-L144) · [引擎定位](app/src/main/java/com/quadplayer/feature/quadplayer/QuadPlaybackEngine.kt#L88-L94)
4. **合并媒体库刷新。** 首页和文件夹页各有自动刷新入口，仓库每次扫描并批量比对 URI。可在仓库合并重叠刷新，按变化写入缓存。用千条级媒体库记录首次可见耗时、刷新次数及数据库写入量，并检查收藏和播放列表在刷新前后保持正确。 [首页刷新](app/src/main/java/com/quadplayer/feature/home/HomeScreen.kt#L70-L84) · [文件夹页刷新](app/src/main/java/com/quadplayer/feature/filebrowser/FileBrowserScreen.kt#L67-L81) · [缓存更新](app/src/main/java/com/quadplayer/core/data/Repositories.kt#L73-L81)

性能基线建议使用同一批本地素材和相同设备条件。先测单路与四路 1080p H.264 的首帧时间、每路掉帧和四路运行 10 分钟的内存峰值，再用 HEVC 与 4K 素材检查降级。记录机型、系统版本、片源参数和测量方法。可用 PRD 的单路启播 ≤ 500 ms、四路 1080p 加载 ≤ 3 s、四格内存 ≤ 800 MB 作首轮比较目标。 [PRD 性能指标](QuadPlayer_PRD.md#L280-L292)

## 功能与交互设计补充

| 顺序 | 功能 | 设计与验收 |
| --- | --- | --- |
| P2 | 四格独立播放控制与音频入口 | README 已描述逐格暂停和点按切声。当前菜单仅支持更换、交换和移除，实际切声手势为双击。长按菜单可补充逐格播放、暂停和速度。为音频源增加可见入口与选中状态，用户可以靠按钮或 TalkBack 完成切声、静音和独立暂停。 [README 功能说明](README.md#L11-L17) · [逐格手势](app/src/main/java/com/quadplayer/feature/quadplayer/QuadPlayerScreen.kt#L410-L441) · [菜单动作](app/src/main/java/com/quadplayer/feature/quadplayer/QuadPlayerScreen.kt#L518-L563) |
| P2 | 单视频播放队列 | 从播放列表进入时携带有序队列和当前项，补齐上一条、下一条、自动连播和单曲循环。单独打开视频时提供单项队列。验收列表中间项起播、末尾结束、失效文件及从历史恢复。 [播放列表入口](app/src/main/java/com/quadplayer/navigation/QuadPlayerApp.kt#L90-L106) · [单视频路由](app/src/main/java/com/quadplayer/navigation/Screen.kt) |
| P3 | 1+3 焦点布局与单格放大 | 先提供显式的放大和返回按钮，再扩展到平板及更大的横屏窗口。布局切换复用已装载的播放器，保留各格进度、播放状态和选中的音频源。聚焦后的可见控制与紧凑进度栏保持一致。 [布局状态](app/src/main/java/com/quadplayer/feature/quadplayer/QuadLayoutManager.kt#L8-L15) · [PRD 单格放大](QuadPlayer_PRD.md#L183-L188) |
| P3 | 四格会话恢复 | 复用 Room 持久化每格 URI、位置、倍速、布局和音频源。再次进入时由用户选择恢复，缺失或撤权素材仅影响对应格，可重新选片。先完成四格状态和数据库迁移，再引入会话保存。 [四格状态](app/src/main/java/com/quadplayer/feature/quadplayer/QuadPlayerViewModel.kt#L28-L72) · [四格选片](app/src/main/java/com/quadplayer/feature/quadplayer/QuadPlayerScreen.kt#L98-L109) |
| P3 | 完整的后台播放 | 目前设置仅决定 `ON_STOP` 时是否暂停。若要提供锁屏与通知栏控制，将单视频播放器交由 Media3 `MediaSessionService` 管理。验收锁屏、耳机媒体键、来电或音频焦点切换以及停止后资源释放。 [后台状态处理](app/src/main/java/com/quadplayer/feature/player/PlayerViewModel.kt#L150-L154) · [Media3 后台播放指南](https://developer.android.com/media/media3/session/background-playback) |

## 回归验证与交付顺序

- **本轮结果**　Debug APK 构建通过。重新运行的 9 项单元测试全部通过。Lint 为 0 个错误、53 个警告，包括部分媒体授权、依赖版本和未使用资源。Android 16 手机模拟器完成单视频横屏、四格系统栏和画中画入口复核。真机兼容性和四路解码性能留待设备验证。
- **现有覆盖**　仓库内有文件夹筛选排序、音频源切换和四格默认布局的单元测试。优先补充全屏进出及恢复、拖动只定位一次、空格不参与全局播放、换位保留进度、旧系统扫描和续播保存。 [现有测试](app/src/test/java/com/quadplayer)
- **设备矩阵**　用 API 26 或 28 检查最低版本与旧媒体库，API 29 检查新旧扫描分界，API 34 或 35 检查权限、画中画与系统栏。API 35 设备覆盖手势和三键导航、小屏与刘海。另用平板验证大窗口，四路解码与热稳定性用至少两款性能有差异的真机测量。
- **构建可移植性**　`gradle.properties` 固定了本机 Windows 的 JDK 路径。建议将机器路径移出版本控制，由开发环境或 CI 提供 JDK 17，把 Debug 构建、单元测试和 Lint 纳入每次提交的检查。 [Gradle 配置](gradle.properties#L1-L3)
- **交付顺序**　第一批交付横屏控制区、沉浸式全屏与设备回归。第二批处理媒体库兼容、画中画入口及四格资源和状态，并记录性能基线。后续按使用价值推进独立控制、播放队列、焦点布局与会话恢复，同时更新 README 的手势和功能说明。 [当前用户说明](README.md)
