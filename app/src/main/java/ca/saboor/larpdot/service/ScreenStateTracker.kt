package ca.saboor.larpdot.service

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ScreenStateTracker {
    private val _isScreenOn = MutableStateFlow(true)
    val isScreenOn: StateFlow<Boolean> = _isScreenOn.asStateFlow()

    private val _isDeviceLocked = MutableStateFlow(false)
    val isDeviceLocked: StateFlow<Boolean> = _isDeviceLocked.asStateFlow()

    private var isInitialized = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    _isScreenOn.value = true
                    context?.let { updateLockState(it) }
                }
                Intent.ACTION_SCREEN_OFF -> {
                    _isScreenOn.value = false
                    _isDeviceLocked.value = true
                }
                Intent.ACTION_USER_PRESENT -> {
                    _isDeviceLocked.value = false
                    _isScreenOn.value = true
                }
            }
        }
    }

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        val appContext = context.applicationContext
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        _isScreenOn.value = powerManager?.isInteractive ?: true
        updateLockState(appContext)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        try {
            appContext.registerReceiver(receiver, filter)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateLockState(context: Context) {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        _isDeviceLocked.value = km?.isKeyguardLocked ?: false
    }
}
