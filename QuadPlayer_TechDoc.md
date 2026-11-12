# QuadPlayer — 安卓多视频同步播放器 技术开发文档

> **版本：** v1.0  
> **日期：** 2026-03-24  
> **目标读者：** AI 编码代理（Codex）/ 开发工程师  
> **关联文档：** QuadPlayer PRD v1.0

---

## 目录

1. [技术栈与开发环境](#1-技术栈与开发环境)
2. [项目工程结构](#2-项目工程结构)
3. [架构设计](#3-架构设计)
4. [数据模型与持久化](#4-数据模型与持久化)
5. [模块一：应用基础框架](#5-模块一应用基础框架)
6. [模块二：文件扫描与媒体库](#6-模块二文件扫描与媒体库)
7. [模块三：单视频播放器](#7-模块三单视频播放器)
8. [模块四：四宫格播放引擎（核心）](#8-模块四四宫格播放引擎核心)
9. [模块五：音频源管理器（核心）](#9-模块五音频源管理器核心)
10. [模块六：手势与交互系统](#10-模块六手势与交互系统)
11. [模块七：字幕系统](#11-模块七字幕系统)
12. [模块八：设置与偏好](#12-模块八设置与偏好)
13. [模块九：性能监控与降级策略](#13-模块九性能监控与降级策略)
14. [开发阶段与任务拆解](#14-开发阶段与任务拆解)
15. [测试策略](#15-测试策略)
16. [附录](#附录)

---

## 1. 技术栈与开发环境

### 1.1 核心技术栈

```
语言:           Kotlin 1.9+
最低 SDK:       API 26 (Android 8.0)
目标 SDK:       API 35 (Android 15)
构建工具:       Gradle 8.x + AGP 8.x + Version Catalog (libs.versions.toml)
UI 框架:        Jetpack Compose (BOM 2025.x)
播放引擎:       AndroidX Media3 ExoPlayer 1.5+
导航:           Compose Navigation
DI:             Hilt (Dagger)
数据库:         Room 2.6+
异步:           Kotlin Coroutines + Flow
图片加载:       Coil 3.x (Compose 原生)
权限:           Accompanist Permissions
```

### 1.2 关键依赖清单

```toml
# libs.versions.toml

[versions]
kotlin = "1.9.24"
agp = "8.5.0"
composeBom = "2025.03.00"
media3 = "1.5.1"
hilt = "2.51"
room = "2.6.1"
coroutines = "1.8.1"
coil = "3.0.4"
navigation = "2.8.0"

[libraries]
# Compose
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }

# Media3 ExoPlayer
media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
media3-ui = { group = "androidx.media3", name = "media3-ui", version.ref = "media3" }
media3-session = { group = "androidx.media3", name = "media3-session", version.ref = "media3" }
media3-common = { group = "androidx.media3", name = "media3-common", version.ref = "media3" }
media3-datasource = { group = "androidx.media3", name = "media3-datasource", version.ref = "media3" }

# Hilt DI
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version = "1.2.0" }

# Room
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

# Others
coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version = "1.1.1" }
```

### 1.3 开发环境要求

```
IDE:              Android Studio Ladybug (2024.2+)
JDK:              17
测试设备 (最低):   Pixel 6a / 同等中端设备, 6GB RAM
测试设备 (推荐):   Pixel 8 Pro / Samsung Galaxy S24, 8GB+ RAM
模拟器:           不推荐用于四宫格性能测试，必须真机
```

---

## 2. 项目工程结构

采用**按功能分包 (Package by Feature)** + **Clean Architecture 分层**的混合结构：

```
com.quadplayer.app/
│
├── QuadPlayerApplication.kt          # Application 入口, Hilt @HiltAndroidApp
├── MainActivity.kt                   # 单 Activity, Compose Host
│
├── di/                               # Hilt 依赖注入模块
│   ├── AppModule.kt                  # 全局单例: Database, DataStore, CoroutineScope
│   ├── PlayerModule.kt               # ExoPlayer 工厂和配置
│   └── RepositoryModule.kt           # Repository 绑定
│
├── core/                             # 基础工具和公共组件
│   ├── model/                        # 全局数据模型
│   │   ├── VideoFile.kt              # 视频文件实体
│   │   ├── PlaybackState.kt          # 播放状态枚举
│   │   ├── PlaybackSpeed.kt          # 倍速枚举
│   │   ├── LayoutMode.kt             # 布局模式枚举
│   │   └── AudioSourceState.kt       # 音频源状态
│   ├── database/                     # Room 数据库
│   │   ├── QuadPlayerDatabase.kt
│   │   ├── dao/
│   │   │   ├── VideoDao.kt
│   │   │   ├── PlayHistoryDao.kt
│   │   │   └── PlaylistDao.kt
│   │   └── entity/
│   │       ├── VideoEntity.kt
│   │       ├── PlayHistoryEntity.kt
│   │       └── PlaylistEntity.kt
│   ├── datastore/                    # DataStore 偏好
│   │   └── UserPreferences.kt
│   ├── util/                         # 工具类
│   │   ├── FileUtils.kt
│   │   ├── TimeUtils.kt
│   │   ├── DeviceCapabilityChecker.kt
│   │   └── PerformanceMonitor.kt
│   └── ui/                           # 公共 Compose 组件
│       ├── theme/
│       │   ├── Theme.kt
│       │   ├── Color.kt
│       │   └── Typography.kt
│       └── components/
│           ├── VideoThumbnail.kt
│           ├── SeekBar.kt
│           └── SpeedSelector.kt
│
├── navigation/                       # 导航图
│   ├── NavGraph.kt
│   └── Screen.kt                     # 路由定义 sealed class
│
├── feature/                          # 功能模块
│   ├── home/                         # 主页 (Tab: 最近/文件夹/收藏)
│   │   ├── HomeScreen.kt
│   │   ├── HomeViewModel.kt
│   │   └── components/
│   │       ├── VideoListItem.kt
│   │       ├── FolderListItem.kt
│   │       └── SearchBar.kt
│   │
│   ├── filebrowser/                  # 文件浏览器
│   │   ├── FileBrowserScreen.kt
│   │   ├── FileBrowserViewModel.kt
│   │   └── components/
│   │       └── FolderBreadcrumb.kt
│   │
│   ├── player/                       # 单视频播放器
│   │   ├── PlayerScreen.kt
│   │   ├── PlayerViewModel.kt
│   │   ├── PlayerController.kt       # ExoPlayer 封装
│   │   └── components/
│   │       ├── PlayerSurface.kt      # Compose 中嵌入 SurfaceView
│   │       ├── PlaybackControls.kt   # 底部控制栏
│   │       ├── GestureOverlay.kt     # 手势层
│   │       ├── TopBar.kt             # 顶部信息栏
│   │       └── SubtitleOverlay.kt    # 字幕渲染层
│   │
│   ├── quadplayer/                   # ★ 四宫格播放器 (核心特色)
│   │   ├── QuadPlayerScreen.kt
│   │   ├── QuadPlayerViewModel.kt
│   │   ├── QuadPlaybackEngine.kt     # 四路播放引擎管理
│   │   ├── AudioSourceManager.kt     # 音频源切换管理器
│   │   ├── QuadLayoutManager.kt      # 布局计算与切换
│   │   └── components/
│   │       ├── QuadGrid.kt           # 四宫格容器布局
│   │       ├── VideoCell.kt          # 单个宫格组件
│   │       ├── VideoCellOverlay.kt   # 宫格上层控制覆盖
│   │       ├── AudioIndicator.kt     # 音频源指示器 (高亮边框+图标)
│   │       ├── GlobalControlBar.kt   # 底部全局控制栏
│   │       ├── AddVideoButton.kt     # 空宫格 "+" 添加按钮
│   │       └── CellContextMenu.kt    # 宫格长按菜单
│   │
│   └── settings/                     # 设置页
│       ├── SettingsScreen.kt
│       └── SettingsViewModel.kt
│
└── service/                          # 后台服务
    ├── MediaScanService.kt           # 媒体文件扫描服务
    └── PlaybackService.kt            # 后台播放服务 (音频)
```

---

## 3. 架构设计

### 3.1 整体架构: MVVM + Clean Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                        UI Layer (Compose)                        │
│  Screen → 观察 ViewModel 的 StateFlow → 渲染 UI                  │
│  用户操作 → 调用 ViewModel 方法 → 触发业务逻辑                    │
└────────────────────────────┬─────────────────────────────────────┘
                             │
                    StateFlow / SharedFlow
                             │
┌────────────────────────────▼─────────────────────────────────────┐
│                     ViewModel Layer                              │
│  持有 UI State (data class)                                      │
│  协调 UseCase / Repository                                       │
│  管理 CoroutineScope                                             │
└────────────────────────────┬─────────────────────────────────────┘
                             │
┌────────────────────────────▼─────────────────────────────────────┐
│                     Domain / Engine Layer                         │
│  PlayerController — 封装单个 ExoPlayer 实例                       │
│  QuadPlaybackEngine — 管理四个 PlayerController 实例              │
│  AudioSourceManager — 控制音频源选择与切换逻辑                     │
│  QuadLayoutManager — 布局模式状态机                               │
└────────────────────────────┬─────────────────────────────────────┘
                             │
┌────────────────────────────▼─────────────────────────────────────┐
│                     Data Layer                                    │
│  Room Database (播放记录, 播放列表)                                │
│  DataStore (用户偏好设置)                                         │
│  MediaStore (系统媒体库查询)                                      │
│  FileSystem (文件系统遍历)                                        │
└──────────────────────────────────────────────────────────────────┘
```

### 3.2 四宫格播放引擎架构（核心）

```
QuadPlayerViewModel
    │
    ├── QuadPlaybackEngine (核心引擎)
    │       │
    │       ├── PlayerController[0]  ←→  ExoPlayer Instance 0  ←→  SurfaceView 0
    │       ├── PlayerController[1]  ←→  ExoPlayer Instance 1  ←→  SurfaceView 1
    │       ├── PlayerController[2]  ←→  ExoPlayer Instance 2  ←→  SurfaceView 2
    │       └── PlayerController[3]  ←→  ExoPlayer Instance 3  ←→  SurfaceView 3
    │
    ├── AudioSourceManager
    │       │
    │       ├── currentAudioSourceIndex: StateFlow<Int>
    │       ├── switchAudioSource(index: Int)
    │       └── crossfadeController  →  渐入渐出音量控制
    │
    └── QuadLayoutManager
            │
            ├── layoutMode: StateFlow<LayoutMode>  (EQUAL_GRID / FOCUS_1_PLUS_3)
            ├── focusIndex: StateFlow<Int?>
            └── switchLayout(mode, focusIndex)
```

### 3.3 状态管理设计

```kotlin
/**
 * 四宫格播放器的完整 UI 状态
 * ViewModel 通过 StateFlow<QuadPlayerUiState> 对外暴露
 */
data class QuadPlayerUiState(
    // 四个槽位的状态, null 表示该槽位为空
    val cells: List<CellState?> = listOf(null, null, null, null),

    // 当前音频源索引, -1 表示全部静音
    val audioSourceIndex: Int = -1,

    // 布局模式
    val layoutMode: LayoutMode = LayoutMode.EQUAL_GRID,

    // 焦点视频索引 (仅 FOCUS 模式下有效)
    val focusIndex: Int? = null,

    // 全局控制
    val isAllPlaying: Boolean = true,
    val globalSpeed: PlaybackSpeed = PlaybackSpeed.X1_0,
    val globalVolume: Float = 1.0f,

    // 设备性能
    val performanceWarning: PerformanceWarning? = null,

    // 控制栏可见性
    val isControlsVisible: Boolean = true
)

data class CellState(
    val index: Int,                          // 槽位索引 0-3
    val videoFile: VideoFile,                // 视频文件信息
    val playbackState: PlaybackState,        // IDLE / BUFFERING / PLAYING / PAUSED / ENDED
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: PlaybackSpeed = PlaybackSpeed.X1_0,
    val isAudioSource: Boolean = false,      // 是否为当前音频源
    val bufferPercentage: Int = 0
)

enum class LayoutMode {
    EQUAL_GRID,      // 2×2 均分
    FOCUS_1_PLUS_3   // 1大+3小
}

enum class PlaybackState {
    IDLE, BUFFERING, PLAYING, PAUSED, ENDED, ERROR
}

enum class PlaybackSpeed(val value: Float, val label: String) {
    X0_5(0.5f, "0.5x"),
    X0_75(0.75f, "0.75x"),
    X1_0(1.0f, "1.0x"),
    X1_25(1.25f, "1.25x"),
    X1_5(1.5f, "1.5x"),
    X2_0(2.0f, "2.0x"),
    X3_0(3.0f, "3.0x")
}
```

---

## 4. 数据模型与持久化

### 4.1 Room 数据库

```kotlin
@Database(
    entities = [VideoEntity::class, PlayHistoryEntity::class, PlaylistEntity::class, PlaylistVideoEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class QuadPlayerDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun playHistoryDao(): PlayHistoryDao
    abstract fun playlistDao(): PlaylistDao
}
```

#### 4.1.1 VideoEntity — 视频文件缓存

```kotlin
@Entity(tableName = "videos", indices = [Index(value = ["file_path"], unique = true)])
data class VideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "folder_path") val folderPath: String,
    @ColumnInfo(name = "folder_name") val folderName: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "file_size") val fileSize: Long,           // bytes
    @ColumnInfo(name = "width") val width: Int,
    @ColumnInfo(name = "height") val height: Int,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "date_added") val dateAdded: Long,         // epoch seconds
    @ColumnInfo(name = "date_modified") val dateModified: Long,
    @ColumnInfo(name = "thumbnail_path") val thumbnailPath: String? = null,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "media_store_id") val mediaStoreId: Long? = null
)
```

#### 4.1.2 PlayHistoryEntity — 播放记录

```kotlin
@Entity(
    tableName = "play_history",
    foreignKeys = [ForeignKey(
        entity = VideoEntity::class,
        parentColumns = ["id"],
        childColumns = ["video_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("video_id")]
)
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "video_id") val videoId: Long,
    @ColumnInfo(name = "last_position_ms") val lastPositionMs: Long,
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Long,   // epoch millis
    @ColumnInfo(name = "play_count") val playCount: Int = 1
)
```

#### 4.1.3 PlaylistEntity — 播放列表

```kotlin
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "playlist_videos",
    primaryKeys = ["playlist_id", "video_id"],
    foreignKeys = [
        ForeignKey(entity = PlaylistEntity::class, parentColumns = ["id"], childColumns = ["playlist_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = VideoEntity::class, parentColumns = ["id"], childColumns = ["video_id"], onDelete = ForeignKey.CASCADE)
    ]
)
data class PlaylistVideoEntity(
    @ColumnInfo(name = "playlist_id") val playlistId: Long,
    @ColumnInfo(name = "video_id") val videoId: Long,
    @ColumnInfo(name = "sort_order") val sortOrder: Int
)
```

#### 4.1.4 DAO 接口

```kotlin
@Dao
interface VideoDao {
    @Query("SELECT * FROM videos ORDER BY date_modified DESC")
    fun getAllVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE folder_path = :folderPath ORDER BY file_name ASC")
    fun getVideosByFolder(folderPath: String): Flow<List<VideoEntity>>

    @Query("SELECT DISTINCT folder_path, folder_name FROM videos ORDER BY folder_name ASC")
    fun getAllFolders(): Flow<List<FolderInfo>>  // FolderInfo 是一个自定义 data class

    @Query("SELECT * FROM videos WHERE file_name LIKE '%' || :keyword || '%' OR display_name LIKE '%' || :keyword || '%'")
    fun searchVideos(keyword: String): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE is_favorite = 1 ORDER BY file_name ASC")
    fun getFavorites(): Flow<List<VideoEntity>>

    @Upsert
    suspend fun upsertAll(videos: List<VideoEntity>)

    @Query("UPDATE videos SET is_favorite = :isFavorite WHERE id = :videoId")
    suspend fun setFavorite(videoId: Long, isFavorite: Boolean)

    @Query("DELETE FROM videos WHERE file_path NOT IN (:existingPaths)")
    suspend fun removeStaleEntries(existingPaths: List<String>)
}

@Dao
interface PlayHistoryDao {
    @Query("""
        SELECT ph.*, v.* FROM play_history ph 
        INNER JOIN videos v ON ph.video_id = v.id 
        ORDER BY ph.last_played_at DESC 
        LIMIT :limit
    """)
    fun getRecentHistory(limit: Int = 100): Flow<List<PlayHistoryWithVideo>>

    @Query("SELECT * FROM play_history WHERE video_id = :videoId LIMIT 1")
    suspend fun getHistoryForVideo(videoId: Long): PlayHistoryEntity?

    @Upsert
    suspend fun upsert(history: PlayHistoryEntity)
}
```

### 4.2 DataStore 用户偏好

```kotlin
/**
 * 使用 Jetpack DataStore Preferences 存储用户设置
 */
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    // Keys
    private object Keys {
        val DECODER_MODE = stringPreferencesKey("decoder_mode")             // "auto" | "hardware" | "software"
        val DEFAULT_SPEED = floatPreferencesKey("default_speed")            // 1.0f
        val DOUBLE_TAP_SEEK_SEC = intPreferencesKey("double_tap_seek_sec") // 10
        val BACKGROUND_PLAY = booleanPreferencesKey("background_play")     // false
        val AUTO_RESUME = booleanPreferencesKey("auto_resume")             // true
        val AUDIO_CROSSFADE = booleanPreferencesKey("audio_crossfade")     // true
        val CROSSFADE_DURATION_MS = intPreferencesKey("crossfade_ms")      // 150
        val THEME_MODE = stringPreferencesKey("theme_mode")                // "system" | "light" | "dark"
        val LANGUAGE = stringPreferencesKey("language")                     // "system" | "zh" | "en" | "ja"
    }

    // 以 Flow 形式暴露完整偏好对象
    val preferencesFlow: Flow<UserPreferences> = dataStore.data.map { prefs ->
        UserPreferences(
            decoderMode = DecoderMode.fromString(prefs[Keys.DECODER_MODE] ?: "auto"),
            defaultSpeed = prefs[Keys.DEFAULT_SPEED] ?: 1.0f,
            doubleTapSeekSec = prefs[Keys.DOUBLE_TAP_SEEK_SEC] ?: 10,
            backgroundPlay = prefs[Keys.BACKGROUND_PLAY] ?: false,
            autoResume = prefs[Keys.AUTO_RESUME] ?: true,
            audioCrossfade = prefs[Keys.AUDIO_CROSSFADE] ?: true,
            crossfadeDurationMs = prefs[Keys.CROSSFADE_DURATION_MS] ?: 150,
            themeMode = ThemeMode.fromString(prefs[Keys.THEME_MODE] ?: "system"),
            language = prefs[Keys.LANGUAGE] ?: "system"
        )
    }

    suspend fun <T> update(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }
}

data class UserPreferences(
    val decoderMode: DecoderMode = DecoderMode.AUTO,
    val defaultSpeed: Float = 1.0f,
    val doubleTapSeekSec: Int = 10,
    val backgroundPlay: Boolean = false,
    val autoResume: Boolean = true,
    val audioCrossfade: Boolean = true,
    val crossfadeDurationMs: Int = 150,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: String = "system"
)
```

---

## 5. 模块一：应用基础框架

### 5.1 开发步骤

```
步骤 5.1.1 — 创建项目
  ├── 使用 Android Studio 创建 Empty Compose Activity 项目
  ├── 包名: com.quadplayer.app
  ├── 最低 SDK: API 26
  └── 配置 libs.versions.toml 版本目录 (参见 1.2)

步骤 5.1.2 — 配置 Hilt
  ├── 添加 Hilt Gradle 插件 (id("com.google.dagger.hilt.android"))
  ├── 创建 QuadPlayerApplication.kt 并添加 @HiltAndroidApp
  ├── 在 AndroidManifest.xml 中注册 Application
  └── 创建 di/ 目录下三个 Module

步骤 5.1.3 — 配置 Compose 主题
  ├── 定义 Color.kt: 主色调 (#1E88E5 蓝), 音频源高亮色 (#42A5F5), 错误色 (#EF5350)
  ├── 定义 Typography.kt: 标题/正文/标签字体
  ├── 定义 Theme.kt: 浅色/深色 MaterialTheme
  └── 支持 Dynamic Color (Android 12+)

步骤 5.1.4 — 配置导航
  ├── 创建 Screen sealed class 定义所有路由
  ├── 创建 NavGraph.kt 配置导航图
  ├── MainActivity.kt 中设置 NavHost
  └── 底部导航栏: 最近播放 / 文件夹 / 四宫格 / 设置
```

### 5.2 Screen 路由定义

```kotlin
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object FileBrowser : Screen("file_browser/{folderPath}") {
        fun createRoute(folderPath: String) = "file_browser/${Uri.encode(folderPath)}"
    }
    object Player : Screen("player/{videoPath}") {
        fun createRoute(videoPath: String) = "player/${Uri.encode(videoPath)}"
    }
    object QuadPlayer : Screen("quad_player")
    object Settings : Screen("settings")
}
```

### 5.3 AndroidManifest 关键配置

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- 权限 -->
    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />           <!-- API 33+ -->
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />         <!-- API 33+ -->

    <application
        android:name=".QuadPlayerApplication"
        android:hardwareAccelerated="true"
        android:largeHeap="true"
        android:configChanges="orientation|screenSize|screenLayout|keyboardHidden"
        ...>

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:supportsPictureInPicture="true"
            android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden"
            android:launchMode="singleTask">

            <!-- 视频文件打开 Intent Filter -->
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="video/*" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

### 5.4 AppModule (Hilt)

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): QuadPlayerDatabase {
        return Room.databaseBuilder(
            context,
            QuadPlayerDatabase::class.java,
            "quadplayer.db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile("user_preferences")
        }
    }

    @Provides
    @Singleton
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher
```

---

## 6. 模块二：文件扫描与媒体库

### 6.1 开发步骤

```
步骤 6.1.1 — 实现 MediaStore 查询
  ├── 创建 MediaStoreVideoScanner.kt
  ├── 查询 MediaStore.Video.Media.EXTERNAL_CONTENT_URI
  ├── 提取: _ID, DATA, DISPLAY_NAME, DURATION, SIZE, WIDTH, HEIGHT, MIME_TYPE, DATE_ADDED, DATE_MODIFIED
  ├── 过滤掉时长 < 1000ms 的文件（避免图片误识别）
  └── API 29+ 使用 MediaStore.Video.Media.RELATIVE_PATH

步骤 6.1.2 — 同步到 Room
  ├── 将 MediaStore 查询结果转为 VideoEntity 列表
  ├── 使用 upsertAll 批量更新
  ├── 使用 removeStaleEntries 删除已不存在的文件记录
  └── 首次扫描在后台线程完成，扫描期间 UI 展示加载骨架屏

步骤 6.1.3 — 创建 VideoRepository
  ├── 封装 VideoDao 和 MediaStoreVideoScanner
  ├── 提供 Flow<List<VideoFile>> 给 ViewModel
  ├── 实现文件夹分组逻辑
  └── 实现搜索功能

步骤 6.1.4 — 缩略图生成
  ├── 使用 ContentResolver.loadThumbnail() (API 29+)
  ├── 低版本使用 MediaStore.Video.Thumbnails
  ├── 缓存缩略图到 App 专属目录
  └── Coil 加载时使用文件路径作为缓存 Key
```

### 6.2 MediaStoreVideoScanner 核心实现

```kotlin
class MediaStoreVideoScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun scanAllVideos(): List<VideoEntity> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<VideoEntity>()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED
        )

        val selection = "${MediaStore.Video.Media.DURATION} >= ?"
        val selectionArgs = arrayOf("1000") // 过滤 < 1s 的文件

        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"

        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection, selection, selectionArgs, sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val filePath = cursor.getString(pathCol) ?: continue
                val file = File(filePath)
                videos.add(
                    VideoEntity(
                        filePath = filePath,
                        fileName = cursor.getString(nameCol) ?: file.name,
                        displayName = file.nameWithoutExtension,
                        folderPath = file.parent ?: "",
                        folderName = file.parentFile?.name ?: "",
                        durationMs = cursor.getLong(durationCol),
                        fileSize = cursor.getLong(sizeCol),
                        width = cursor.getInt(widthCol),
                        height = cursor.getInt(heightCol),
                        mimeType = cursor.getString(mimeCol) ?: "video/*",
                        dateAdded = cursor.getLong(dateAddedCol),
                        dateModified = cursor.getLong(dateModifiedCol),
                        mediaStoreId = cursor.getLong(idCol)
                    )
                )
            }
        }
        videos
    }
}
```

---

## 7. 模块三：单视频播放器

### 7.1 开发步骤

```
步骤 7.1.1 — 创建 PlayerController (ExoPlayer 封装)
  ├── 封装单个 ExoPlayer 实例的完整生命周期
  ├── 暴露 StateFlow<PlayerState> 给上层观察
  ├── 方法: prepare / play / pause / seekTo / setSpeed / release
  ├── 实现 Player.Listener 回调 → 更新 StateFlow
  └── 支持配置解码器模式 (硬解/软解/自动)

步骤 7.1.2 — 创建 PlayerScreen Compose 界面
  ├── 全屏沉浸式布局 (隐藏系统栏)
  ├── 分层结构: SurfaceView(底) → GestureOverlay(中) → Controls(顶)
  ├── 横竖屏自适应
  └── 返回按钮 / 标题栏

步骤 7.1.3 — 创建 PlayerSurface 组件
  ├── 使用 AndroidView 在 Compose 中嵌入 SurfaceView
  ├── ExoPlayer.setVideoSurfaceView(surfaceView)
  ├── 根据视频宽高比动态计算 SurfaceView 尺寸
  └── 处理 Surface 创建/销毁生命周期

步骤 7.1.4 — 创建 PlaybackControls 控制栏
  ├── 播放/暂停按钮 (居中)
  ├── 上一个/下一个按钮
  ├── 进度条 (SeekBar): 当前时间 / 总时长
  ├── 倍速选择器按钮
  ├── 画面比例切换按钮
  ├── 画面旋转锁定按钮
  ├── 「进入四宫格」快捷按钮
  ├── 自动隐藏: 3s 无操作后隐藏, 点击屏幕显示
  └── 动画: 淡入淡出 (300ms)

步骤 7.1.5 — 创建 GestureOverlay 手势层
  ├── 亮度调节: 左半区垂直滑动
  ├── 音量调节: 右半区垂直滑动
  ├── 快退: 左半区双击 → 后退 N 秒
  ├── 快进: 右半区双击 → 前进 N 秒
  ├── 长按倍速: 长按期间 2x 播放, 松手恢复
  ├── 水平滑动: 拖动进度
  └── 锁定模式: 锁定后忽略所有手势

步骤 7.1.6 — 创建 PlayerViewModel
  ├── 持有 PlayerController 实例
  ├── 管理 PlayerUiState (播放状态/进度/控制栏可见性)
  ├── 接收 UserPreferences 应用默认设置
  ├── 保存播放进度到 PlayHistoryDao
  └── 处理播放列表导航逻辑
```

### 7.2 PlayerController 核心实现

```kotlin
/**
 * 封装单个 ExoPlayer 实例
 * 可复用于单视频模式和四宫格模式
 */
class PlayerController(
    private val context: Context,
    private val decoderMode: DecoderMode = DecoderMode.AUTO
) {
    private var _player: ExoPlayer? = null
    val player: ExoPlayer? get() = _player

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    // —— 位置更新定时器 ——
    private var positionUpdateJob: Job? = null

    data class PlayerState(
        val playbackState: PlaybackState = PlaybackState.IDLE,
        val isPlaying: Boolean = false,
        val currentPositionMs: Long = 0L,
        val durationMs: Long = 0L,
        val bufferedPercentage: Int = 0,
        val speed: Float = 1.0f,
        val videoWidth: Int = 0,
        val videoHeight: Int = 0,
        val errorMessage: String? = null
    )

    /**
     * 初始化 ExoPlayer 实例
     */
    fun initialize(scope: CoroutineScope) {
        if (_player != null) return

        val renderersFactory = when (decoderMode) {
            DecoderMode.HARDWARE -> DefaultRenderersFactory(context)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            DecoderMode.SOFTWARE -> DefaultRenderersFactory(context)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                .forceDisableMediaCodecAsynchronousQueueing()
            DecoderMode.AUTO -> DefaultRenderersFactory(context)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        }

        _player = ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        /* minBufferMs = */ 15_000,
                        /* maxBufferMs = */ 50_000,
                        /* bufferForPlaybackMs = */ 2_500,
                        /* bufferForPlaybackAfterRebufferMs = */ 5_000
                    )
                    .build()
            )
            .build()
            .also { exo ->
                exo.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        _state.update {
                            it.copy(playbackState = state.toPlaybackState())
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _state.update { it.copy(isPlaying = isPlaying) }
                    }

                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        _state.update {
                            it.copy(videoWidth = videoSize.width, videoHeight = videoSize.height)
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        _state.update {
                            it.copy(
                                playbackState = PlaybackState.ERROR,
                                errorMessage = error.localizedMessage
                            )
                        }
                    }
                })
            }

        // 启动位置更新轮询 (每 250ms)
        positionUpdateJob = scope.launch {
            while (isActive) {
                _player?.let { p ->
                    _state.update {
                        it.copy(
                            currentPositionMs = p.currentPosition,
                            durationMs = p.duration.coerceAtLeast(0),
                            bufferedPercentage = p.bufferedPercentage
                        )
                    }
                }
                delay(250)
            }
        }
    }

    fun prepare(uri: Uri, startPositionMs: Long = 0L) {
        _player?.let { p ->
            val mediaItem = MediaItem.fromUri(uri)
            p.setMediaItem(mediaItem)
            p.prepare()
            if (startPositionMs > 0) p.seekTo(startPositionMs)
            p.playWhenReady = true
        }
    }

    fun play() { _player?.play() }
    fun pause() { _player?.pause() }
    fun seekTo(positionMs: Long) { _player?.seekTo(positionMs) }

    fun setSpeed(speed: Float) {
        _player?.setPlaybackSpeed(speed)
        _state.update { it.copy(speed = speed) }
    }

    fun setVolume(volume: Float) {
        _player?.volume = volume.coerceIn(0f, 1f)
    }

    fun release() {
        positionUpdateJob?.cancel()
        _player?.release()
        _player = null
        _state.value = PlayerState()
    }

    private fun Int.toPlaybackState(): PlaybackState = when (this) {
        Player.STATE_IDLE -> PlaybackState.IDLE
        Player.STATE_BUFFERING -> PlaybackState.BUFFERING
        Player.STATE_READY -> PlaybackState.PLAYING
        Player.STATE_ENDED -> PlaybackState.ENDED
        else -> PlaybackState.IDLE
    }
}
```

### 7.3 PlayerSurface Compose 组件

```kotlin
/**
 * 在 Compose 中嵌入 SurfaceView 用于视频渲染
 * 被单视频模式和四宫格的每个 Cell 共同复用
 */
@Composable
fun PlayerSurface(
    player: ExoPlayer?,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            SurfaceView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { surfaceView ->
            player?.setVideoSurfaceView(surfaceView)
        },
        onRelease = { surfaceView ->
            player?.clearVideoSurfaceView(surfaceView)
        },
        modifier = modifier
    )
}
```

---

## 8. 模块四：四宫格播放引擎（核心）★

### 8.1 开发步骤

```
步骤 8.1.1 — 创建 QuadPlaybackEngine
  ├── 管理四个 PlayerController 实例的生命周期
  ├── 提供 addVideo / removeVideo / swapVideos 方法
  ├── 提供 playAll / pauseAll / syncPositions 全局控制
  ├── 每个 PlayerController 的 StateFlow 合并为统一 StateFlow
  └── 实现资源释放 (release) 确保所有实例被正确销毁

步骤 8.1.2 — 创建 QuadPlayerViewModel
  ├── 持有 QuadPlaybackEngine 和 AudioSourceManager 实例
  ├── 合并各模块 StateFlow → 统一 QuadPlayerUiState
  ├── 处理所有用户操作 (Intent / Action)
  ├── 管理生命周期 (onCleared 释放资源)
  └── 接收 UserPreferences 应用设置

步骤 8.1.3 — 创建 QuadPlayerScreen
  ├── 全屏沉浸式
  ├── 包含 QuadGrid + GlobalControlBar
  ├── 权限检查 + 设备能力检查
  └── 文件选择器 Intent 集成

步骤 8.1.4 — 创建 QuadGrid 布局组件
  ├── 使用 Compose Layout 或 BoxWithConstraints 实现自定义布局
  ├── EQUAL_GRID 模式: 2×2 等分
  ├── FOCUS_1_PLUS_3 模式: 上方 2/3 焦点 + 下方 1/3 三等分
  ├── 切换时执行 animateFloatAsState 过渡动画 (300ms)
  └── 横屏适配: 横屏时 2×2 仍等分, 1+3 变为左大右三竖排

步骤 8.1.5 — 创建 VideoCell 宫格组件
  ├── 包含: PlayerSurface + VideoCellOverlay + AudioIndicator
  ├── 空状态: 显示 AddVideoButton ("+" 按钮)
  ├── 加载状态: 显示 CircularProgressIndicator
  ├── 播放状态: 显示视频画面 + 半透明控制覆盖
  └── 错误状态: 显示错误信息 + 重试按钮

步骤 8.1.6 — 创建 GlobalControlBar 全局控制栏
  ├── 全部播放/暂停按钮
  ├── 同步进度按钮
  ├── 全局倍速选择器
  ├── 全局音量滑块
  ├── 全部静音按钮
  ├── 布局切换按钮 (等分 ↔ 焦点)
  └── 自动隐藏逻辑 (3s)
```

### 8.2 QuadPlaybackEngine 核心实现

```kotlin
/**
 * 四路视频播放引擎
 * 管理 4 个 PlayerController 实例
 */
class QuadPlaybackEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: UserPreferencesRepository
) {
    companion object {
        const val MAX_CELLS = 4
    }

    // 四个播放器实例 (可空 = 该槽位为空)
    private val controllers = arrayOfNulls<PlayerController>(MAX_CELLS)

    // 每个槽位的视频文件信息
    private val _cellVideos = MutableStateFlow<List<VideoFile?>>(listOf(null, null, null, null))
    val cellVideos: StateFlow<List<VideoFile?>> = _cellVideos.asStateFlow()

    // 各 PlayerController 状态的合并流
    private val _cellStates = MutableStateFlow<List<PlayerController.PlayerState?>>(
        listOf(null, null, null, null)
    )
    val cellStates: StateFlow<List<PlayerController.PlayerState?>> = _cellStates.asStateFlow()

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var stateCollectionJobs = arrayOfNulls<Job>(MAX_CELLS)

    /**
     * 在指定槽位加载视频
     */
    fun addVideo(index: Int, videoFile: VideoFile): Boolean {
        require(index in 0 until MAX_CELLS) { "Index must be 0-3" }

        // 释放该槽位旧的播放器
        removeVideo(index)

        // 获取用户偏好的解码器模式
        val decoderMode = runBlocking {
            preferencesRepository.preferencesFlow.first().decoderMode
        }

        val controller = PlayerController(context, decoderMode)
        controller.initialize(engineScope)
        controller.prepare(Uri.parse(videoFile.filePath))

        controllers[index] = controller

        // 更新视频文件状态
        _cellVideos.update { list ->
            list.toMutableList().also { it[index] = videoFile }
        }

        // 收集该 controller 的状态变化
        stateCollectionJobs[index] = engineScope.launch {
            controller.state.collect { playerState ->
                _cellStates.update { list ->
                    list.toMutableList().also { it[index] = playerState }
                }
            }
        }

        return true
    }

    /**
     * 移除指定槽位的视频
     */
    fun removeVideo(index: Int) {
        stateCollectionJobs[index]?.cancel()
        controllers[index]?.release()
        controllers[index] = null

        _cellVideos.update { list ->
            list.toMutableList().also { it[index] = null }
        }
        _cellStates.update { list ->
            list.toMutableList().also { it[index] = null }
        }
    }

    /**
     * 交换两个槽位的视频
     */
    fun swapVideos(indexA: Int, indexB: Int) {
        val tempController = controllers[indexA]
        controllers[indexA] = controllers[indexB]
        controllers[indexB] = tempController

        val tempJob = stateCollectionJobs[indexA]
        stateCollectionJobs[indexA] = stateCollectionJobs[indexB]
        stateCollectionJobs[indexB] = tempJob

        _cellVideos.update { list ->
            list.toMutableList().also {
                val temp = it[indexA]
                it[indexA] = it[indexB]
                it[indexB] = temp
            }
        }
    }

    /**
     * 获取指定槽位的 ExoPlayer 实例 (用于绑定 SurfaceView)
     */
    fun getPlayer(index: Int): ExoPlayer? = controllers[index]?.player

    // —— 全局控制 ——

    fun playAll() {
        controllers.forEach { it?.play() }
    }

    fun pauseAll() {
        controllers.forEach { it?.pause() }
    }

    fun setGlobalSpeed(speed: Float) {
        controllers.forEach { it?.setSpeed(speed) }
    }

    /**
     * 同步所有视频进度到参考视频的当前位置
     */
    fun syncPositions(referenceIndex: Int) {
        val refPosition = controllers[referenceIndex]?.player?.currentPosition ?: return
        controllers.forEachIndexed { i, ctrl ->
            if (i != referenceIndex) ctrl?.seekTo(refPosition)
        }
    }

    /**
     * 设置指定槽位的音量
     */
    fun setVolume(index: Int, volume: Float) {
        controllers[index]?.setVolume(volume)
    }

    fun getActiveCount(): Int = controllers.count { it != null }

    /**
     * 释放所有资源
     */
    fun releaseAll() {
        engineScope.cancel()
        controllers.forEachIndexed { i, _ -> removeVideo(i) }
    }
}
```

### 8.3 QuadGrid 布局组件

```kotlin
/**
 * 四宫格自定义布局
 * 支持 EQUAL_GRID 和 FOCUS_1_PLUS_3 两种模式之间的动画切换
 */
@Composable
fun QuadGrid(
    layoutMode: LayoutMode,
    focusIndex: Int?,
    cells: List<CellState?>,
    audioSourceIndex: Int,
    onCellClick: (Int) -> Unit,
    onCellDoubleClick: (Int) -> Unit,
    onCellLongClick: (Int) -> Unit,
    onAddVideo: (Int) -> Unit,
    getPlayer: (Int) -> ExoPlayer?,
    modifier: Modifier = Modifier
) {
    // 布局切换动画进度 (0f = EQUAL_GRID, 1f = FOCUS_1_PLUS_3)
    val focusProgress by animateFloatAsState(
        targetValue = if (layoutMode == LayoutMode.FOCUS_1_PLUS_3) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "layoutTransition"
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val totalWidth = constraints.maxWidth.toFloat()
        val totalHeight = constraints.maxHeight.toFloat()

        val gapPx = with(LocalDensity.current) { 2.dp.toPx() }

        for (i in 0 until 4) {
            val bounds = calculateCellBounds(
                index = i,
                focusIndex = focusIndex ?: 0,
                focusProgress = focusProgress,
                totalWidth = totalWidth,
                totalHeight = totalHeight,
                gap = gapPx
            )

            Box(
                modifier = Modifier
                    .offset { IntOffset(bounds.left.toInt(), bounds.top.toInt()) }
                    .size(
                        width = with(LocalDensity.current) { bounds.width.toDp() },
                        height = with(LocalDensity.current) { bounds.height.toDp() }
                    )
            ) {
                VideoCell(
                    index = i,
                    cellState = cells.getOrNull(i),
                    isAudioSource = (i == audioSourceIndex),
                    player = getPlayer(i),
                    onClick = { onCellClick(i) },
                    onDoubleClick = { onCellDoubleClick(i) },
                    onLongClick = { onCellLongClick(i) },
                    onAddVideo = { onAddVideo(i) }
                )
            }
        }
    }
}

/**
 * 计算每个宫格在两种布局间的插值位置
 */
private fun calculateCellBounds(
    index: Int,
    focusIndex: Int,
    focusProgress: Float,      // 0 = equal grid, 1 = focus mode
    totalWidth: Float,
    totalHeight: Float,
    gap: Float
): RectF {
    // == EQUAL_GRID 位置 ==
    val eqCol = index % 2
    val eqRow = index / 2
    val eqCellW = (totalWidth - gap) / 2f
    val eqCellH = (totalHeight - gap) / 2f
    val eqLeft = eqCol * (eqCellW + gap)
    val eqTop = eqRow * (eqCellH + gap)

    // == FOCUS_1_PLUS_3 位置 ==
    val (fLeft, fTop, fWidth, fHeight) = if (index == focusIndex) {
        // 焦点视频: 占据上方 65%
        arrayOf(0f, 0f, totalWidth, totalHeight * 0.65f - gap / 2)
    } else {
        // 非焦点视频: 下方 35% 平均分成 3 份
        val nonFocusIndices = (0 until 4).filter { it != focusIndex }
        val posInRow = nonFocusIndices.indexOf(index)
        val smallW = (totalWidth - gap * 2) / 3f
        val smallH = totalHeight * 0.35f - gap / 2
        val sLeft = posInRow * (smallW + gap)
        val sTop = totalHeight * 0.65f + gap / 2
        arrayOf(sLeft, sTop, smallW, smallH)
    }

    // 在两种布局间插值
    return RectF(
        lerp(eqLeft, fLeft, focusProgress),
        lerp(eqTop, fTop, focusProgress),
        lerp(eqLeft + eqCellW, fLeft + fWidth, focusProgress),
        lerp(eqTop + eqCellH, fTop + fHeight, focusProgress)
    )
}

private val RectF.width get() = right - left
private val RectF.height get() = bottom - top
private fun lerp(start: Float, end: Float, fraction: Float) = start + (end - start) * fraction
```

### 8.4 VideoCell 宫格组件

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoCell(
    index: Int,
    cellState: CellState?,
    isAudioSource: Boolean,
    player: ExoPlayer?,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onLongClick: () -> Unit,
    onAddVideo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val audioIndicatorColor by animateColorAsState(
        targetValue = if (isAudioSource) AudioSourceHighlight else Color.Transparent,
        animationSpec = tween(200),
        label = "audioHighlight"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black)
            .border(
                width = if (isAudioSource) 2.dp else 0.dp,
                color = audioIndicatorColor,
                shape = RoundedCornerShape(4.dp)
            )
    ) {
        when {
            // 空状态 → 显示添加按钮
            cellState == null -> {
                AddVideoButton(onClick = onAddVideo)
            }

            // 错误状态
            cellState.playbackState == PlaybackState.ERROR -> {
                ErrorCell(message = "播放出错", onRetry = onAddVideo)
            }

            // 正常播放/暂停/缓冲
            else -> {
                // 视频渲染层
                PlayerSurface(
                    player = player,
                    modifier = Modifier.fillMaxSize()
                )

                // 缓冲指示器
                if (cellState.playbackState == PlaybackState.BUFFERING) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(36.dp)
                            .align(Alignment.Center),
                        color = Color.White.copy(alpha = 0.8f),
                        strokeWidth = 3.dp
                    )
                }

                // 手势响应覆盖层
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .combinedClickable(
                            onClick = onClick,
                            onDoubleClick = onDoubleClick,
                            onLongClick = onLongClick,
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        )
                )

                // 音频源指示器
                AudioIndicator(
                    isAudioSource = isAudioSource,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                )

                // 简略进度条 (底部细线)
                if (cellState.durationMs > 0) {
                    LinearProgressIndicator(
                        progress = {
                            (cellState.currentPositionMs.toFloat() / cellState.durationMs).coerceIn(0f, 1f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .align(Alignment.BottomCenter),
                        color = if (isAudioSource) AudioSourceHighlight else Color.White.copy(alpha = 0.5f),
                        trackColor = Color.White.copy(alpha = 0.15f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioIndicator(isAudioSource: Boolean, modifier: Modifier = Modifier) {
    val alpha by animateFloatAsState(
        targetValue = if (isAudioSource) 1f else 0.4f,
        label = "audioAlpha"
    )
    Surface(
        modifier = modifier.size(28.dp),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.5f)
    ) {
        Icon(
            imageVector = if (isAudioSource) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
            contentDescription = if (isAudioSource) "音频播放中" else "已静音",
            tint = Color.White.copy(alpha = alpha),
            modifier = Modifier
                .padding(4.dp)
                .fillMaxSize()
        )
    }
}
```

---

## 9. 模块五：音频源管理器（核心）★

### 9.1 开发步骤

```
步骤 9.1.1 — 创建 AudioSourceManager
  ├── 管理当前音频源索引 (StateFlow<Int>)
  ├── switchTo(index) 方法: 切换音频源
  ├── muteAll() 方法: 全部静音
  ├── 集成 Crossfade 淡入淡出控制
  └── 绑定 QuadPlaybackEngine 控制各路音量

步骤 9.1.2 — 实现 Crossfade 控制器
  ├── 创建 CrossfadeController 类
  ├── 使用 ValueAnimator 或 Coroutine 实现音量渐变
  ├── 默认 Crossfade 时长: 150ms (可配置)
  ├── 渐出曲线: 当前源音量 1.0 → 0.0
  ├── 渐入曲线: 新源音量 0.0 → 1.0
  └── 支持开关 Crossfade (关闭时立即切换)

步骤 9.1.3 — 实现触觉反馈
  ├── 音频源切换时触发 HapticFeedbackConstants.CONFIRM
  └── 使用 Compose hapticFeedback API
```

### 9.2 AudioSourceManager 核心实现

```kotlin
class AudioSourceManager(
    private val engine: QuadPlaybackEngine,
    private val preferencesRepository: UserPreferencesRepository
) {
    private val _currentSource = MutableStateFlow(-1) // -1 = 未选择/全部静音
    val currentSource: StateFlow<Int> = _currentSource.asStateFlow()

    private val _isMutedAll = MutableStateFlow(false)
    val isMutedAll: StateFlow<Boolean> = _isMutedAll.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var crossfadeJob: Job? = null

    /**
     * 切换音频源到指定索引
     * 如果已经是当前音频源, 则不做操作
     */
    fun switchTo(index: Int) {
        if (index == _currentSource.value) return
        if (engine.getPlayer(index) == null) return // 该槽位为空

        val previousIndex = _currentSource.value
        _currentSource.value = index
        _isMutedAll.value = false

        crossfadeJob?.cancel()
        crossfadeJob = scope.launch {
            val prefs = preferencesRepository.preferencesFlow.first()

            if (prefs.audioCrossfade && previousIndex >= 0) {
                // —— 带 Crossfade 的切换 ——
                executeCrossfade(
                    fromIndex = previousIndex,
                    toIndex = index,
                    durationMs = prefs.crossfadeDurationMs.toLong()
                )
            } else {
                // —— 立即切换 ——
                if (previousIndex >= 0) engine.setVolume(previousIndex, 0f)
                engine.setVolume(index, 1f)
            }
        }
    }

    /**
     * 执行 Crossfade 音频渐变
     */
    private suspend fun executeCrossfade(
        fromIndex: Int,
        toIndex: Int,
        durationMs: Long
    ) {
        val steps = 15  // 渐变步数
        val stepDelay = durationMs / steps

        for (i in 0..steps) {
            val progress = i.toFloat() / steps
            // 使用 ease-out 曲线让切换更自然
            val easedProgress = 1f - (1f - progress).pow(2)

            engine.setVolume(fromIndex, 1f - easedProgress)
            engine.setVolume(toIndex, easedProgress)
            delay(stepDelay)
        }

        // 确保最终状态精确
        engine.setVolume(fromIndex, 0f)
        engine.setVolume(toIndex, 1f)
    }

    /**
     * 全部静音 / 取消全部静音
     */
    fun toggleMuteAll() {
        _isMutedAll.update { !it }
        if (_isMutedAll.value) {
            for (i in 0 until QuadPlaybackEngine.MAX_CELLS) {
                engine.setVolume(i, 0f)
            }
        } else {
            // 恢复当前音频源的音量
            val current = _currentSource.value
            if (current >= 0) engine.setVolume(current, 1f)
        }
    }

    /**
     * 当某个槽位的视频被移除时调用
     * 如果移除的是当前音频源, 自动切换到下一个可用槽位
     */
    fun onVideoRemoved(removedIndex: Int) {
        if (removedIndex != _currentSource.value) return

        // 寻找下一个可用的槽位
        val nextAvailable = (0 until QuadPlaybackEngine.MAX_CELLS)
            .filter { it != removedIndex && engine.getPlayer(it) != null }
            .firstOrNull()

        if (nextAvailable != null) {
            switchTo(nextAvailable)
        } else {
            _currentSource.value = -1
        }
    }

    fun release() {
        crossfadeJob?.cancel()
        scope.cancel()
    }
}
```

### 9.3 AudioFocus 系统级音频焦点管理

```kotlin
/**
 * 在 QuadPlayerViewModel 中管理系统音频焦点
 */
class AudioFocusHandler(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build()
        )
        .setOnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS -> onFocusLost()
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> onFocusLostTransient()
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onFocusDuck()
                AudioManager.AUDIOFOCUS_GAIN -> onFocusGained()
            }
        }
        .build()

    var onFocusLost: () -> Unit = {}
    var onFocusLostTransient: () -> Unit = {}
    var onFocusDuck: () -> Unit = {}
    var onFocusGained: () -> Unit = {}

    fun requestFocus(): Boolean {
        return audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    fun abandonFocus() {
        audioManager.abandonAudioFocusRequest(focusRequest)
    }
}
```

---

## 10. 模块六：手势与交互系统

### 10.1 开发步骤

```
步骤 10.1.1 — 单视频播放器手势
  ├── 创建 SinglePlayerGestureHandler
  ├── 实现左半区亮度滑动 (WindowManager.LayoutParams.screenBrightness)
  ├── 实现右半区音量滑动 (AudioManager.STREAM_MUSIC)
  ├── 实现双击快进/快退 (左/右半区)
  ├── 实现长按倍速播放 (按住 2x, 松手恢复)
  ├── 实现水平滑动拖进度
  └── 实现点击显示/隐藏控制栏

步骤 10.1.2 — 四宫格手势
  ├── 单击宫格 → AudioSourceManager.switchTo(index)
  ├── 双击宫格 → QuadLayoutManager.toggleFocus(index)
  ├── 长按宫格 → 显示上下文菜单 (移除/替换/倍速/信息)
  ├── 双指点击宫格 → 单独暂停/播放该宫格
  └── 双指捏合 → 全屏放大该宫格

步骤 10.1.3 — 自动隐藏控制栏逻辑
  ├── 任何用户交互重置 3s 计时器
  ├── 超时后控制栏淡出 (300ms)
  ├── 点击空白区域切换显示/隐藏
  └── 拖动进度条期间保持显示
```

### 10.2 四宫格手势处理

```kotlin
/**
 * 四宫格模式的手势检测器
 * 使用 Compose 的 pointerInput 实现多种手势
 */
@Composable
fun QuadCellGestureDetector(
    onSingleTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit,
    onTwoFingerTap: () -> Unit,
    onPinchToZoom: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var lastTapTime by remember { mutableLongStateOf(0L) }
    val doubleTapTimeout = 300L

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime < doubleTapTimeout) {
                            onDoubleTap()
                            lastTapTime = 0L
                        } else {
                            lastTapTime = now
                            // 延迟执行单击, 等待可能的双击
                            kotlinx.coroutines.MainScope().launch {
                                delay(doubleTapTimeout)
                                if (lastTapTime == now) onSingleTap()
                            }
                        }
                    },
                    onLongPress = { onLongPress() }
                )
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        // 检测两指点击
                        if (event.changes.size == 2 &&
                            event.type == PointerEventType.Press
                        ) {
                            onTwoFingerTap()
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    if (zoom < 0.8f) onPinchToZoom()
                }
            }
    ) {
        content()
    }
}
```

---

## 11. 模块七：字幕系统

### 11.1 开发步骤

```
步骤 11.1.1 — 外挂字幕加载
  ├── 视频加载时, 扫描同目录下同名的 .srt / .ass / .ssa / .vtt 文件
  ├── 使用 ExoPlayer 的 MediaItem.SubtitleConfiguration 添加字幕轨道
  ├── 提供字幕轨道列表给 UI 选择
  └── 支持手动选择任意路径的字幕文件

步骤 11.1.2 — 内嵌字幕
  ├── ExoPlayer 自动检测 MKV/MP4 内嵌字幕轨道
  ├── 通过 trackSelector.setParameters 切换字幕轨道
  └── 合并内嵌和外挂字幕为统一列表

步骤 11.1.3 — 字幕渲染
  ├── 使用 ExoPlayer SubtitleView (AndroidView 嵌入)
  ├── 字幕偏移调整 (时间提前/延后, 单位: 100ms 步进)
  └── 字幕样式: 字号 / 颜色 / 背景 / 描边

步骤 11.1.4 — 四宫格模式字幕
  ├── 仅当前音频源视频显示字幕
  ├── 切换音频源时同步切换字幕显示
  └── 焦点模式下字幕显示在焦点视频上
```

### 11.2 字幕加载实现

```kotlin
/**
 * 扫描并加载外挂字幕
 */
object SubtitleScanner {
    private val supportedExtensions = listOf("srt", "ass", "ssa", "vtt")

    fun findSubtitles(videoPath: String): List<SubtitleTrack> {
        val videoFile = File(videoPath)
        val videoName = videoFile.nameWithoutExtension
        val directory = videoFile.parentFile ?: return emptyList()

        return directory.listFiles()
            ?.filter { file ->
                val ext = file.extension.lowercase()
                ext in supportedExtensions &&
                    file.nameWithoutExtension.startsWith(videoName, ignoreCase = true)
            }
            ?.map { file ->
                SubtitleTrack(
                    label = extractLanguageLabel(file.nameWithoutExtension, videoName),
                    filePath = file.absolutePath,
                    mimeType = when (file.extension.lowercase()) {
                        "srt" -> MimeTypes.APPLICATION_SUBRIP
                        "ass", "ssa" -> MimeTypes.TEXT_SSA
                        "vtt" -> MimeTypes.TEXT_VTT
                        else -> MimeTypes.APPLICATION_SUBRIP
                    },
                    isExternal = true
                )
            }
            ?: emptyList()
    }

    /**
     * 构建带字幕的 MediaItem
     */
    fun buildMediaItemWithSubtitles(videoUri: Uri, subtitles: List<SubtitleTrack>): MediaItem {
        val builder = MediaItem.Builder().setUri(videoUri)

        val subtitleConfigs = subtitles.map { track ->
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(track.filePath))
                .setMimeType(track.mimeType)
                .setLabel(track.label)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
        }

        return builder.setSubtitleConfigurations(subtitleConfigs).build()
    }

    private fun extractLanguageLabel(subtitleName: String, videoName: String): String {
        val suffix = subtitleName.removePrefix(videoName).trimStart('.', '_', ' ')
        return suffix.ifBlank { "默认字幕" }
    }
}

data class SubtitleTrack(
    val label: String,
    val filePath: String,
    val mimeType: String,
    val isExternal: Boolean
)
```

---

## 12. 模块八：设置与偏好

### 12.1 开发步骤

```
步骤 12.1.1 — 创建 SettingsScreen
  ├── 使用 Compose LazyColumn 构建设置列表
  ├── 分组: 播放设置 / 四宫格设置 / 界面设置 / 关于
  ├── 每项设置读写 DataStore
  └── 实时预览效果 (如主题切换即时生效)

步骤 12.1.2 — 设置项组件封装
  ├── SwitchPreference: 开关型设置
  ├── SliderPreference: 滑块型设置
  ├── DropdownPreference: 下拉选择型设置
  └── ClickPreference: 点击跳转型设置

步骤 12.1.3 — 主题切换
  ├── 监听 themeMode 偏好变化
  ├── 在 Theme.kt 中根据偏好动态切换 MaterialTheme
  └── 支持 Android 12+ Dynamic Color
```

---

## 13. 模块九：性能监控与降级策略

### 13.1 开发步骤

```
步骤 13.1.1 — 创建 DeviceCapabilityChecker
  ├── 检测 RAM 总量 (ActivityManager.MemoryInfo)
  ├── 检测 MediaCodec 支持的并发解码器数量
  │   └── MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
  ├── 检测当前电池温度 (BatteryManager)
  ├── 进入四宫格前执行检测, 不通过则弹出警告 Dialog
  └── 返回 DeviceCapability 数据对象

步骤 13.1.2 — 创建 PerformanceMonitor
  ├── 后台协程每 2s 采集一次指标
  ├── 监控: 可用内存 / CPU 温度 / 渲染帧率 (Choreographer)
  ├── 当可用内存 < 200MB → 发出 WARNING
  ├── 当温度 > 42°C → 发出 THERMAL_WARNING
  └── 通过 SharedFlow<PerformanceWarning> 通知 ViewModel

步骤 13.1.3 — 实现降级策略
  ├── 收到 WARNING → 降低非焦点视频帧率至 24fps
  ├── 收到 CRITICAL → 弹出 Dialog 建议关闭部分视频
  ├── 降帧策略: ExoPlayer setVideoFrameMetadataListener 跳帧
  └── 降分辨率策略: trackSelector 限制最大分辨率
```

### 13.2 DeviceCapabilityChecker 实现

```kotlin
class DeviceCapabilityChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class DeviceCapability(
        val totalRamMb: Long,
        val availableRamMb: Long,
        val maxConcurrentCodecs: Int,
        val supportsHEVC: Boolean,
        val supportsAV1: Boolean,
        val warning: CapabilityWarning?
    )

    enum class CapabilityWarning {
        LOW_RAM,               // RAM < 4GB
        INSUFFICIENT_CODECS,   // 并发解码器 < 4
        BOTH
    }

    fun check(): DeviceCapability {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }

        val totalRamMb = memInfo.totalMem / (1024 * 1024)
        val availableRamMb = memInfo.availMem / (1024 * 1024)

        // 检测并发解码器数量
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        val videoDecoders = codecList.codecInfos
            .filter { !it.isEncoder }
            .filter { info -> info.supportedTypes.any { it.startsWith("video/") } }

        val maxConcurrent = estimateMaxConcurrentDecoders(videoDecoders)

        val supportsHEVC = videoDecoders.any { info ->
            info.supportedTypes.any { it.equals("video/hevc", ignoreCase = true) }
        }
        val supportsAV1 = videoDecoders.any { info ->
            info.supportedTypes.any { it.equals("video/av01", ignoreCase = true) }
        }

        val warning = when {
            totalRamMb < 4096 && maxConcurrent < 4 -> CapabilityWarning.BOTH
            totalRamMb < 4096 -> CapabilityWarning.LOW_RAM
            maxConcurrent < 4 -> CapabilityWarning.INSUFFICIENT_CODECS
            else -> null
        }

        return DeviceCapability(
            totalRamMb = totalRamMb,
            availableRamMb = availableRamMb,
            maxConcurrentCodecs = maxConcurrent,
            supportsHEVC = supportsHEVC,
            supportsAV1 = supportsAV1,
            warning = warning
        )
    }

    private fun estimateMaxConcurrentDecoders(decoders: List<MediaCodecInfo>): Int {
        // 硬件解码器通常限制并发数, 这里采用保守估计
        val hwDecoders = decoders.filter { !it.name.contains("sw", ignoreCase = true) }
        // 大多数现代 SoC 支持 4 路并发 1080p 解码
        // 实际数量需要运行时测试, 这里返回一个估计值
        return if (hwDecoders.size >= 4) 4 else hwDecoders.size
    }
}
```

### 13.3 PerformanceMonitor 实现

```kotlin
class PerformanceMonitor {

    data class Metrics(
        val availableMemoryMb: Long,
        val cpuTemperature: Float?,
        val droppedFrames: Int
    )

    sealed class PerformanceWarning {
        object LowMemory : PerformanceWarning()
        object ThermalThrottle : PerformanceWarning()
        object HighDroppedFrames : PerformanceWarning()
    }

    private val _warnings = MutableSharedFlow<PerformanceWarning>(extraBufferCapacity = 5)
    val warnings: SharedFlow<PerformanceWarning> = _warnings.asSharedFlow()

    private var monitorJob: Job? = null

    fun startMonitoring(context: Context, scope: CoroutineScope) {
        monitorJob = scope.launch(Dispatchers.Default) {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

            while (isActive) {
                val memInfo = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
                val availMb = memInfo.availMem / (1024 * 1024)

                if (availMb < 200) {
                    _warnings.tryEmit(PerformanceWarning.LowMemory)
                }

                // CPU 温度 (需要读取 thermal zone, 非所有设备支持)
                val temp = readCpuTemperature()
                if (temp != null && temp > 42f) {
                    _warnings.tryEmit(PerformanceWarning.ThermalThrottle)
                }

                delay(2000)
            }
        }
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
    }

    private fun readCpuTemperature(): Float? {
        return try {
            val thermalFile = File("/sys/class/thermal/thermal_zone0/temp")
            if (thermalFile.exists()) {
                thermalFile.readText().trim().toFloatOrNull()?.let { it / 1000f }
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
```

---

## 14. 开发阶段与任务拆解

### 阶段一：项目基础 (预计 3 天)

```
任务 ID | 任务描述                              | 依赖 | 产出文件
--------|---------------------------------------|------|------------------------------------------
T-1.1   | 创建项目, 配置 Gradle 和依赖            | 无   | build.gradle.kts, libs.versions.toml
T-1.2   | 配置 Hilt DI 框架                      | T-1.1| QuadPlayerApplication.kt, di/*.kt
T-1.3   | 创建 Room 数据库和全部 Entity/DAO         | T-1.1| core/database/*.kt
T-1.4   | 创建 DataStore 偏好存储                  | T-1.1| core/datastore/UserPreferences.kt
T-1.5   | 配置 Compose 主题 (浅色/深色)            | T-1.1| core/ui/theme/*.kt
T-1.6   | 配置导航图和底部导航栏                    | T-1.5| navigation/*.kt, MainActivity.kt
T-1.7   | 创建 AndroidManifest 配置               | T-1.1| AndroidManifest.xml
```

### 阶段二：文件管理与媒体库 (预计 3 天)

```
任务 ID | 任务描述                              | 依赖 | 产出文件
--------|---------------------------------------|------|------------------------------------------
T-2.1   | 实现 MediaStoreVideoScanner            | T-1.3| MediaStoreVideoScanner.kt
T-2.2   | 实现 VideoRepository                   | T-2.1| VideoRepository.kt
T-2.3   | 创建 HomeScreen (最近播放/文件夹 Tab)    | T-1.6| feature/home/*.kt
T-2.4   | 创建 HomeViewModel                     | T-2.2| HomeViewModel.kt
T-2.5   | 创建视频列表项组件 (缩略图+信息)          | T-2.3| components/VideoListItem.kt
T-2.6   | 创建 FileBrowserScreen (文件夹浏览)      | T-2.2| feature/filebrowser/*.kt
T-2.7   | 实现搜索功能                            | T-2.4| SearchBar.kt
T-2.8   | 实现收藏功能                            | T-2.2| VideoDao.setFavorite
```

### 阶段三：单视频播放器 (预计 5 天)

```
任务 ID | 任务描述                              | 依赖 | 产出文件
--------|---------------------------------------|------|------------------------------------------
T-3.1   | 创建 PlayerController (ExoPlayer 封装)  | T-1.2| PlayerController.kt
T-3.2   | 创建 PlayerSurface Compose 组件         | T-3.1| components/PlayerSurface.kt
T-3.3   | 创建 PlayerScreen 全屏布局              | T-3.2| PlayerScreen.kt
T-3.4   | 创建 PlaybackControls 底部控制栏        | T-3.3| components/PlaybackControls.kt
T-3.5   | 实现进度条 (SeekBar)                    | T-3.4| components/SeekBar.kt
T-3.6   | 实现倍速选择器                          | T-3.4| components/SpeedSelector.kt
T-3.7   | 创建 GestureOverlay 手势层              | T-3.3| components/GestureOverlay.kt
T-3.8   | 实现亮度和音量滑动手势                   | T-3.7| SinglePlayerGestureHandler.kt
T-3.9   | 实现双击快进快退                        | T-3.7| (同上)
T-3.10  | 实现长按倍速                            | T-3.7| (同上)
T-3.11  | 创建 PlayerViewModel                   | T-3.1| PlayerViewModel.kt
T-3.12  | 实现播放记录保存与续播                   | T-3.11| PlayHistoryDao
T-3.13  | 实现控制栏自动隐藏 (3s)                 | T-3.4| (ViewModel 逻辑)
T-3.14  | 实现画面比例切换                        | T-3.3| (PlayerController 扩展)
T-3.15  | 实现画面旋转锁定                        | T-3.3| (Activity 方向控制)
```

### 阶段四：四宫格播放引擎 ★ (预计 8 天)

```
任务 ID | 任务描述                              | 依赖 | 产出文件
--------|---------------------------------------|------|------------------------------------------
T-4.1   | 创建 QuadPlaybackEngine                | T-3.1| QuadPlaybackEngine.kt
T-4.2   | 创建 AudioSourceManager                | T-4.1| AudioSourceManager.kt
T-4.3   | 实现 Crossfade 音频渐变控制器            | T-4.2| (AudioSourceManager 内部)
T-4.4   | 创建 AudioFocusHandler                 | T-4.2| AudioFocusHandler.kt
T-4.5   | 创建 QuadLayoutManager                 | 无   | QuadLayoutManager.kt
T-4.6   | 创建 QuadPlayerScreen                  | T-4.1| QuadPlayerScreen.kt
T-4.7   | 创建 QuadGrid 自定义布局组件             | T-4.5| components/QuadGrid.kt
T-4.8   | 实现 EQUAL_GRID ↔ FOCUS_1_PLUS_3 动画  | T-4.7| (QuadGrid 内部)
T-4.9   | 创建 VideoCell 宫格组件                 | T-4.7| components/VideoCell.kt
T-4.10  | 创建 AudioIndicator 音频指示器组件       | T-4.9| components/AudioIndicator.kt
T-4.11  | 创建 AddVideoButton 空宫格组件          | T-4.9| components/AddVideoButton.kt
T-4.12  | 创建 GlobalControlBar 全局控制栏        | T-4.6| components/GlobalControlBar.kt
T-4.13  | 实现四宫格手势系统                      | T-4.9| QuadCellGestureDetector.kt
T-4.14  | 实现单击切换音频源 + 触觉反馈            | T-4.13| (ViewModel 联动)
T-4.15  | 实现双击切换焦点布局                    | T-4.13| (ViewModel 联动)
T-4.16  | 实现长按上下文菜单                      | T-4.13| components/CellContextMenu.kt
T-4.17  | 实现全部播放/暂停                       | T-4.12| (Engine 联动)
T-4.18  | 实现同步进度                            | T-4.12| (Engine 联动)
T-4.19  | 实现全局倍速                            | T-4.12| (Engine 联动)
T-4.20  | 创建 QuadPlayerViewModel               | T-4.1-19| QuadPlayerViewModel.kt
T-4.21  | 实现文件选择器集成 (SAF)                 | T-4.6| (Intent 调用)
T-4.22  | 实现宫格视频拖拽交换                    | T-4.9| (Compose DnD)
T-4.23  | 实现设备能力检测入口                    | T-4.6| (进入前检测)
```

### 阶段五：字幕与高级功能 (预计 3 天)

```
任务 ID | 任务描述                              | 依赖  | 产出文件
--------|---------------------------------------|-------|------------------------------------------
T-5.1   | 实现外挂字幕扫描与加载                  | T-3.1 | SubtitleScanner.kt
T-5.2   | 实现字幕轨道切换 UI                    | T-5.1 | SubtitleSelector.kt
T-5.3   | 实现字幕样式自定义                      | T-5.2 | SubtitleStyleConfig.kt
T-5.4   | 实现四宫格模式字幕同步显示               | T-4.20| (ViewModel 联动)
T-5.5   | 实现播放列表管理                        | T-2.2 | PlaylistScreen.kt
T-5.6   | 实现多音轨切换                          | T-3.1 | AudioTrackSelector.kt
```

### 阶段六：设置、性能与打磨 (预计 3 天)

```
任务 ID | 任务描述                              | 依赖  | 产出文件
--------|---------------------------------------|-------|------------------------------------------
T-6.1   | 创建 SettingsScreen                    | T-1.4 | feature/settings/*.kt
T-6.2   | 实现 DeviceCapabilityChecker           | 无    | DeviceCapabilityChecker.kt
T-6.3   | 实现 PerformanceMonitor                | 无    | PerformanceMonitor.kt
T-6.4   | 实现性能降级策略                        | T-6.3 | (ViewModel 联动)
T-6.5   | 横竖屏 / 平板适配测试与修复              | 全部  | 布局调整
T-6.6   | 内存泄漏检查 (LeakCanary)               | 全部  | (debug 依赖)
T-6.7   | 全流程 UI 走查与动画打磨                 | 全部  | 细节调整
T-6.8   | ProGuard / R8 混淆规则配置              | 全部  | proguard-rules.pro
```

### 总计预估工期

```
阶段一: 项目基础        →  3 天
阶段二: 文件管理        →  3 天
阶段三: 单视频播放器    →  5 天
阶段四: 四宫格引擎 ★   →  8 天
阶段五: 字幕与高级功能  →  3 天
阶段六: 设置与打磨      →  3 天
──────────────────────────────
总计                    → 25 天 (约 5 周)
```

---

## 15. 测试策略

### 15.1 单元测试

```
测试目标                        | 测试框架      | 关注点
-------------------------------|--------------|----------------------------------------
PlayerController               | JUnit 5      | 状态转换正确性, setVolume 边界值
AudioSourceManager             | JUnit 5 + Mock | crossfade 时序, 边界切换, 空槽位处理
QuadPlaybackEngine             | JUnit 5 + Mock | 添加/移除/交换, 全局控制, 资源释放
QuadLayoutManager              | JUnit 5      | 布局坐标计算, 插值正确性
SubtitleScanner                | JUnit 5      | 文件名匹配, 格式识别
DeviceCapabilityChecker        | Robolectric  | 各种设备条件下的 Warning 判定
UserPreferencesRepository      | JUnit 5      | 读写一致性, 默认值
VideoDao                       | Room Testing | CRUD, 搜索, 去重
```

### 15.2 集成测试

```
测试场景                                   | 验证内容
------------------------------------------|-------------------------------------------
启动 → 扫描 → 打开视频 → 播放              | 端到端播放流程正常
四宫格加载 4 个视频 → 音频切换 → 移除 → 添加 | 引擎生命周期完整
音频源切换 Crossfade                       | 渐变无爆音, 时序正确
进入四宫格 → 切换焦点布局 → 全屏 → 返回     | 布局切换动画, 播放不中断
后台 → 前台 → 播放器恢复                    | 生命周期处理, 续播正确
极端: 4 个 4K 视频                          | 降级策略触发, 不崩溃
```

### 15.3 性能测试

```
场景                          | 指标               | 通过标准
-----------------------------|-------------------|------------------
4×1080p H.264 同播           | 帧率               | ≥ 24fps (中端设备)
4×1080p H.264 同播           | 内存峰值            | ≤ 800MB
音频源切换                    | 切换延迟            | ≤ 200ms
冷启动                       | 首帧时间            | ≤ 2s
四宫格加载完成                | 全部就绪时间         | ≤ 3s
连续运行 30 分钟              | 内存增长            | ≤ 50MB (无泄漏)
```

### 15.4 兼容性测试矩阵

```
设备                    | SoC         | RAM  | Android | 重点验证
-----------------------|-------------|------|---------|------------------
Pixel 6a               | Tensor G1   | 6GB  | 14      | 基准设备
Pixel 8 Pro            | Tensor G3   | 12GB | 15      | 高性能
Samsung Galaxy A54     | Exynos 1380 | 6GB  | 14      | 中端三星
Samsung Galaxy S24     | Snapdragon 8G3| 8GB| 14      | 旗舰三星
Xiaomi Redmi Note 13   | Snapdragon 685| 4GB | 13     | 低端降级
OnePlus 12             | Snapdragon 8G3| 12GB| 14     | 高端一加
Samsung Galaxy Tab S9  | Snapdragon 8G2| 8GB | 14     | 平板适配
```

---

## 附录

### A. Gradle 配置参考

```kotlin
// app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.quadplayer.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.quadplayer.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.tooling)

    // Media3 ExoPlayer
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.common)
    implementation(libs.media3.datasource)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Others
    implementation(libs.coil.compose)
    implementation(libs.navigation.compose)
    implementation(libs.datastore.preferences)

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("io.mockk:mockk:1.13.10")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
}
```

### B. ProGuard 规则

```proguard
# ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * { @androidx.room.* <methods>; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Coil
-keep class coil3.** { *; }
```

### C. 关键技术决策记录

| 编号 | 决策 | 原因 | 备选方案 |
|------|------|------|---------|
| D-01 | 选择 Media3 ExoPlayer 而非 MediaPlayer | 支持多实例, 格式兼容性更好, 社区活跃 | Android MediaPlayer (不支持多实例) |
| D-02 | 使用 SurfaceView 而非 TextureView | 性能更高, 四路视频渲染压力下优势明显 | TextureView (支持动画但性能差) |
| D-03 | Compose 而非 XML | 布局灵活, 动画 API 强大, 状态管理简洁 | XML + ViewBinding |
| D-04 | 音量控制使用 player.volume 而非 AudioTrack | 更简单可靠, 不需要额外音频管道 | AudioTrack 混音 |
| D-05 | Crossfade 使用协程而非 ValueAnimator | 与 Kotlin 技术栈一致, 便于取消 | ValueAnimator |
| D-06 | 单 Activity 架构 | Compose Navigation 最佳实践 | 多 Activity |

### D. 给 Codex 的执行指引

```
开发顺序严格按照阶段一 → 阶段六执行, 每个阶段内按任务 ID 顺序执行。

关键约束:
1. 所有 ExoPlayer 实例必须在主线程创建和操作
2. Room 数据库操作必须在 IO 线程
3. PlayerController.release() 必须在不需要时调用, 避免解码器泄漏
4. 四宫格模式的 SurfaceView 必须各自独立, 不可复用
5. AudioSourceManager 的 Crossfade 必须可取消 (切换过程中再次切换)
6. QuadPlayerViewModel.onCleared() 中必须调用 engine.releaseAll()
7. 所有 StateFlow 使用 stateIn(SharingStarted.WhileSubscribed(5000)) 防止泄漏
8. 处理 Configuration Change (屏幕旋转) 时播放器状态不可丢失

每完成一个阶段, 运行该阶段的全部单元测试确保通过后再进入下一阶段。
```

---

*文档结束 — QuadPlayer 技术开发文档 v1.0*
