package com.quadplayer.feature.quadplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

/** One focus owner for the whole grid. Losing focus or an audio route pauses all streams. */
internal class QuadAudioFocus(private val context: Context, private val onInterrupted: () -> Unit) {
    private val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    var active = false
        private set
    private var focused = false
    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener({ change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) interrupt()
        }, Handler(Looper.getMainLooper()))
        .build()
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) interrupt()
        }
    }

    fun start() {
        if (active) return
        ContextCompat.registerReceiver(context, noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
        active = true
    }

    fun request(): Boolean {
        if (!active) return false
        if (!focused) focused = manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return focused
    }

    private fun interrupt() {
        onInterrupted()
        abandon()
    }

    fun abandon() {
        if (focused) manager.abandonAudioFocusRequest(request)
        focused = false
    }

    fun stop() {
        abandon()
        if (active) context.unregisterReceiver(noisyReceiver)
        active = false
    }
}
