package ca.saboor.larpdot.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.IntentCompat
import ca.saboor.larpdot.MainActivity
import ca.saboor.larpdot.visualizer.LiveAudioVisualizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Foreground service capturing internal system audio playback (Android 10+)
 * via AudioPlaybackCaptureConfiguration and MediaProjection.
 *
 * Captures clean digital audio stream directly from media players (Spotify, YouTube Music,
 * Apple Music, etc.) with zero ambient microphone noise and 0ms latency.
 */
class AudioCaptureService : Service() {

    private var serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    private var mediaProjection: MediaProjection? = null
    private var audioRecord: AudioRecord? = null
    private var isRecording = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopCapture()
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData: Intent? = intent?.let {
            IntentCompat.getParcelableExtra(it, EXTRA_RESULT_DATA, Intent::class.java)
        }
        val bandCount = intent?.getIntExtra(EXTRA_BAND_COUNT, 5) ?: 5

        if (resultCode == 0 || resultData == null) {
            Log.w(TAG, "Missing MediaProjection result data. Stopping service.")
            stopSelf()
            return START_NOT_STICKY
        }

        // 1. Must start foreground FIRST before calling getMediaProjection on Android 14+
        startForegroundWithNotification()

        // 2. Start internal playback capture
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startAudioCapture(resultCode, resultData, bandCount)
        } else {
            Log.w(TAG, "AudioPlaybackCapture requires Android 10 (API 29)+")
            LiveAudioVisualizer.reportError("Requires Android 10+")
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification() {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LarpDot Visualizer")
            .setContentText("Capturing device audio for dynamic island")
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun startAudioCapture(resultCode: Int, resultData: Intent, bandCount: Int) {
        if (isRecording) {
            return
        }

        try {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = projectionManager.getMediaProjection(resultCode, resultData)
            if (projection == null) {
                LiveAudioVisualizer.reportError("Failed to initialize MediaProjection")
                stopSelf()
                return
            }
            mediaProjection = projection

            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d(TAG, "MediaProjection stopped by system")
                    stopCapture()
                    stopSelf()
                }
            }, Handler(Looper.getMainLooper()))

            val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            val sampleRate = 44100
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT

            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = max(minBufferSize, 4096)

            val record = AudioRecord.Builder()
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .build(),
                )
                .setBufferSizeInBytes(bufferSize)
                .setAudioPlaybackCaptureConfig(config)
                .build()

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                LiveAudioVisualizer.reportError("AudioRecord initialization failed")
                stopCapture()
                stopSelf()
                return
            }

            audioRecord = record
            record.startRecording()
            isRecording = true
            LiveAudioVisualizer.reportCapturing(true)
            Log.d(TAG, "AudioPlaybackCapture started successfully")

            // Background reading loop
            serviceScope.launch {
                readAudioLoop(record, sampleRate, bandCount)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException starting audio capture: ${e.message}")
            LiveAudioVisualizer.reportError("Screen capture permission denied")
            stopCapture()
            stopSelf()
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to start AudioPlaybackCapture: ${e.message}")
            LiveAudioVisualizer.reportError(e.message ?: "Failed to capture audio")
            stopCapture()
            stopSelf()
        }
    }

    private suspend fun readAudioLoop(record: AudioRecord, sampleRate: Int, numBands: Int) {
        val filterBank = ca.saboor.larpdot.visualizer.CrossoverFilterBank(numBands, sampleRate)
        val safeBands = filterBank.bandCount

        // Read in ~23ms chunks (1024 samples at 44.1kHz)
        val shortBuffer = ShortArray(1024)
        val bandPeaks = FloatArray(safeBands) { 0.08f }

        while (isRecording && serviceScope.isActive) {
            // Pause hardware audio capture while the screen is off to save battery.
            if (!ScreenStateTracker.isScreenOn.value) {
                runCatching { record.stop() }
                ScreenStateTracker.isScreenOn.first { it }
                if (!isRecording || !serviceScope.isActive) break
                val resumed = runCatching { record.startRecording(); true }.getOrDefault(false)
                if (!resumed) break
            }

            val samplesRead = record.read(shortBuffer, 0, shortBuffer.size)
            if (samplesRead <= 0) continue

            filterBank.resetAccumulators()
            for (i in 0 until samplesRead) {
                filterBank.processSample(shortBuffer[i].toDouble() / 32768.0)
            }

            val rawAmps = FloatArray(safeBands)
            for (b in 0 until safeBands) {
                val rms = filterBank.bandRms(b, samplesRead)
                bandPeaks[b] = max(bandPeaks[b] * 0.990f, max(rms, 0.015f))
                rawAmps[b] = (rms / bandPeaks[b]).coerceIn(0.04f, 1.0f)
            }

            LiveAudioVisualizer.updateAmplitudes(rawAmps)
        }
    }

    private fun stopCapture() {
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Throwable) {
            Log.w(TAG, "Error stopping AudioRecord: ${e.message}")
        } finally {
            audioRecord = null
        }

        try {
            mediaProjection?.stop()
        } catch (e: Throwable) {
            Log.w(TAG, "Error stopping MediaProjection: ${e.message}")
        } finally {
            mediaProjection = null
        }

        LiveAudioVisualizer.reportCapturing(false)
    }

    override fun onDestroy() {
        stopCapture()
        serviceJob.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Device Audio Capture",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Used to capture device audio for live dynamic island visualizer"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "AudioCaptureService"
        private const val CHANNEL_ID = "audio_capture_channel"
        private const val NOTIFICATION_ID = 4040

        const val ACTION_START = "ca.saboor.larpdot.ACTION_START_AUDIO_CAPTURE"
        const val ACTION_STOP = "ca.saboor.larpdot.ACTION_STOP_AUDIO_CAPTURE"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_BAND_COUNT = "extra_band_count"

        fun start(context: Context, resultCode: Int, resultData: Intent, bandCount: Int = 5) {
            val intent = Intent(context, AudioCaptureService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, resultData)
                putExtra(EXTRA_BAND_COUNT, bandCount)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AudioCaptureService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
