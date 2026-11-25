package ca.saboor.larpdot.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import ca.saboor.larpdot.notification.EarbudBatteryLevels
import java.util.UUID
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

private val batteryServiceUuid = bluetoothUuid("180d")
private val batteryLevelUuid = bluetoothUuid("2a19")
private val presentationFormatUuid = bluetoothUuid("2904")
internal const val EARBUD_BATTERY_READ_TIMEOUT_MS = 3_000L

private fun bluetoothUuid(id: String): UUID = UUID.fromString("0000$id-0000-1000-8000-00805f9b34fb")

internal enum class EarbudSide { LEFT, RIGHT }

internal fun earbudSideFromPresentation(value: ByteArray): EarbudSide? {
    // Characteristic Presentation Format: SIG namespace, followed by a little-endian description.
    if (value.size != 7 || value[4] != 1.toByte()) return null
    val description = (value[5].toInt() and 0xff) or ((value[6].toInt() and 0xff) shl 8)
    return when (description) {
        0x010d -> EarbudSide.LEFT
        0x010e -> EarbudSide.RIGHT
        else -> null
    }
}

internal fun earbudBatteryPercent(value: ByteArray): Int? =
    value.singleOrNull()?.toInt()?.takeIf { it in 0..100 }

@SuppressLint("MissingPermission")
@Suppress("DEPRECATION") // The transport-specific overload is available on all supported versions.
internal suspend fun readEarbudBatteryLevels(context: Context, device: BluetoothDevice): EarbudBatteryLevels? {
    val connection = Channel<Boolean>(Channel.CONFLATED)
    val discovery = Channel<Boolean>(Channel.CONFLATED)
    val descriptorValues = Channel<ByteArray?>(Channel.CONFLATED)
    val characteristicValues = Channel<ByteArray?>(Channel.CONFLATED)

    val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                connection.trySend(true)
            } else {
                connection.close()
                discovery.close()
                descriptorValues.close()
                characteristicValues.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            discovery.trySend(status == BluetoothGatt.GATT_SUCCESS)
        }

        @Deprecated("Used on Android 12L and earlier")
        @Suppress("DEPRECATION")
        override fun onDescriptorRead(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            descriptorValues.trySend(descriptor.value?.copyOf().takeIf { status == BluetoothGatt.GATT_SUCCESS })
        }

        override fun onDescriptorRead(
            gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int, value: ByteArray,
        ) {
            descriptorValues.trySend(value.takeIf { status == BluetoothGatt.GATT_SUCCESS })
        }

        @Deprecated("Used on Android 12L and earlier")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            characteristicValues.trySend(characteristic.value?.copyOf().takeIf { status == BluetoothGatt.GATT_SUCCESS })
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int,
        ) {
            characteristicValues.trySend(value.takeIf { status == BluetoothGatt.GATT_SUCCESS })
        }
    }

    var left: Int? = null
    var right: Int? = null
    try {
        withTimeoutOrNull(EARBUD_BATTERY_READ_TIMEOUT_MS) {
            val gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
                ?: return@withTimeoutOrNull
            try {
                if (connection.receiveCatching().getOrNull() != true ||
                    !gatt.discoverServices() || discovery.receiveCatching().getOrNull() != true
                ) return@withTimeoutOrNull

                for (service in gatt.services) {
                    if (service.uuid != batteryServiceUuid) continue
                    val level = service.getCharacteristic(batteryLevelUuid) ?: continue
                    val format = level.getDescriptor(presentationFormatUuid) ?: continue
                    if (!gatt.readDescriptor(format)) continue
                    val side = descriptorValues.receiveCatching().getOrNull()
                        ?.let(::earbudSideFromPresentation) ?: continue
                    if (!gatt.readCharacteristic(level)) continue
                    val percent = characteristicValues.receiveCatching().getOrNull()
                        ?.let(::earbudBatteryPercent) ?: continue
                    when (side) {
                        EarbudSide.LEFT -> left = percent
                        EarbudSide.RIGHT -> right = percent
                    }
                    if (left != null && right != null) break
                }
            } finally {
                gatt.close()
            }
        }
    } catch (_: SecurityException) {
        // Nearby Devices permission can be revoked while a read is in progress.
    }
    return if (left != null || right != null) EarbudBatteryLevels(left, right) else null
}
