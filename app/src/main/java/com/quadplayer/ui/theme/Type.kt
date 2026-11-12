package com.quadplayer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.quadplayer.R

private val InterTight = FontFamily(
    Font(R.font.inter_tight_regular, FontWeight.Normal),
    Font(R.font.inter_tight_semibold, FontWeight.SemiBold),
)

private fun type(size: Float, line: Float, weight: FontWeight, tracking: Float) = TextStyle(
    fontFamily = InterTight,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

val AppTypography = Typography(
    displayLarge = type(30f, 36f, FontWeight.SemiBold, -0.7f),
    displayMedium = type(28f, 34f, FontWeight.SemiBold, -0.7f),
    displaySmall = type(26f, 32f, FontWeight.SemiBold, -0.7f),
    headlineLarge = type(24f, 30f, FontWeight.SemiBold, -0.7f),
    headlineMedium = type(22f, 28f, FontWeight.SemiBold, -0.7f),
    headlineSmall = type(20f, 26f, FontWeight.SemiBold, -0.7f),
    titleLarge = type(19f, 24f, FontWeight.SemiBold, 0.6f),
    titleMedium = type(19f, 24f, FontWeight.SemiBold, 0.6f),
    titleSmall = type(16f, 22f, FontWeight.SemiBold, -0.2f),
    bodyLarge = type(14.5f, 18f, FontWeight.Normal, 0.3f),
    bodyMedium = type(14.5f, 18f, FontWeight.Normal, 0.3f),
    bodySmall = type(14.5f, 18f, FontWeight.Normal, 0.3f),
    labelLarge = type(14f, 20f, FontWeight.SemiBold, 0.3f),
    labelMedium = type(12f, 16f, FontWeight.SemiBold, 0.3f),
    labelSmall = type(11f, 16f, FontWeight.SemiBold, 0.3f),
)
