package com.quadplayer.core.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@Composable
fun LockScreenOrientation(orientation: Int) {
    val activity = LocalContext.current.findActivity()

    DisposableEffect(activity, orientation) {
        val originalOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = orientation

        onDispose {
            if (activity != null && originalOrientation != null) {
                activity.requestedOrientation = originalOrientation
            }
        }
    }
}

@Composable
fun ImmersiveFullscreen(
    hideStatusBars: Boolean = true,
    hideNavigationBars: Boolean = true,
) {
    val activity = LocalContext.current.findActivity()

    DisposableEffect(activity, hideStatusBars, hideNavigationBars) {
        val window = activity?.window
        val decorView = window?.decorView
        val controller = if (window != null && decorView != null) {
            WindowInsetsControllerCompat(window, decorView)
        } else {
            null
        }
        val insetTypes = buildSet {
            if (hideStatusBars) add(WindowInsetsCompat.Type.statusBars())
            if (hideNavigationBars) add(WindowInsetsCompat.Type.navigationBars())
        }.fold(0) { acc, type -> acc or type }

        if (controller != null && insetTypes != 0) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(insetTypes)
        }

        onDispose {
            if (controller != null && insetTypes != 0) {
                controller.show(insetTypes)
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
