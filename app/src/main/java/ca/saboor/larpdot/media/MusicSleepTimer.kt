package ca.saboor.larpdot.media

import android.os.SystemClock
import java.util.Locale
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

sealed class SleepTimerState {
    data object Off : SleepTimerState()
    data class Countdown(val remainingMs: Long) : SleepTimerState()
    data class EndOfTrack(val targetTrackKey: String) : SleepTimerState()
}

internal fun formatSleepTimerBadge(state: SleepTimerState): String? = when (state) {
    SleepTimerState.Off -> null
    is SleepTimerState.EndOfTrack -> "End"
    is SleepTimerState.Countdown -> {
        val totalSeconds = ((state.remainingMs + 999L) / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        if (hours > 0L) {
            String.format(Locale.US, "%dh %02dm", hours, minutes)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }
}

internal fun shouldTriggerEndOfTrackSleep(state: SleepTimerState, currentTrackKey: String): Boolean {
    if (state !is SleepTimerState.EndOfTrack) return false
    if (currentTrackKey.isBlank()) return false
    if (state.targetTrackKey.isBlank()) return false
    return state.targetTrackKey != currentTrackKey
}

/** Sleep timer that automatically pauses media playback after a duration or at end of current track. */
object MusicSleepTimer {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var timerJob: Job? = null

    private val _state = MutableStateFlow<SleepTimerState>(SleepTimerState.Off)
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    fun startMinutes(minutes: Int) {
        cancel()
        if (minutes <= 0) return
        val durationMs = minutes.toLong() * 60_000L
        val deadlineMs = SystemClock.uptimeMillis() + durationMs
        _state.value = SleepTimerState.Countdown(durationMs)
        timerJob = scope.launch {
            while (isActive) {
                delay(1_000L)
                val remaining = deadlineMs - SystemClock.uptimeMillis()
                if (remaining <= 0L) {
                    _state.value = SleepTimerState.Off
                    MediaPlaybackState.pause()
                    break
                }
                _state.value = SleepTimerState.Countdown(remaining)
            }
        }
    }

    fun startEndOfTrack() {
        cancel()
        val track = MediaPlaybackState.currentTrack.value
        val key = "${track.title}_${track.artist}".trim()
        _state.value = SleepTimerState.EndOfTrack(key)
    }

    fun onTrackChanged(trackKey: String) {
        val current = _state.value
        if (current is SleepTimerState.EndOfTrack && current.targetTrackKey.isBlank() && trackKey.isNotBlank()) {
            _state.value = SleepTimerState.EndOfTrack(trackKey)
            return
        }
        if (shouldTriggerEndOfTrackSleep(current, trackKey)) {
            cancel()
            MediaPlaybackState.pause()
        }
    }

    fun cancel() {
        timerJob?.cancel()
        timerJob = null
        _state.value = SleepTimerState.Off
    }
}
