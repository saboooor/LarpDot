package ca.saboor.larpdot.ui.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ca.saboor.larpdot.service.CustomIsland
import ca.saboor.larpdot.service.CustomIslandIcon
import ca.saboor.larpdot.service.CustomIslandSlot
import ca.saboor.larpdot.service.CustomIslandWidgetView
import ca.saboor.larpdot.service.CustomIslandWidgets
import ca.saboor.larpdot.service.WidgetColorKey
import ca.saboor.larpdot.ui.components.WidgetPreviewSampler
import ca.saboor.larpdot.ui.theme.LarpIcons
import kotlinx.coroutines.delay

internal val customIslandCardPadding = 8.dp
internal val customWidgetWingInset = 4.dp

internal fun customIslandExpandedPadding(cameraClearance: Dp, spanFullIsland: Boolean = false) =
    if (spanFullIsland) {
        PaddingValues(0.dp)
    } else {
        PaddingValues(
            start = customIslandCardPadding,
            end = customIslandCardPadding,
            top = if (cameraClearance > 0.dp) maxOf(customIslandCardPadding, cameraClearance + 4.dp) else customIslandCardPadding,
            bottom = customIslandCardPadding,
        )
    }

private fun customWingOuterInset(slot: CustomIslandSlot, thickness: Dp): Dp =
    if (slot.widgetId != null) customWidgetWingInset else compactEndInset(thickness, secondaryBubbleIconSize(thickness))

internal fun customIslandCompactExtra(island: CustomIsland?, thickness: Dp): Dp {
    if (island == null) return 0.dp
    fun width(slot: CustomIslandSlot): Dp {
        val contentWidth = if (slot.widgetId != null) slot.width.dp else secondaryBubbleIconSize(thickness)
        return contentWidth + customWingOuterInset(slot, thickness)
    }
    return maxOf(width(island.left), width(island.right)) * 2
}

internal val CustomIslandIcon.image: ImageVector
    get() = when (this) {
        CustomIslandIcon.APPS -> LarpIcons.Apps
        CustomIslandIcon.MUSIC -> LarpIcons.MusicNote
        CustomIslandIcon.FLASHLIGHT -> LarpIcons.FlashlightOn
        CustomIslandIcon.BATTERY -> LarpIcons.BatteryChargingFull
        CustomIslandIcon.HEADPHONES -> LarpIcons.Headphones
        CustomIslandIcon.TIMER -> LarpIcons.Timer
        CustomIslandIcon.CALL -> LarpIcons.Call
        CustomIslandIcon.STAR -> LarpIcons.StarBorder
        CustomIslandIcon.NAVIGATION -> LarpIcons.Navigation
        CustomIslandIcon.NOTIFICATIONS -> LarpIcons.Notifications
        CustomIslandIcon.HOME -> LarpIcons.Home
        CustomIslandIcon.PHOTO -> LarpIcons.PhotoLibrary
        CustomIslandIcon.ANDROID -> LarpIcons.Android
        CustomIslandIcon.AUDIO_TRACK -> LarpIcons.Audiotrack
        CustomIslandIcon.BATTERY_ALERT -> LarpIcons.BatteryAlert
        CustomIslandIcon.BATTERY_SAVER -> LarpIcons.BatterySaver
        CustomIslandIcon.BLUETOOTH -> LarpIcons.Bluetooth
        CustomIslandIcon.BRIGHTNESS -> LarpIcons.BrightnessMedium
        CustomIslandIcon.BUBBLES -> LarpIcons.BubbleChart
        CustomIslandIcon.BUG -> LarpIcons.BugReport
        CustomIslandIcon.CAR -> LarpIcons.DirectionsCar
        CustomIslandIcon.HOURGLASS -> LarpIcons.HourglassEmpty
        CustomIslandIcon.DOWNLOAD -> LarpIcons.Download
        CustomIslandIcon.EARBUDS -> LarpIcons.Earbuds
        CustomIslandIcon.GAME -> LarpIcons.SportsEsports
        CustomIslandIcon.HEARING -> LarpIcons.Hearing
        CustomIslandIcon.KEYBOARD -> LarpIcons.Keyboard
        CustomIslandIcon.LAPTOP -> LarpIcons.Laptop
        CustomIslandIcon.LAYERS -> LarpIcons.Layers
        CustomIslandIcon.LOCK -> LarpIcons.Lock
        CustomIslandIcon.MIC -> LarpIcons.HeadsetMic
        CustomIslandIcon.MOUSE -> LarpIcons.Mouse
        CustomIslandIcon.PALETTE -> LarpIcons.Palette
        CustomIslandIcon.PAUSE -> LarpIcons.Pause
        CustomIslandIcon.PHONE -> LarpIcons.PhoneAndroid
        CustomIslandIcon.PIN -> LarpIcons.PushPin
        CustomIslandIcon.PLAY -> LarpIcons.PlayArrow
        CustomIslandIcon.POWER -> LarpIcons.PowerSettingsNew
        CustomIslandIcon.SCREENSHOT -> LarpIcons.Screenshot
        CustomIslandIcon.SEARCH -> LarpIcons.Search
        CustomIslandIcon.SECURITY -> LarpIcons.Security
        CustomIslandIcon.SETTINGS -> LarpIcons.Settings
        CustomIslandIcon.SHARE -> LarpIcons.Share
        CustomIslandIcon.SHUFFLE -> LarpIcons.Shuffle
        CustomIslandIcon.SPEAKER -> LarpIcons.Speaker
        CustomIslandIcon.SPEED -> LarpIcons.Speed
        CustomIslandIcon.SUN -> LarpIcons.WbSunny
        CustomIslandIcon.TV -> LarpIcons.Tv
        CustomIslandIcon.UPLOAD -> LarpIcons.Upload
        CustomIslandIcon.USB -> LarpIcons.Usb
        CustomIslandIcon.VIBRATION -> LarpIcons.Vibration
        CustomIslandIcon.VOLUME -> LarpIcons.VolumeUp
        CustomIslandIcon.VOLUME_OFF -> LarpIcons.VolumeOff
        CustomIslandIcon.WATCH -> LarpIcons.Watch
        CustomIslandIcon.WAVEFORM -> LarpIcons.GraphicEq
    }

