package ca.saboor.larpdot.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.service.AudioOutputDevice
import ca.saboor.larpdot.service.AudioOutputKind
import ca.saboor.larpdot.service.AudioOutputTracker
import ca.saboor.larpdot.ui.theme.LarpIcons

@Composable
fun AudioOutputChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val activeOutput by AudioOutputTracker.activeOutput.collectAsState()
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.22f),
            modifier = if (highlighted) modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape) else modifier,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = audioOutputIcon(activeOutput),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = activeOutput.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = MaterialTheme.typography.bodyMedium.fontWeight,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun AudioOutputButton(onClick: () -> Unit, highlighted: Boolean) {
    val activeOutput by AudioOutputTracker.activeOutput.collectAsState()
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        shape = shape,
        color = Color.White.copy(alpha = 0.22f),
        modifier = Modifier.size(36.dp).then(
            if (highlighted) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, shape) else Modifier,
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = audioOutputIcon(activeOutput),
                contentDescription = "Playing on ${activeOutput.name}",
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun audioOutputIcon(device: AudioOutputDevice) = when (device.kind) {
    AudioOutputKind.PHONE_SPEAKER -> LarpIcons.PhoneAndroid
    AudioOutputKind.BLUETOOTH_AUDIO -> LarpIcons.BluetoothAudio
    AudioOutputKind.WIRED_HEADSET -> LarpIcons.Headphones
    AudioOutputKind.USB_AUDIO -> LarpIcons.Usb
    AudioOutputKind.CAR_AUDIO -> LarpIcons.DirectionsCar
    AudioOutputKind.HEARING_AID -> LarpIcons.Hearing
    AudioOutputKind.EXTERNAL_DISPLAY -> LarpIcons.Tv
}
