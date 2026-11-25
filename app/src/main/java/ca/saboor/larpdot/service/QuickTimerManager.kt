package ca.saboor.larpdot.service

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import ca.saboor.larpdot.notification.NotificationActivityInfo
import ca.saboor.larpdot.notification.NotificationActivityKind
import ca.saboor.larpdot.notification.NotificationActivityState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

enum class QuickTimerMode {
    COUNTDOWN,
    STOPWATCH,
}

sealed interface QuickTimerState {
    data object Idle : QuickTimerState

    data class Running(
        val mode: QuickTimerMode,
        val label: String,
        val totalDurationMs: Long,
        val baseElapsedRealtimeMs: Long,
        val displayMs: Long,
    ) : QuickTimerState

    data class Paused(
        val mode: QuickTimerMode,
        val label: String,
        val totalDurationMs: Long,
        val pausedDisplayMs: Long,
    ) : QuickTimerState

    data class Finished(
        val label: String,
        val totalDurationMs: Long,
    ) : QuickTimerState
}

internal fun formatQuickTimerDuration(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) + 500L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

internal fun computeQuickTimerRemainingProgress(remainingMs: Long, totalDurationMs: Long): Int? {
    if (totalDurationMs <= 0L) return null
    val clamped = remainingMs.coerceIn(0L, totalDurationMs)
    return ((clamped.toDouble() / totalDurationMs.toDouble()) * 100.0).toInt().coerceIn(0, 100)
}

internal fun formatCountdownTitle(totalSeconds: Int, customLabel: String? = null): String {
    val cleanLabel = customLabel?.trim()?.take(24).orEmpty()
    if (cleanLabel.isNotEmpty()) return cleanLabel
    val safeSeconds = totalSeconds.coerceAtLeast(1)
    val minutes = safeSeconds / 60
    val seconds = safeSeconds % 60
    return when {
        minutes > 0 && seconds > 0 -> "${minutes}m ${seconds}s Timer"
        minutes > 0 -> "${minutes}m Timer"
        else -> "${seconds}s Timer"
    }
}

/**
 * Built-in Dynamic Island quick countdown timer and stopwatch with zero external app dependency.
 */
object QuickTimerManager {
    const val ACTIVITY_KEY = "larpdot_quick_timer"
    private const val PACKAGE_NAME = "ca.saboor.larpdot"
    private const val MIN_COUNTDOWN_SECONDS = 5
    private const val MAX_COUNTDOWN_SECONDS = 240 * 60

    private var appContext: Context? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var tickJob: Job? = null

    private val _state = MutableStateFlow<QuickTimerState>(QuickTimerState.Idle)
    val state: StateFlow<QuickTimerState> = _state.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun startCountdown(durationMinutes: Int, nowElapsedMs: Long = SystemClock.elapsedRealtime()) {
        startCountdownSeconds(
            totalSeconds = durationMinutes.coerceIn(1, 240) * 60,
            label = null,
            nowElapsedMs = nowElapsedMs,
        )
    }

    fun startCountdownSeconds(
        totalSeconds: Int,
        label: String? = null,
        nowElapsedMs: Long = SystemClock.elapsedRealtime(),
    ) {
        val safeSeconds = totalSeconds.coerceIn(MIN_COUNTDOWN_SECONDS, MAX_COUNTDOWN_SECONDS)
        val totalMs = safeSeconds * 1_000L
        val title = formatCountdownTitle(safeSeconds, label)
        val endElapsed = nowElapsedMs + totalMs
        val running = QuickTimerState.Running(
            mode = QuickTimerMode.COUNTDOWN,
            label = title,
            totalDurationMs = totalMs,
            baseElapsedRealtimeMs = endElapsed,
            displayMs = totalMs,
        )
        _state.value = running
        publishActivity(running)
        startTicker()
    }

    fun startStopwatch(nowElapsedMs: Long = SystemClock.elapsedRealtime()) {
        val running = QuickTimerState.Running(
            mode = QuickTimerMode.STOPWATCH,
            label = "Stopwatch",
            totalDurationMs = 0L,
            baseElapsedRealtimeMs = nowElapsedMs,
            displayMs = 0L,
        )
        _state.value = running
        publishActivity(running)
        startTicker()
    }

