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

private fun featureGroup(context: Context, @androidx.annotation.ArrayRes id: Int): AboutBulletGroup {
    val values = context.resources.getStringArray(id)
    return AboutBulletGroup(values.first(), values.drop(1))
}

private fun guideGroup(context: Context, @androidx.annotation.ArrayRes id: Int): AboutNumberedGroup {
    val values = context.resources.getStringArray(id)
    return AboutNumberedGroup(values.first(), values.drop(1))
}

private fun groupIds(context: Context, @androidx.annotation.ArrayRes id: Int): List<Int> {
    val array = context.resources.obtainTypedArray(id)
    return try { (0 until array.length()).map { array.getResourceId(it, 0).also { ref -> require(ref != 0) } } }
    finally { array.recycle() }
}

internal fun aboutSoftwareContent(context: Context): AboutSoftwareContent {
    val requirements = context.resources.getStringArray(R.array.about_requirements)
    require(requirements.size >= 3 && requirements.size % 2 == 1)
    return AboutSoftwareContent(
        intro = context.resources.getStringArray(R.array.about_intro).single(),
        featureGroups = groupIds(context, R.array.about_feature_groups).map { featureGroup(context, it) },
        requirements = requirements.dropLast(1).chunked(2).map { AboutKeyValue(it[0], it[1]) },
        requirementHint = requirements.last(),
        guideGroups = groupIds(context, R.array.about_guide_groups).map { guideGroup(context, it) },
    )
}

internal fun aboutVisualSections(context: Context): List<AboutVisualSection> {
    val headings = context.resources.getStringArray(R.array.about_headings)
    val icons = listOf(Icons.Rounded.Info, Icons.Rounded.GridView, Icons.Rounded.PhoneAndroid, Icons.Rounded.PlayCircle)
    require(headings.size == icons.size)
    return headings.zip(icons).map { (title, icon) -> AboutVisualSection(title, icon) }
}
