package com.quadplayer.feature.settings

import android.content.Context
import com.quadplayer.R
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

internal fun aboutSoftwareContent(context: Context): AboutSoftwareContent {
    return AboutSoftwareContent(
        intro = context.getString(R.string.about_detail_01),
        featureGroups = listOf(
            AboutBulletGroup(
                title = context.getString(R.string.about_detail_02),
                items = listOf(
                    context.getString(R.string.about_detail_03),
                    context.getString(R.string.about_detail_04),
                    context.getString(R.string.about_detail_05),
                    context.getString(R.string.about_detail_06),
                    context.getString(R.string.about_detail_07),
                    context.getString(R.string.about_detail_08),
                ),
            ),
            AboutBulletGroup(
                title = context.getString(R.string.about_detail_09),
                items = listOf(
                    context.getString(R.string.about_detail_10),
                    context.getString(R.string.about_detail_11),
                    context.getString(R.string.about_detail_12),
                    context.getString(R.string.about_detail_13),
                    context.getString(R.string.about_detail_14),
                ),
            ),
            AboutBulletGroup(
                title = context.getString(R.string.about_detail_15),
                items = listOf(
                    context.getString(R.string.about_detail_16),
                    context.getString(R.string.about_detail_17),
                    context.getString(R.string.about_detail_18),
                ),
            ),
            AboutBulletGroup(
                title = context.getString(R.string.about_detail_19),
                items = listOf(
                    context.getString(R.string.about_detail_20),
                    context.getString(R.string.about_detail_21),
                    context.getString(R.string.about_detail_22),
                ),
            ),
        ),
        requirements = listOf(
            AboutKeyValue(context.getString(R.string.about_detail_23), context.getString(R.string.about_detail_24)),
            AboutKeyValue(context.getString(R.string.about_detail_25), context.getString(R.string.about_detail_26)),
            AboutKeyValue(context.getString(R.string.about_detail_27), context.getString(R.string.about_detail_28)),
        ),
        requirementHint = context.getString(R.string.about_detail_29),
        guideGroups = listOf(
            AboutNumberedGroup(
                title = context.getString(R.string.about_detail_30),
                steps = listOf(
                    context.getString(R.string.about_detail_31),
                    context.getString(R.string.about_detail_32),
                    context.getString(R.string.about_detail_33),
                    context.getString(R.string.about_detail_34),
                ),
            ),
            AboutNumberedGroup(
                title = context.getString(R.string.about_detail_35),
                steps = listOf(
                    context.getString(R.string.about_detail_36),
                    context.getString(R.string.about_detail_37),
                    context.getString(R.string.about_detail_38),
                    context.getString(R.string.about_detail_39),
                    context.getString(R.string.about_detail_40),
                ),
            ),
        ),
    )
}

internal fun aboutVisualSections(context: Context): List<AboutVisualSection> {
    return listOf(
        AboutVisualSection(title = context.getString(R.string.about_detail_41), icon = Icons.Rounded.Info),
        AboutVisualSection(title = context.getString(R.string.about_detail_42), icon = Icons.Rounded.GridView),
        AboutVisualSection(title = context.getString(R.string.about_detail_43), icon = Icons.Rounded.PhoneAndroid),
        AboutVisualSection(title = context.getString(R.string.about_detail_44), icon = Icons.Rounded.PlayCircle),
    )
}