    fun togglePause(nowElapsedMs: Long = SystemClock.elapsedRealtime()) {
        when (val current = _state.value) {
            is QuickTimerState.Running -> {
                tickJob?.cancel()
                tickJob = null
                val frozenMs = if (current.mode == QuickTimerMode.COUNTDOWN) {
                    (current.baseElapsedRealtimeMs - nowElapsedMs).coerceAtLeast(0L)
                } else {
                    (nowElapsedMs - current.baseElapsedRealtimeMs).coerceAtLeast(0L)
                }
                val paused = QuickTimerState.Paused(
                    mode = current.mode,
                    label = current.label,
                    totalDurationMs = current.totalDurationMs,
                    pausedDisplayMs = frozenMs,
                )
                _state.value = paused
                publishActivity(paused)
            }
            is QuickTimerState.Paused -> {
                val base = if (current.mode == QuickTimerMode.COUNTDOWN) {
                    nowElapsedMs + current.pausedDisplayMs
                } else {
                    nowElapsedMs - current.pausedDisplayMs
                }
                val running = QuickTimerState.Running(
                    mode = current.mode,
                    label = current.label,
                    totalDurationMs = current.totalDurationMs,
                    baseElapsedRealtimeMs = base,
                    displayMs = current.pausedDisplayMs,
                )
                _state.value = running
                publishActivity(running)
                startTicker()
            }
            else -> Unit
        }
    }

    fun addOneMinute(nowElapsedMs: Long = SystemClock.elapsedRealtime()) {
        val extraMs = 60_000L
        when (val current = _state.value) {
            is QuickTimerState.Running -> {
                if (current.mode != QuickTimerMode.COUNTDOWN) return
                val updated = current.copy(
                    totalDurationMs = current.totalDurationMs + extraMs,
                    baseElapsedRealtimeMs = current.baseElapsedRealtimeMs + extraMs,
                    displayMs = (current.baseElapsedRealtimeMs + extraMs - nowElapsedMs).coerceAtLeast(0L),
                )
                _state.value = updated
                publishActivity(updated)
            }
            is QuickTimerState.Paused -> {
                if (current.mode != QuickTimerMode.COUNTDOWN) return
                val updated = current.copy(
                    totalDurationMs = current.totalDurationMs + extraMs,
                    pausedDisplayMs = current.pausedDisplayMs + extraMs,
                )
                _state.value = updated
                publishActivity(updated)
            }
            is QuickTimerState.Finished -> {
                val running = QuickTimerState.Running(
                    mode = QuickTimerMode.COUNTDOWN,
                    label = current.label,
                    totalDurationMs = extraMs,
                    baseElapsedRealtimeMs = nowElapsedMs + extraMs,
                    displayMs = extraMs,
                )
                _state.value = running
                publishActivity(running)
                startTicker()
            }
            QuickTimerState.Idle -> Unit
        }
    }

    fun restart(nowElapsedMs: Long = SystemClock.elapsedRealtime()) {
        when (val current = _state.value) {
            is QuickTimerState.Finished -> {
                val totalMs = current.totalDurationMs.coerceAtLeast(MIN_COUNTDOWN_SECONDS * 1_000L)
                val running = QuickTimerState.Running(
                    mode = QuickTimerMode.COUNTDOWN,
                    label = current.label,
                    totalDurationMs = totalMs,
                    baseElapsedRealtimeMs = nowElapsedMs + totalMs,
                    displayMs = totalMs,
                )
                _state.value = running
                publishActivity(running)
                startTicker()
            }
            is QuickTimerState.Paused -> {
                if (current.mode == QuickTimerMode.STOPWATCH) {
                    startStopwatch(nowElapsedMs)
                } else {
                    val totalMs = current.totalDurationMs.coerceAtLeast(MIN_COUNTDOWN_SECONDS * 1_000L)
                    val running = QuickTimerState.Running(
                        mode = QuickTimerMode.COUNTDOWN,
                        label = current.label,
                        totalDurationMs = totalMs,
                        baseElapsedRealtimeMs = nowElapsedMs + totalMs,
                        displayMs = totalMs,
                    )
                    _state.value = running
                    publishActivity(running)
                    startTicker()
                }
            }
            is QuickTimerState.Running -> {
                if (current.mode == QuickTimerMode.STOPWATCH) startStopwatch(nowElapsedMs)
            }
            QuickTimerState.Idle -> Unit
        }
    }

    fun stop() {
        tickJob?.cancel()
        tickJob = null
        _state.value = QuickTimerState.Idle
        NotificationActivityState.removeSystemActivity(ACTIVITY_KEY)
    }

