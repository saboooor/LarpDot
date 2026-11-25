package ca.saboor.larpdot.service

import android.content.Context
import android.content.IntentFilter
import androidx.core.content.ContextCompat

/** Registers the same torch action while either overlay host is active. */
internal class FlashlightBroadcastRegistration {
    private val receiver = FlashlightToggleReceiver()
    private var registered = false

    fun register(context: Context) {
        if (registered) return
        val filter = IntentFilter("ca.saboor.larpdot.ACTION_TOGGLE_FLASHLIGHT")
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        registered = true
    }

    fun unregister(context: Context) {
        if (!registered) return
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
            // The system may already have detached the receiver during service teardown.
        }
        registered = false
    }
}
