package com.quadplayer.benchmark

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.os.PowerManager
import android.os.SystemClock
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quadplayer.MainActivity
import com.quadplayer.core.model.VideoFile
import com.quadplayer.feature.player.PlayerController
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in real-device benchmark; no media is installed or removed by the test. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackPerformanceTest {
    @Test fun measure() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val uriText = args.getString("video_uri").orEmpty()
        assumeTrue("Supply video_uri to run the benchmark", uriText.isNotBlank())
        val count = args.getString("player_count")?.toIntOrNull() ?: 1
        require(count == 1 || count == 4)
        val seconds = (args.getString("duration_seconds")?.toIntOrNull() ?: 600).coerceIn(1, 3600)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse(uriText)
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        retriever.release()
        require(duration >= (seconds + 5L) * 1000L) { "Video must be at least five seconds longer than the sample" }
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        val format = (0 until extractor.trackCount).map(extractor::getTrackFormat).firstOrNull {
            it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
        }
        val mime = format?.getString(MediaFormat.KEY_MIME) ?: "unknown"
        val fps = if (format?.containsKey(MediaFormat.KEY_FRAME_RATE) == true) runCatching { format.getInteger(MediaFormat.KEY_FRAME_RATE) }.getOrNull() else null
        extractor.release()
        val video = VideoFile(-1, uriText, "benchmark", "benchmark", "benchmark", duration, 0, width, height, mime, 0, false, true)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val views = mutableListOf<SurfaceView>()
        val controllers = mutableListOf<PlayerController>()
        val firstFrames = List(count) { AtomicLong(0) }
        val pts = List(count) { AtomicLong(-1) }
        val releaseNs = List(count) { AtomicLong(0) }
        val startMs = LongArray(count)
        var overlay: FrameLayout? = null
        try {
            scenario.onActivity { activity ->
                val root = FrameLayout(activity)
                overlay = root
                activity.addContentView(root, FrameLayout.LayoutParams(-1, -1))
                val metrics = activity.resources.displayMetrics
                repeat(count) { index ->
                    val surface = SurfaceView(activity)
                    val columns = if (count == 4) 2 else 1
                    val rows = if (count == 4) 2 else 1
                    val params = FrameLayout.LayoutParams(metrics.widthPixels / columns, metrics.heightPixels / rows)
                    params.leftMargin = (index % columns) * params.width
                    params.topMargin = (index / columns) * params.height
                    root.addView(surface, params)
                    views += surface
                }
            }
            check(withTimeoutOrNull(15000) { while (views.any { !it.holder.surface.isValid }) delay(50); true } == true) { "Surfaces not ready" }
            withContext(Dispatchers.Main) {
                repeat(count) { index ->
                    val controller = PlayerController(context)
                    controllers += controller
                    controller.player.volume = 0f
                    controller.player.addListener(object : Player.Listener {
                        override fun onRenderedFirstFrame() { firstFrames[index].compareAndSet(0, SystemClock.elapsedRealtime()) }
                    })
                    controller.player.setVideoFrameMetadataListener(VideoFrameMetadataListener { presentationTimeUs, releaseTimeNs, _, _ ->
                        pts[index].set(presentationTimeUs)
                        releaseNs[index].set(releaseTimeNs)
                    })
                    startMs[index] = SystemClock.elapsedRealtime()
                    controller.load(video)
                    controller.player.setVideoSurfaceView(views[index])
                }
            }
            withTimeoutOrNull(20000) { while (firstFrames.any { it.get() == 0L }) delay(50) }
            val allRendered = firstFrames.all { it.get() > 0 }
            val baseline = withContext(Dispatchers.Main) {
                controllers.map { controller ->
                    controller.player.videoDecoderCounters?.apply { ensureUpdated() }?.let {
                        it.renderedOutputBufferCount to it.droppedBufferCount
                    } ?: (0 to 0)
                }
            }
            withContext(Dispatchers.Main) {
                controllers.forEach { it.pause(); it.seekTo(0) }
                pts.forEach { it.set(-1) }
                releaseNs.forEach { it.set(0) }
                controllers.forEach { it.play() }
            }
            var peakPssKb = 0
            val drift = JSONObject()
            val started = SystemClock.elapsedRealtime()
            while (SystemClock.elapsedRealtime() - started < seconds * 1000L) {
                val elapsed = (SystemClock.elapsedRealtime() - started) / 1000
                val memory = Debug.MemoryInfo().also(Debug::getMemoryInfo)
                peakPssKb = maxOf(peakPssKb, memory.totalPss)
                if (elapsed in listOf(1L, 10L, 60L) && !drift.has(elapsed.toString()) && pts.indices.all { pts[it].get() >= 0 && releaseNs[it].get() > 0 }) {
                    val now = System.nanoTime()
                    val projected = pts.indices.map { index -> pts[index].get() + (now - releaseNs[index].get()) / 1000 }
                    drift.put(elapsed.toString(), (projected.max() - projected.min()) / 1000)
                }
                delay(1000)
            }
            val counters = withContext(Dispatchers.Main) {
                controllers.mapIndexed { index, controller ->
                    controller.player.videoDecoderCounters?.apply { ensureUpdated() }?.let {
                        (it.renderedOutputBufferCount - baseline[index].first).coerceAtLeast(0) to
                            (it.droppedBufferCount - baseline[index].second).coerceAtLeast(0)
                    } ?: (0 to 0)
                }
            }
            val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val report = JSONObject().put("model", Build.MODEL).put("manufacturer", Build.MANUFACTURER)
                .put("api", Build.VERSION.SDK_INT).put("playerCount", count).put("sampleSeconds", seconds)
                .put("sourceDurationMs", duration).put("screenWidthPx", context.resources.displayMetrics.widthPixels)
                .put("screenHeightPx", context.resources.displayMetrics.heightPixels)
                .put("trackMime", mime).put("width", width).put("height", height).put("frameRate", fps ?: JSONObject.NULL)
                .put("firstFrameMs", JSONArray(firstFrames.indices.map { i -> if (firstFrames[i].get() > 0) firstFrames[i].get() - startMs[i] else JSONObject.NULL }))
                .put("peakProcessPssKb", peakPssKb)
                .put("renderedFrames", JSONArray(counters.map { it.first }))
                .put("droppedFrames", JSONArray(counters.map { it.second }))
                .put("estimatedPtsDriftMs", drift)
                .put("thermalStatus", if (Build.VERSION.SDK_INT >= 29) power.currentThermalStatus else JSONObject.NULL)
            val output = context.getExternalFilesDir("benchmarks")!!.resolve("playback-${System.currentTimeMillis()}.json")
            output.writeText(report.toString(2))
            InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("benchmark", report.toString()); putString("reportFile", output.absolutePath) })
            assertTrue("At least one player failed to render its first frame; report: ${output.absolutePath}", allRendered)
        } finally {
            withContext(Dispatchers.Main) {
                controllers.forEach(PlayerController::release)
                overlay?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) }
            }
            scenario.close()
        }
    }
}
