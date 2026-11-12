package com.quadplayer.core.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

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
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(activity, lifecycleOwner, hideStatusBars, hideNavigationBars) {
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

        val originalBehavior = controller?.systemBarsBehavior
        val originalInsets = decorView?.let { ViewCompat.getRootWindowInsets(it) }
        val statusWasVisible = originalInsets?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true
        val navigationWasVisible = originalInsets?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && insetTypes != 0) controller?.hide(insetTypes)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (controller != null && insetTypes != 0) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(insetTypes)
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (controller != null && insetTypes != 0) {
                if (hideStatusBars && statusWasVisible) controller.show(WindowInsetsCompat.Type.statusBars())
                if (hideNavigationBars && navigationWasVisible) controller.show(WindowInsetsCompat.Type.navigationBars())
                if (originalBehavior != null) controller.systemBarsBehavior = originalBehavior
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