    private fun startTicker() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (isActive) {
                val current = _state.value
                if (current !is QuickTimerState.Running) break
                val now = SystemClock.elapsedRealtime()
                if (current.mode == QuickTimerMode.COUNTDOWN) {
                    val remaining = current.baseElapsedRealtimeMs - now
                    if (remaining <= 0L) {
                        val finished = QuickTimerState.Finished(
                            label = current.label,
                            totalDurationMs = current.totalDurationMs,
                        )
                        _state.value = finished
                        publishActivity(finished)
                        vibrateTimerFinished()
                        // Auto-clear finished banner after 20s if not restarted or dismissed.
                        delay(20_000L)
                        if (_state.value is QuickTimerState.Finished) {
                            stop()
                        }
                        break
                    } else {
                        val updated = current.copy(displayMs = remaining)
                        _state.value = updated
                        publishActivity(updated)
                    }
                } else {
                    val elapsed = (now - current.baseElapsedRealtimeMs).coerceAtLeast(0L)
                    _state.value = current.copy(displayMs = elapsed)
                }
                delay(1_000L)
            }
        }
    }

    private fun publishActivity(timerState: QuickTimerState) {
        val nowWall = System.currentTimeMillis()
        val info = when (timerState) {
            QuickTimerState.Idle -> {
                NotificationActivityState.removeSystemActivity(ACTIVITY_KEY)
                return
            }
            is QuickTimerState.Running -> {
                val isCountdown = timerState.mode == QuickTimerMode.COUNTDOWN
                NotificationActivityInfo(
                    key = ACTIVITY_KEY,
                    packageName = PACKAGE_NAME,
                    appName = "Quick Timer",
                    title = timerState.label,
                    text = if (isCountdown) {
                        "${formatQuickTimerDuration(timerState.displayMs)} remaining"
                    } else {
                        "Running"
                    },
                    kind = NotificationActivityKind.TIMER,
                    progress = if (isCountdown) {
                        computeQuickTimerRemainingProgress(timerState.displayMs, timerState.totalDurationMs)
                    } else null,
                    progressMax = if (isCountdown) 100 else null,
                    progressIndeterminate = false,
                    chronometerBaseElapsedRealtime = timerState.baseElapsedRealtimeMs,
                    chronometerCountsDown = isCountdown,
                    postedAtMillis = nowWall,
                    contentIntent = null,
                    actions = emptyList(),
                )
            }
            is QuickTimerState.Paused -> {
                val formatted = formatQuickTimerDuration(timerState.pausedDisplayMs)
                val isCountdown = timerState.mode == QuickTimerMode.COUNTDOWN
                NotificationActivityInfo(
                    key = ACTIVITY_KEY,
                    packageName = PACKAGE_NAME,
                    appName = "Quick Timer",
                    title = "${timerState.label} • Paused",
                    text = formatted,
                    kind = NotificationActivityKind.TIMER,
                    progress = if (isCountdown) {
                        computeQuickTimerRemainingProgress(timerState.pausedDisplayMs, timerState.totalDurationMs)
                    } else null,
                    progressMax = if (isCountdown) 100 else null,
                    progressIndeterminate = false,
                    chronometerBaseElapsedRealtime = null,
                    chronometerCountsDown = false,
                    postedAtMillis = nowWall,
                    contentIntent = null,
                    actions = emptyList(),
                    concurrentStatuses = listOf(formatted),
                )
            }
            is QuickTimerState.Finished -> {
                NotificationActivityInfo(
                    key = ACTIVITY_KEY,
                    packageName = PACKAGE_NAME,
                    appName = "Quick Timer",
                    title = "Time's Up!",
                    text = "${timerState.label} finished",
                    kind = NotificationActivityKind.TIMER,
                    progress = 0,
                    progressMax = 100,
                    progressIndeterminate = false,
                    chronometerBaseElapsedRealtime = null,
                    chronometerCountsDown = false,
                    postedAtMillis = nowWall,
                    contentIntent = null,
                    actions = emptyList(),
                    concurrentStatuses = listOf("Done"),
                )
            }
        }
        NotificationActivityState.postSystemActivity(info)
    }

    @Suppress("DEPRECATION")
    private fun vibrateTimerFinished() {
        if (!OverlayPreferences.hapticsEnabledFlow.value) return
        val ctx = appContext ?: return
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return
            if (!vibrator.hasVibrator()) return
            val timings = longArrayOf(0L, 140L, 90L, 140L, 90L, 220L)
            val amplitudes = intArrayOf(0, 180, 0, 180, 0, 230)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                vibrator.vibrate(timings, -1)
            }
        }
    }
}
