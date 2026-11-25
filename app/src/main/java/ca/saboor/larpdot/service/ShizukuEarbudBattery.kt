package ca.saboor.larpdot.service

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.AttributionSource
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.IInterface
import android.util.Log
import androidx.annotation.RequiresApi
import ca.saboor.larpdot.notification.EarbudBatteryLevels
import java.lang.reflect.Method
import java.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper

internal enum class ShizukuBatteryStatus {
    NOT_RUNNING, NEEDS_PERMISSION, PERMISSION_DENIED, UNSUPPORTED, READY,
}

internal object ShizukuEarbudBattery {
    private const val PERMISSION_REQUEST = 110
    private const val LEFT_BATTERY = 10
    private const val RIGHT_BATTERY = 11
    private const val BLUETOOTH_PRIVILEGED = "android.permission.BLUETOOTH_PRIVILEGED"
    private const val SHELL_UID = 2000

    private val _status = MutableStateFlow(ShizukuBatteryStatus.NOT_RUNNING)
    val status = _status.asStateFlow()

    fun initialize() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        Shizuku.addBinderReceivedListenerSticky(::refreshStatus)
        Shizuku.addBinderDeadListener { _status.value = ShizukuBatteryStatus.NOT_RUNNING }
        Shizuku.addRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == PERMISSION_REQUEST) refreshStatus()
        }
    }

    fun refreshStatus() {
        _status.value = runCatching {
            when {
                !Shizuku.pingBinder() -> ShizukuBatteryStatus.NOT_RUNNING
                Shizuku.isPreV11() -> ShizukuBatteryStatus.UNSUPPORTED
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED ->
                    if (Shizuku.shouldShowRequestPermissionRationale()) ShizukuBatteryStatus.PERMISSION_DENIED
                    else ShizukuBatteryStatus.NEEDS_PERMISSION
                Shizuku.checkRemotePermission(BLUETOOTH_PRIVILEGED) != PackageManager.PERMISSION_GRANTED ||
                    Shizuku.checkRemotePermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ->
                    ShizukuBatteryStatus.UNSUPPORTED
                else -> ShizukuBatteryStatus.READY
            }
        }.getOrDefault(ShizukuBatteryStatus.NOT_RUNNING)
    }

    fun requestPermission() {
        refreshStatus()
        if (_status.value == ShizukuBatteryStatus.NEEDS_PERMISSION) {
            runCatching { Shizuku.requestPermission(PERMISSION_REQUEST) }.onFailure { refreshStatus() }
        }
    }

    fun managerLaunchIntent(context: Context): Intent? {
        val packages = listOf("af.shizuku.plus.api", "moe.shizuku.privileged.api")
        return packages.firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) }
    }

    suspend fun readMissingLevels(
        context: Context,
        device: BluetoothDevice,
        standardLevels: EarbudBatteryLevels? = null,
    ): EarbudBatteryLevels? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || status.value != ShizukuBatteryStatus.READY ||
            (standardLevels?.left != null && standardLevels.right != null)
        ) return standardLevels

        return withContext(Dispatchers.IO) {
            runCatching {
                if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    return@runCatching standardLevels
                }
                val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
                    ?: return@runCatching standardLevels
                readMetadata(adapter, device, standardLevels)
            }.onFailure {
                if (it is CancellationException) throw it
                Log.d("ShizukuEarbudBattery", "Earbud metadata unavailable", it)
            }.getOrDefault(standardLevels)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun readMetadata(
        adapter: BluetoothAdapter,
        device: BluetoothDevice,
        standardLevels: EarbudBatteryLevels?,
    ): EarbudBatteryLevels? {
        val service = serviceMethod.let { method ->
            if (method.parameterCount == 0) method.invoke(adapter) else method.invoke(adapter, null)
        } as? IInterface ?: return standardLevels
        val proxy = asInterface.invoke(null, ShizukuBinderWrapper(service.asBinder())) ?: return standardLevels
        // Shizuku forwards the call with shell or root identity. Shell owns the attribution package.
        val source = AttributionSource.Builder(SHELL_UID).setPackageName("com.android.shell").build()
        val left = standardLevels?.left ?: readPercent(proxy, device, LEFT_BATTERY, source)
        val right = standardLevels?.right ?: readPercent(proxy, device, RIGHT_BATTERY, source)
        return if (left != null || right != null) EarbudBatteryLevels(left, right) else null
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun readPercent(proxy: Any, device: BluetoothDevice, key: Int, source: AttributionSource): Int? {
        val method = metadataMethod
        val value = if (method.parameterCount == 3) {
            method.invoke(proxy, device, key, source)
        } else {
            val receiverClass = method.parameterTypes[3]
            val receiver = hiddenMethod(receiverClass, "get").invoke(null)
            method.invoke(proxy, device, key, source, receiver)
            val result = hiddenMethod(receiverClass, "awaitResultNoInterrupt", Duration::class.java)
                .invoke(receiver, Duration.ofMillis(400)) ?: return null
            hiddenMethod(result.javaClass, "getValue", Any::class.java).invoke(result, null)
        }
        return (value as? ByteArray)?.let { String(it, Charsets.UTF_8).toIntOrNull() }?.takeIf { it in 0..100 }
    }

    private val serviceMethod: Method by lazy {
        HiddenApiBypass.getDeclaredMethods(BluetoothAdapter::class.java).filterIsInstance<Method>()
            .filter { it.name == "getBluetoothService" && it.parameterCount <= 1 }
            .minBy { it.parameterCount }
            .apply { isAccessible = true }
    }
    private val asInterface: Method by lazy {
        hiddenMethod(Class.forName("android.bluetooth.IBluetooth\$Stub"), "asInterface", IBinder::class.java)
    }
    private val metadataMethod: Method by lazy {
        HiddenApiBypass.getDeclaredMethods(Class.forName("android.bluetooth.IBluetooth")).filterIsInstance<Method>()
            .filter { it.name == "getMetadata" && it.parameterCount in 3..4 }
            .minBy { it.parameterCount }
            .apply { isAccessible = true }
    }

    private fun hiddenMethod(owner: Class<*>, name: String, vararg parameters: Class<*>): Method =
        HiddenApiBypass.getDeclaredMethod(owner, name, *parameters).apply { isAccessible = true }
}
