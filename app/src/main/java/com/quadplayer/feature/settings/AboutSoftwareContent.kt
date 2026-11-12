package com.quadplayer.feature.settings

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayCircle

internal data class AboutSoftwareContent(
    val intro: String,
    val featureGroups: List<AboutBulletGroup>,
    val requirements: List<AboutKeyValue>,
    val requirementHint: String,
    val guideGroups: List<AboutNumberedGroup>,
)

internal data class AboutBulletGroup(
    val title: String,
    val items: List<String>,
)

internal data class AboutKeyValue(
    val title: String,
    val value: String,
)

internal data class AboutNumberedGroup(
    val title: String,
    val steps: List<String>,
)

internal data class AboutVisualSection(
    val title: String,
    val icon: ImageVector,
)

internal fun aboutSoftwareContent(): AboutSoftwareContent {
    return AboutSoftwareContent(
        intro = "QuadPlayer 是一款专为 Android 平台打造的多视频同步播放应用。它的核心能力是同时播放最多 4 个视频，并允许你在不同视频之间自由切换音频源。无论是同时观看多场体育赛事、监控多路摄像头画面，还是对比不同视频素材，QuadPlayer 都能让你在一块屏幕上高效完成。",
        featureGroups = listOf(
            AboutBulletGroup(
                title = "四格同步播放（Quad 模式）",
                items = listOf(
                    "2×2 等分网格，四个视频画面可同时播放。",
                    "可点击任一画面切换当前音频源，其余画面自动静音。",
                    "支持音频交叉淡入淡出，切换听感更平滑。",
                    "支持全部播放/暂停、同步进度、统一调速等全局控制。",
                    "每个视频可单独拖拽进度、暂停或更换内容。",
                    "长按视频可交换画面位置，调整观看顺序。",
                ),
            ),
            AboutBulletGroup(
                title = "单视频播放",
                items = listOf(
                    "支持播放、暂停、进度拖动和缓冲提示。",
                    "支持 0.5x 到 3.0x 多档变速播放。",
                    "支持适应、填充、缩放、16:9、4:3、原始等画面比例。",
                    "支持外挂字幕、多音轨和手势调节音量、亮度。",
                    "支持断点续播，下次打开可从上次位置继续。",
                ),
            ),
            AboutBulletGroup(
                title = "媒体库管理",
                items = listOf(
                    "自动扫描设备中的本地视频文件。",
                    "按文件夹浏览视频，支持筛选、搜索和排序。",
                    "支持收藏、最近播放记录和播放列表管理。",
                ),
            ),
            AboutBulletGroup(
                title = "个性化设置",
                items = listOf(
                    "支持主题模式、解码模式、默认倍速等播放偏好。",
                    "支持自定义双击快进秒数和双击方向反转。",
                    "支持后台播放、自动续播和音频切换渐变开关。",
                ),
            ),
        ),
        requirements = listOf(
            AboutKeyValue("操作系统", "Android 8.0（API 26）及以上"),
            AboutKeyValue("推荐内存", "4GB 及以上（四格模式需同时运行 4 个解码器实例）"),
            AboutKeyValue("存储权限", "需要授予视频文件读取权限"),
        ),
        requirementHint = "QuadPlayer 内置设备能力检测，会在内存不足或硬件解码器数量不够时给出提示；运行过程中也会持续监控内存与设备温度，在资源紧张时提醒你及时调整。",
        guideGroups = listOf(
            AboutNumberedGroup(
                title = "快速开始",
                steps = listOf(
                    "首次启动后授予视频文件读取权限，应用会自动扫描本地视频。",
                    "在首页可查看媒体库、最近播放、收藏和播放列表。",
                    "点击任意视频即可进入单视频播放界面。",
                    "通过底部导航进入四格页面，可添加最多 4 个视频开始同步播放。",
                ),
            ),
            AboutNumberedGroup(
                title = "四格模式操作",
                steps = listOf(
                    "点击空白格子添加视频。",
                    "单击画面可切换当前音频源。",
                    "使用底部控制栏进行全部播放/暂停、同步进度和统一调速。",
                    "长按画面可交换位置或更换视频。",
                    "移除不需要的画面后，可继续补充新的本地视频。",
                ),
            ),
        ),
    )
}

internal fun aboutVisualSections(): List<AboutVisualSection> {
    return listOf(
        AboutVisualSection(title = "应用简介", icon = Icons.Rounded.Info),
        AboutVisualSection(title = "核心功能", icon = Icons.Rounded.GridView),
        AboutVisualSection(title = "系统要求", icon = Icons.Rounded.PhoneAndroid),
        AboutVisualSection(title = "使用指南", icon = Icons.Rounded.PlayCircle),
    )
}
