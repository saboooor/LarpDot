package ca.saboor.larpdot.ui.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import ca.saboor.larpdot.service.CustomColorMode
import ca.saboor.larpdot.service.CustomIsland
import ca.saboor.larpdot.service.CustomIslandIcon
import ca.saboor.larpdot.service.CustomIslandImage
import ca.saboor.larpdot.service.CustomIslandSlot
import ca.saboor.larpdot.service.CustomIslandSources
import ca.saboor.larpdot.service.CustomIslandTapAction
import ca.saboor.larpdot.service.CustomIslandWidgetView
import ca.saboor.larpdot.service.CustomIslandWidgets
import ca.saboor.larpdot.service.CustomTextAlignment
import ca.saboor.larpdot.service.WidgetColorKey
import ca.saboor.larpdot.ui.components.WidgetPreviewSampler
import ca.saboor.larpdot.ui.components.resolveCustomIslandColor
import ca.saboor.larpdot.ui.theme.LarpIcons
import kotlinx.coroutines.delay

internal val customWidgetWingInset = 4.dp

internal fun customIslandExpandedPadding(cameraClearance: Dp, spanFullIsland: Boolean = false, x: Dp = 8.dp, y: Dp = 8.dp) =
    PaddingValues(
        start = x,
        end = x,
        top = if (spanFullIsland) y else maxOf(y, cameraClearance),
        bottom = y,
    )

private val customTextWingInset = 10.dp

private fun customWingOuterInset(slot: CustomIslandSlot, thickness: Dp): Dp = when {
    slot.widgetId != null -> customWidgetWingInset
    !slot.text.isNullOrBlank() -> customTextWingInset
    else -> compactEndInset(thickness, secondaryBubbleIconSize(thickness))
}

