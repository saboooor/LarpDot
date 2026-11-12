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
