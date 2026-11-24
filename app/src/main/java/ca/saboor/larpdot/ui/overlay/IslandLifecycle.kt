package ca.saboor.larpdot.ui.overlay

import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Shared visibility and exit grace periods. Reactivation cancels a pending exit. */
class IslandLifecycle<K>(private val scope: () -> CoroutineScope) {
    private val active = mutableStateMapOf<K, Boolean>()
    private val exits = mutableMapOf<K, Job>()

    fun isActive(key: K): Boolean = active[key] == true

    fun update(key: K, visible: Boolean, exitDelayMs: Long = 0, onChanged: () -> Unit = {}) {
        exits.remove(key)?.cancel()
        if (!visible && isActive(key) && exitDelayMs > 0) {
            exits[key] = scope().launch {
                delay(exitDelayMs)
                active[key] = false
                exits.remove(key)
                onChanged()
            }
        } else {
            active[key] = visible
            onChanged()
        }
    }

    fun clear() {
        exits.values.forEach { it.cancel() }
        exits.clear()
        active.clear()
    }
}

/** Temporarily suppresses a swiped-up provider until its underlying session or identity changes. */
class IslandDismissalTracker {
    private var dismissedMediaToken: String? = null
    private var dismissedFlashlightSession: Long? = null
    private val dismissedActivities = mutableSetOf<String>()

    fun dismissMedia(token: String) {
        if (token.isNotBlank()) dismissedMediaToken = token
    }

    fun isMediaDismissed(token: String?): Boolean {
        if (token.isNullOrBlank()) {
            dismissedMediaToken = null
            return false
        }
        if (dismissedMediaToken != null && dismissedMediaToken != token) {
            dismissedMediaToken = null
        }
        return dismissedMediaToken == token
    }

    fun dismissFlashlight(sessionTime: Long) {
        dismissedFlashlightSession = sessionTime
    }

    fun isFlashlightDismissed(isOn: Boolean, sessionTime: Long): Boolean {
        if (!isOn) {
            dismissedFlashlightSession = null
            return false
        }
        if (dismissedFlashlightSession != null && dismissedFlashlightSession != sessionTime) {
            dismissedFlashlightSession = null
        }
        return dismissedFlashlightSession == sessionTime
    }

    fun dismissActivity(key: String) {
        if (key.isNotBlank()) dismissedActivities.add(key)
    }

    fun isActivityDismissed(key: String?): Boolean = key != null && key in dismissedActivities

    fun pruneActivities(activeKeys: Set<String>) {
        dismissedActivities.retainAll(activeKeys)
    }

    fun clear() {
        dismissedMediaToken = null
        dismissedFlashlightSession = null
        dismissedActivities.clear()
    }
}