internal fun customIslandCompactExtra(island: CustomIsland?, thickness: Dp): Dp {
    if (island == null) return 0.dp
    fun width(slot: CustomIslandSlot): Dp {
        val contentWidth = if (slot.widgetId != null || !slot.text.isNullOrBlank()) {
            slot.width.dp
        } else {
            secondaryBubbleIconSize(thickness)
        }
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

internal enum class CustomWingSide { LEFT, RIGHT }

internal data class CustomWingMorphLinks(
    val leftCompactTarget: CustomWingSide? = null,
    val rightCompactTarget: CustomWingSide? = null,
)

internal fun CustomIslandSlot.hasSameWingContentAs(other: CustomIslandSlot): Boolean =
    (icon != null && icon == other.icon) ||
        (image != null && image == other.image) ||
        (!text.isNullOrBlank() && text == other.text)

internal fun resolveCustomWingMorphLinks(
    island: CustomIsland?,
    fromSecondaryBubble: Boolean,
): CustomWingMorphLinks {
    if (island == null || island.isSwipeDownOnly) return CustomWingMorphLinks()
    if (fromSecondaryBubble) {
        val secondarySource = listOf(CustomWingSide.LEFT to island.left, CustomWingSide.RIGHT to island.right)
            .firstOrNull { (_, slot) -> slot.icon != null || slot.image != null || !slot.text.isNullOrBlank() }
            ?: return CustomWingMorphLinks()
        val target = when {
            secondarySource.second.hasSameWingContentAs(island.expandedLeft) -> CustomWingSide.LEFT
            secondarySource.second.hasSameWingContentAs(island.expandedRight) -> CustomWingSide.RIGHT
            else -> null
        }
        return when (secondarySource.first) {
            CustomWingSide.LEFT -> CustomWingMorphLinks(leftCompactTarget = target)
            CustomWingSide.RIGHT -> CustomWingMorphLinks(rightCompactTarget = target)
        }
    }
    var leftTarget = if (island.left.hasSameWingContentAs(island.expandedLeft)) CustomWingSide.LEFT else null
    var rightTarget = if (island.right.hasSameWingContentAs(island.expandedRight)) CustomWingSide.RIGHT else null
    if (leftTarget == null && rightTarget != CustomWingSide.RIGHT && island.left.hasSameWingContentAs(island.expandedRight)) {
        leftTarget = CustomWingSide.RIGHT
    }
    if (rightTarget == null && leftTarget != CustomWingSide.LEFT && island.right.hasSameWingContentAs(island.expandedLeft)) {
        rightTarget = CustomWingSide.LEFT
    }
    return CustomWingMorphLinks(leftCompactTarget = leftTarget, rightCompactTarget = rightTarget)
}

@Composable
internal fun CustomWingText(
    slot: CustomIslandSlot,
    placeholderValues: Map<String, String>,
    preview: Boolean,
    maxLines: Int,
    modifier: Modifier = Modifier,
    dominantColor: Color? = null,
) {
    val raw = slot.text.orEmpty()
    val resolved = remember(raw, placeholderValues, preview) {
        val sourceText = raw.ifBlank { if (preview) "Text" else "" }
        customIslandText(sourceText, placeholderValues)
    }
    val style = slot.textStyle
    val textColor = resolveCustomIslandColor(style.colorChoice, MaterialTheme.colorScheme.primary, dominantColor)
    Text(
        text = resolved,
        maxLines = maxLines,
        softWrap = maxLines > 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelMedium.copy(
            fontSize = style.size.sp,
            lineHeight = (style.size * 1.25f).sp,
            fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Medium,
            fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
            textDecoration = if (style.underline) TextDecoration.Underline else TextDecoration.None,
            textAlign = when (style.alignment) {
                CustomTextAlignment.START -> TextAlign.Start
                CustomTextAlignment.CENTER -> TextAlign.Center
                CustomTextAlignment.END -> TextAlign.End
            },
        ),
        color = textColor,
        modifier = modifier,
    )
}

@Composable
internal fun CustomIslandCompactContent(
    island: CustomIsland,
    thickness: Dp,
    isLandscape: Boolean,
    trailingWingFraction: Float,
    leftAnchor: IslandElementAnchor? = null,
    rightAnchor: IslandElementAnchor? = null,
) {
    val iconSize = secondaryBubbleIconSize(thickness)
    val needsDominant = island.left.iconColor.mode == CustomColorMode.DOMINANT ||
        island.right.iconColor.mode == CustomColorMode.DOMINANT ||
        island.left.textStyle.colorMode == CustomColorMode.DOMINANT ||
        island.right.textStyle.colorMode == CustomColorMode.DOMINANT
    val needsSources = needsDominant || island.left.image != null || island.right.image != null ||
        island.left.text != null || island.right.text != null
    val sources = if (needsSources) customIslandSources(island) else null
    val dominantColor = if (needsDominant) rememberCustomIslandDominantColor(island, sources).first else null
    val placeholderValues = if (sources != null && (island.left.text != null || island.right.text != null)) {
        rememberCustomIslandPlaceholderValues(island, sources, preview = false)
    } else emptyMap()
    val appOverride = island.tapAppPackage.takeIf { island.tapAction == CustomIslandTapAction.OPEN_APP }
    CustomIslandWings(
        diameter = thickness,
        island = island,
        modifier = if (isLandscape) Modifier.verticalActivityLabel() else Modifier,
        trailingWingFraction = if (isLandscape) 0.5f else trailingWingFraction,
        left = {
            if (island.left.hasContent) {
                CustomIslandWing(island.left, iconSize, sources, placeholderValues, appOverride, dominantColor, leftAnchor)
            } else if (!island.right.hasContent) {
                Icon(LarpIcons.Apps, contentDescription = island.name.ifBlank { "Custom island" }, modifier = Modifier.size(iconSize))
            }
        },
        right = { CustomIslandWing(island.right, iconSize, sources, placeholderValues, appOverride, dominantColor, rightAnchor) },
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
private fun CustomIslandWing(
    slot: CustomIslandSlot,
    iconSize: Dp,
    sources: CustomIslandSources?,
    placeholderValues: Map<String, String>,
    appOverride: String?,
    dominantColor: Color? = null,
    anchor: IslandElementAnchor? = null,
) {
    val widget = slot.widgetId
    val iconTint = resolveCustomIslandColor(slot.iconColor, MaterialTheme.colorScheme.primary, dominantColor)
    if (widget != null) {
        CustomIslandWidget(
            widget,
            interactive = false,
            modifier = Modifier.width(slot.width.dp).fillMaxHeight().padding(vertical = customWidgetWingInset),
            colorKey = slot.colorKey,
        )
    } else if (!slot.text.isNullOrBlank()) {
        CustomWingText(
            slot = slot,
            placeholderValues = placeholderValues,
            preview = false,
            maxLines = 1,
            dominantColor = dominantColor,
            modifier = Modifier.widthIn(max = slot.width.dp).morphingElement(anchor),
        )
    } else if (slot.image != null && sources != null) {
        CustomIslandImageContent(
            slot.image,
            sources,
            Modifier.size(iconSize).morphingElement(anchor),
            compact = true,
            preview = false,
            appOverride = appOverride,
            tint = if (slot.image == CustomIslandImage.NOTIFICATION_ICON) iconTint else null,
        )
    } else if (slot.icon != null) {
        Icon(
            slot.icon.image,
            contentDescription = slot.icon.label,
            tint = iconTint,
            modifier = Modifier.size(iconSize).morphingElement(anchor),
        )
    }
}

@Composable
internal fun CustomExpandedWingIcon(
    slot: CustomIslandSlot,
    sources: CustomIslandSources?,
    appOverride: String?,
    preview: Boolean,
    modifier: Modifier = Modifier,
    placeholderValues: Map<String, String> = emptyMap(),
    dominantColor: Color? = null,
    anchor: IslandElementAnchor? = null,
) {
    val iconModifier = modifier.size(44.dp).morphingElement(anchor)
    val iconTint = resolveCustomIslandColor(slot.iconColor, MaterialTheme.colorScheme.primary, dominantColor)
    when {
        slot.text != null && (preview || slot.text.isNotBlank()) -> CustomWingText(
            slot = slot,
            placeholderValues = placeholderValues,
            preview = preview,
            maxLines = 2,
            dominantColor = dominantColor,
            modifier = modifier.fillMaxWidth().padding(horizontal = 2.dp).morphingElement(anchor),
        )
        slot.image != null && sources != null -> CustomIslandImageContent(
            source = slot.image,
            sources = sources,
            modifier = iconModifier,
            compact = false,
            preview = preview,
            appOverride = appOverride,
            tint = if (slot.image == CustomIslandImage.NOTIFICATION_ICON) iconTint else null,
        )
        slot.icon != null -> Icon(
            imageVector = slot.icon.image,
            contentDescription = slot.icon.label,
            tint = iconTint,
            modifier = iconModifier,
        )
        preview -> Icon(
            imageVector = LarpIcons.Add,
            contentDescription = "Empty",
            tint = MaterialTheme.colorScheme.outline,
            modifier = modifier.size(22.dp),
        )
    }
}

@Composable
internal fun ExpandedCustomIslandContent(
    island: CustomIsland,
    topInset: Dp,
    interactive: Boolean,
    modifier: Modifier = Modifier,
    leftWingAnchor: IslandElementAnchor? = null,
    rightWingAnchor: IslandElementAnchor? = null,
) {
    val slot = island.main
    val hasWingIcons = island.expandedLeft.hasContent || island.expandedRight.hasContent
    if (!hasWingIcons) {
        CustomExpandedComponents(
            island,
            modifier.fillMaxWidth().padding(customIslandExpandedPadding(topInset, slot.spanFullIsland, slot.paddingX.dp, slot.paddingY.dp)),
            interactive,
            preview = false,
        )
        return
    }
    val needsDominant = island.expandedLeft.iconColor.mode == CustomColorMode.DOMINANT ||
        island.expandedRight.iconColor.mode == CustomColorMode.DOMINANT ||
        island.expandedLeft.textStyle.colorMode == CustomColorMode.DOMINANT ||
        island.expandedRight.textStyle.colorMode == CustomColorMode.DOMINANT
    val needsSources = needsDominant || island.expandedLeft.image != null || island.expandedRight.image != null ||
        island.expandedLeft.text != null || island.expandedRight.text != null
    val sources = if (needsSources) customIslandSources(island) else null
    val dominantColor = if (needsDominant) rememberCustomIslandDominantColor(island, sources).first else null
    val placeholderValues = if (sources != null && (island.expandedLeft.text != null || island.expandedRight.text != null)) {
        rememberCustomIslandPlaceholderValues(island, sources, preview = false)
    } else emptyMap()
    val appOverride = island.tapAppPackage.takeIf { island.tapAction == CustomIslandTapAction.OPEN_APP }
    val centerTopPadding = if (slot.spanFullIsland) 0.dp else maxOf(slot.paddingY.dp, topInset)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(
                start = slot.paddingX.dp,
                end = slot.paddingX.dp,
                top = if (slot.spanFullIsland) slot.paddingY.dp else 0.dp,
                bottom = slot.paddingY.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (island.expandedLeft.hasContent) {
            val leftWidth = if (island.expandedLeft.text != null) island.expandedLeft.width.coerceAtLeast(48).dp else 48.dp
            Box(
                modifier = Modifier
                    .width(leftWidth)
                    .padding(top = if (slot.spanFullIsland) 0.dp else slot.paddingY.dp),
                contentAlignment = Alignment.Center,
            ) {
                CustomExpandedWingIcon(
                    island.expandedLeft,
                    sources,
                    appOverride,
                    preview = false,
                    placeholderValues = placeholderValues,
                    dominantColor = dominantColor,
                    anchor = leftWingAnchor,
                )
            }
        }
        if (island.expandedComponents.isNotEmpty()) {
            CustomExpandedComponents(
                island = island,
                modifier = Modifier.weight(1f).padding(top = centerTopPadding),
                interactive = interactive,
                preview = false,
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (island.expandedRight.hasContent) {
            val rightWidth = if (island.expandedRight.text != null) island.expandedRight.width.coerceAtLeast(48).dp else 48.dp
            Box(
                modifier = Modifier
                    .width(rightWidth)
                    .padding(top = if (slot.spanFullIsland) 0.dp else slot.paddingY.dp),
                contentAlignment = Alignment.Center,
            ) {
                CustomExpandedWingIcon(
                    island.expandedRight,
                    sources,
                    appOverride,
                    preview = false,
                    placeholderValues = placeholderValues,
                    dominantColor = dominantColor,
                    anchor = rightWingAnchor,
                )
            }
        }
    }
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
