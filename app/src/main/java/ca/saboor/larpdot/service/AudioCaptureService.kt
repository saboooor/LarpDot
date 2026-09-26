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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

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

        return START_STICKY
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

    private fun readAudioLoop(record: AudioRecord, sampleRate: Int, numBands: Int) {
        val safeBands = numBands.coerceIn(1, 32)
        val numCutoffs = (safeBands - 1).coerceAtLeast(1)
        val cutoffs = when (safeBands) {
            1 -> doubleArrayOf(14000.0)
            2 -> doubleArrayOf(1000.0)
            3 -> doubleArrayOf(280.0, 3200.0)
            4 -> doubleArrayOf(220.0, 1000.0, 4200.0)
            5 -> doubleArrayOf(180.0, 600.0, 2400.0, 7000.0)
            6 -> doubleArrayOf(160.0, 450.0, 1400.0, 3800.0, 8500.0)
            7 -> doubleArrayOf(160.0, 450.0, 1200.0, 3000.0, 6500.0, 12000.0)
            else -> {
                val minFreq = 140.0
                val maxFreq = 14000.0
                val logMin = kotlin.math.ln(minFreq)
                val logMax = kotlin.math.ln(maxFreq)
                val step = (logMax - logMin) / safeBands
                DoubleArray(numCutoffs) { idx ->
                    kotlin.math.exp(logMin + (idx + 1) * step)
                }
            }
        }

        val alphas = DoubleArray(numCutoffs) { idx ->
            1.0 - exp(-2.0 * Math.PI * cutoffs[idx] / sampleRate.toDouble())
        }

        val lp1 = DoubleArray(numCutoffs)
        val lp2 = DoubleArray(numCutoffs)

        // Read in ~23ms chunks (1024 samples at 44.1kHz)
        val shortBuffer = ShortArray(1024)
        val bandPeaks = FloatArray(safeBands) { 0.08f }

        while (isRecording && serviceScope.isActive) {
            val samplesRead = record.read(shortBuffer, 0, shortBuffer.size)
            if (samplesRead <= 0) continue

            val sumSq = DoubleArray(safeBands)

            for (i in 0 until samplesRead) {
                val sample = shortBuffer[i].toDouble() / 32768.0

                for (c in 0 until numCutoffs) {
                    val a = alphas[c]
                    lp1[c] += a * (sample - lp1[c])
                    lp2[c] += a * (lp1[c] - lp2[c])
                }

                val band0 = lp2[0]
                sumSq[0] += band0 * band0

                for (b in 1 until numCutoffs) {
                    val bandVal = lp2[b] - lp2[b - 1]
                    sumSq[b] += bandVal * bandVal
                }

                val lastBand = sample - lp2[numCutoffs - 1]
                sumSq[safeBands - 1] += lastBand * lastBand
            }

            val rawAmps = FloatArray(safeBands)
            for (b in 0 until safeBands) {
                val rms = sqrt(sumSq[b] / samplesRead.toDouble()).toFloat()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AudioCaptureService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
