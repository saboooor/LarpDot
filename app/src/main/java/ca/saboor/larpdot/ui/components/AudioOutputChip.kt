package ca.saboor.larpdot.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
) {
    val activeOutput by AudioOutputTracker.activeOutput.collectAsState()
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.22f),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = audioOutputIcon(activeOutput),
                contentDescription = "Audio output",
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

private fun audioOutputIcon(device: AudioOutputDevice) = when (device.kind) {
    AudioOutputKind.PHONE_SPEAKER -> LarpIcons.PhoneAndroid
    AudioOutputKind.BLUETOOTH_AUDIO -> LarpIcons.BluetoothAudio
    AudioOutputKind.WIRED_HEADSET -> LarpIcons.Headphones
    AudioOutputKind.USB_AUDIO -> LarpIcons.Usb
    AudioOutputKind.CAR_AUDIO -> LarpIcons.DirectionsCar
    AudioOutputKind.HEARING_AID -> LarpIcons.Hearing
    AudioOutputKind.EXTERNAL_DISPLAY -> LarpIcons.Tv
}