@Composable
internal fun CustomIslandCompactContent(
    island: CustomIsland,
    thickness: Dp,
    isLandscape: Boolean,
    trailingWingFraction: Float,
) {
    val iconSize = secondaryBubbleIconSize(thickness)
    CustomIslandWings(
        diameter = thickness,
        island = island,
        modifier = if (isLandscape) Modifier.verticalActivityLabel() else Modifier,
        trailingWingFraction = if (isLandscape) 0.5f else trailingWingFraction,
        left = {
            if (island.left.hasContent) {
                CustomIslandWing(island.left, iconSize)
            } else if (!island.right.hasContent) {
                Icon(LarpIcons.Apps, contentDescription = island.name.ifBlank { "Custom island" }, modifier = Modifier.size(iconSize))
            }
        },
        right = { CustomIslandWing(island.right, iconSize) },
    )
}

@Composable
internal fun CustomIslandWings(
    diameter: Dp,
    island: CustomIsland,
    modifier: Modifier = Modifier,
    trailingWingFraction: Float = 0.5f,
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.weight(1f - trailingWingFraction).fillMaxHeight()
                    .padding(start = customWingOuterInset(island.left, diameter)),
                contentAlignment = Alignment.CenterStart,
            ) {
                left()
            }
            Spacer(Modifier.width(diameter))
            Box(
                Modifier.weight(trailingWingFraction).fillMaxHeight()
                    .padding(end = customWingOuterInset(island.right, diameter)),
                contentAlignment = Alignment.CenterEnd,
            ) {
                right()
            }
        }
    }
}

@Composable
private fun CustomIslandWing(slot: CustomIslandSlot, iconSize: Dp) {
    val widget = slot.widgetId
    if (widget != null) {
        CustomIslandWidget(
            widget,
            interactive = false,
            modifier = Modifier.width(slot.width.dp).fillMaxHeight().padding(vertical = customWidgetWingInset),
            colorKey = slot.colorKey,
        )
    } else if (slot.icon != null) {
        Icon(
            slot.icon.image,
            contentDescription = slot.icon.label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
internal fun ExpandedCustomIslandContent(island: CustomIsland, topInset: Dp, interactive: Boolean) {
    val widget = island.main.widgetId ?: return
    CustomIslandWidget(
        widget,
        interactive,
        modifier = Modifier.fillMaxSize().padding(customIslandExpandedPadding(topInset, island.main.spanFullIsland)),
        colorKey = island.main.colorKey,
    )
}

@Composable
internal fun CustomIslandWidget(
    widgetId: Int,
    interactive: Boolean,
    modifier: Modifier,
    colorKey: WidgetColorKey?,
    sampler: WidgetPreviewSampler? = null,
    updateProviderSize: Boolean = true,
) {
    key(widgetId) {
        val info = remember(widgetId) { CustomIslandWidgets.info(widgetId) }
        if (info == null) {
            sampler?.hostView = null
            Box(modifier, contentAlignment = Alignment.Center) {
                if (interactive) Text("Widget unavailable", style = MaterialTheme.typography.labelMedium)
                else Icon(LarpIcons.Apps, contentDescription = "Widget unavailable", modifier = Modifier.size(16.dp))
            }
        } else {
            val density = LocalDensity.current
            var measuredSize by remember { mutableStateOf(IntSize.Zero) }
            DisposableEffect(widgetId) {
                CustomIslandWidgets.startShowing()
                onDispose { CustomIslandWidgets.stopShowing() }
            }
            if (updateProviderSize) {
                LaunchedEffect(widgetId, measuredSize, density) {
                    if (measuredSize == IntSize.Zero) return@LaunchedEffect
                    // Providers only need the settled widget dimensions, even as the island animates.
                    delay(120)
                    CustomIslandWidgets.updateSize(
                        widgetId,
                        with(density) { measuredSize.width.toDp().value },
                        with(density) { measuredSize.height.toDp().value },
                    )
                }
            }
            AndroidView(
                factory = { viewContext ->
                    val hostContext = if (interactive) viewContext else viewContext.applicationContext
                    CustomIslandWidgets.createView(hostContext, widgetId, info).also {
                        sampler?.hostView = it
                    }
                },
                modifier = modifier.onSizeChanged { measuredSize = it },
                update = { widget ->
                    sampler?.hostView = widget
                    widget.interactive = interactive
                    widget.setColorKey(colorKey)
                },
                onRelease = { widget ->
                    CustomIslandWidgets.releaseView(widgetId, widget)
                    if (sampler?.hostView === widget) {
                        sampler.hostView = null
                        sampler.releaseSnapshot()
                    }
                },
            )
        }
    }
}
