package ca.saboor.larpdot.ui.overlay

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ca.saboor.larpdot.media.MediaPlaybackState
import ca.saboor.larpdot.notification.NotificationActivityKind
import ca.saboor.larpdot.service.CustomColorMode
import ca.saboor.larpdot.service.CustomComponentKind
import ca.saboor.larpdot.service.CustomTextAlignment
import ca.saboor.larpdot.service.CustomTextStyle
import ca.saboor.larpdot.service.CustomIsland
import ca.saboor.larpdot.service.CustomIslandCondition
import ca.saboor.larpdot.service.CustomIslandSources
import ca.saboor.larpdot.service.CustomIslandVisibility
import ca.saboor.larpdot.service.performCurrentNotificationAction
import ca.saboor.larpdot.service.CustomIslandWidgets
import ca.saboor.larpdot.ui.components.WidgetProviderPreview
import ca.saboor.larpdot.ui.components.resolveCustomIslandColor
import kotlin.math.roundToInt

private val componentSpacing = 6.dp
private val placeholder = Regex("""\{([a-z_]+)\}""")

internal fun customIslandPlaceholders(island: CustomIsland): List<String> {
    if (island.visibility != CustomIslandVisibility.CONDITIONAL) return emptyList()
    return when (island.condition) {
        CustomIslandCondition.MUSIC -> listOf("song_title", "artist", "album", "app")
        CustomIslandCondition.NOTIFICATION -> listOf("title", "description", "app")
        CustomIslandCondition.LIVE_ACTIVITY -> buildList {
            addAll(listOf("title", "description", "app", "progress"))
            if (island.activityKind == null || island.activityKind == NotificationActivityKind.BATTERY_CHARGING ||
                island.activityKind == NotificationActivityKind.BATTERY_LOW) add("battery_percentage")
            if (island.activityKind == null || island.activityKind == NotificationActivityKind.BLUETOOTH ||
                island.activityKind == NotificationActivityKind.AUDIO_OUTPUT) add("device_battery")
        }
        CustomIslandCondition.FLASHLIGHT -> emptyList()
    }
}

internal fun customIslandText(template: String, values: Map<String, String>) =
    placeholder.replace(template) { values[it.groupValues[1]] ?: it.value }

@Composable
internal fun rememberCustomIslandPlaceholderValues(
    island: CustomIsland,
    sources: CustomIslandSources,
    preview: Boolean,
): Map<String, String> {
    val media = sources.media
    val source = island.condition.takeIf { island.visibility == CustomIslandVisibility.CONDITIONAL }
    val activity = sources.activity
    val notification = sources.notification
    val packages = LocalContext.current.packageManager
    val notificationApp = remember(notification?.packageName, packages) {
        notification?.packageName?.let { name ->
            runCatching { packages.getApplicationLabel(packages.getApplicationInfo(name, 0)).toString() }.getOrDefault(name)
        }
    }
    return customIslandPlaceholders(island).associateWith { name ->
        when (name) {
            "song_title" -> media.title
            "artist" -> media.artist
            "album" -> media.album
            "title" -> notification?.title ?: activity?.title.orEmpty()
            "description" -> notification?.text ?: activity?.text.orEmpty()
            "app" -> notificationApp ?: activity?.appName
                ?: if (source == CustomIslandCondition.MUSIC) media.appName.orEmpty() else ""
            "progress" -> activity?.progressFraction?.let { "${(it * 100).roundToInt()}%" }.orEmpty()
            "battery_percentage" -> activity?.takeIf {
                it.kind == NotificationActivityKind.BATTERY_CHARGING || it.kind == NotificationActivityKind.BATTERY_LOW
            }?.progress?.let { "$it%" }.orEmpty()
            "device_battery" -> activity?.bluetoothBatteryPercent?.let { "$it%" }.orEmpty()
            else -> ""
        }.ifBlank { if (preview) "{$name}" else "" }
    }
}

@Composable
internal fun CustomExpandedComponents(
    island: CustomIsland,
    modifier: Modifier,
    interactive: Boolean,
    preview: Boolean,
    selectedComponentId: String? = null,
    onSelectComponent: ((String) -> Unit)? = null,
) {
    val sources = customIslandSources(island)
    val needsDominant = island.expandedComponents.any {
        it.kind == CustomComponentKind.TEXT && it.textStyle?.colorMode == CustomColorMode.DOMINANT
    }
    val dominantColor = if (needsDominant) rememberCustomIslandDominantColor(island, sources).first else null
    val media = sources.media
    val source = island.condition.takeIf { island.visibility == CustomIslandVisibility.CONDITIONAL }
    val values = rememberCustomIslandPlaceholderValues(island, sources, preview)
    val actions = sources.notificationActions

    Column(
        modifier = modifier.verticalScroll(rememberScrollState(), enabled = interactive || preview),
        verticalArrangement = Arrangement.spacedBy(componentSpacing),
    ) {
        island.expandedComponents.forEach { component ->
            key(component.id) {
                val content: @Composable () -> Unit = {
                    when (component.kind) {
                        CustomComponentKind.TEXT -> {
                            val format = component.textStyle
                            val text = remember(component.text, format, values, preview) {
                                val sourceText = component.text.ifBlank { if (preview) "Text" else "" }
                                if (format == null) renderCustomIslandMarkdown(sourceText, values)
                                else AnnotatedString(customIslandText(sourceText, values))
                            }
                            val style = format ?: CustomTextStyle()
                            val textColor = resolveCustomIslandColor(
                                style.colorChoice,
                                MaterialTheme.colorScheme.primary,
                                dominantColor,
                            )
                            Text(
                                text,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = style.size.sp,
                                    lineHeight = (style.size * 1.4f).sp,
                                    fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
                                    fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
                                    textDecoration = if (style.underline) TextDecoration.Underline else TextDecoration.None,
                                    textAlign = when (style.alignment) {
                                        CustomTextAlignment.START -> TextAlign.Start
                                        CustomTextAlignment.CENTER -> TextAlign.Center
                                        CustomTextAlignment.END -> TextAlign.End
                                    },
                                ),
                                color = textColor,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        CustomComponentKind.WIDGET -> component.widgetId?.let { id ->
                            val widgetModifier = Modifier.fillMaxWidth().height(component.height.dp)
                            if (preview) {
                                val provider = remember(id) { CustomIslandWidgets.info(id) }
                                if (provider != null) WidgetProviderPreview(provider, widgetModifier, component.colorKey)
                                else Text("Widget unavailable", color = Color.White, modifier = widgetModifier)
                            } else {
                                CustomIslandWidget(id, interactive, widgetModifier, component.colorKey)
                            }
                        }
                        CustomComponentKind.ALBUM_ART, CustomComponentKind.NOTIFICATION_IMAGE -> {
                            val image = requireNotNull(component.kind.image)
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                val imageModifier = if (component.kind == CustomComponentKind.ALBUM_ART) {
                                    Modifier.size(component.height.dp)
                                } else Modifier.fillMaxWidth().height(component.height.dp)
                                CustomIslandImageContent(image, sources, imageModifier, compact = false, preview = preview)
                            }
                        }
                        CustomComponentKind.NOTIFICATION_CONTENT -> {
                            CustomNotificationContentView(
                                remoteViews = sources.notificationContentView,
                                interactive = interactive && !preview,
                                preview = preview,
                                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                            )
                        }
                        CustomComponentKind.BUTTONS -> {
                            val style = component.buttonStyle
                            val accent = if (media.albumArt != null) media.dominantColor else MaterialTheme.colorScheme.primary
                            if (source == CustomIslandCondition.MUSIC) {
                                BoxWithConstraints(Modifier.fillMaxWidth()) {
                                    val spacing = if (style.material3ButtonGroup) 3.dp else 6.dp
                                    val skipWidth = minOf(48.dp, (maxWidth - spacing * 2).coerceAtLeast(0.dp) / 3)
                                    val playWidth = minOf(60.dp, (maxWidth - spacing * 2 - skipWidth * 2).coerceAtLeast(0.dp))
                                    ExpandedPlaybackControlsRow(
                                        isPlaying = media.isPlaying,
                                        accentColor = accent,
                                        style = style,
                                        skipWidth = skipWidth,
                                        skipHeight = 44.dp,
                                        skipIconSize = 20.dp,
                                        playWidth = playWidth,
                                        playHeight = 44.dp,
                                        playIconSize = 24.dp,
                                        defaultSpacing = 6.dp,
                                        bpmPulseScale = { 1f },
                                        onPrevious = MediaPlaybackState::skipPrevious,
                                        onPlayPause = MediaPlaybackState::togglePlayPause,
                                        onNext = MediaPlaybackState::skipNext,
                                        enabled = interactive,
                                    )
                                }
                            } else {
                                val visuals = resolvePlaybackButtonVisuals(style, accent)
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState(), enabled = interactive),
                                    horizontalArrangement = Arrangement.spacedBy(if (style.material3ButtonGroup) 3.dp else 6.dp),
                                ) {
                                    actions.forEachIndexed { index, action ->
                                        val shape = when {
                                            actions.size == 1 -> RoundedCornerShape(visuals.outerRadius)
                                            index == 0 -> visuals.prevShape
                                            index == actions.lastIndex -> visuals.nextShape
                                            style.material3ButtonGroup -> visuals.playShape
                                            else -> RoundedCornerShape(visuals.outerRadius)
                                        }
                                        Surface(
                                            enabled = interactive,
                                            shape = shape,
                                            color = visuals.skipBgColor,
                                            onClick = {
                                                island.performCurrentNotificationAction(sources.notificationKey, action)
                                            },
                                        ) {
                                            Text(
                                                action.title,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                color = Color.White,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                    if (actions.isEmpty() && preview) Text("Activity buttons", color = Color.White)
                                }
                            }
                        }
                    }
                }
                if (preview && onSelectComponent != null) {
                    val selected = selectedComponentId == component.id
                    val border = if (selected) MaterialTheme.colorScheme.primary else Color(0x33FFFFFF)
                    Box(Modifier.fillMaxWidth().border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(8.dp))) {
                        content()
                        Box(
                            Modifier.matchParentSize()
                                .clickable(role = Role.Button) { onSelectComponent(component.id) }
                                .semantics { contentDescription = "Edit ${component.kind.label.lowercase()}" },
                        )
                    }
                } else {
                    content()
                }
            }
        }
    }
}

@Composable
private fun CustomNotificationContentView(
    remoteViews: RemoteViews?,
    interactive: Boolean,
    preview: Boolean,
    modifier: Modifier = Modifier,
) {
    if (remoteViews == null) {
        if (preview) {
            Box(
                modifier = modifier
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Notification content",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        return
    }
    val baseContext = LocalContext.current
    val themedContext = remember(baseContext) { darkNotificationViewContext(baseContext) }
    AndroidView(
        factory = { FrameLayout(it) },
        update = { container ->
            if (container.tag === remoteViews) return@AndroidView
            container.tag = remoteViews
            container.removeAllViews()
            val inflated = runCatching { remoteViews.apply(themedContext, container) }.getOrNull()
            if (inflated != null) {
                val existingLp = inflated.layoutParams
                val height = if (existingLp != null && existingLp.height > 0) {
                    existingLp.height
                } else {
                    ViewGroup.LayoutParams.WRAP_CONTENT
                }
                container.addView(
                    inflated,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height),
                )
                if (!interactive) disableRemoteViewInteraction(inflated)
            }
        },
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
    )
}

private fun darkNotificationViewContext(base: Context): Context {
    val config = Configuration(base.resources.configuration).apply {
        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
    }
    val nightContext = runCatching { base.createConfigurationContext(config) }.getOrDefault(base)
    return ContextThemeWrapper(nightContext, android.R.style.Theme_DeviceDefault)
}

private fun disableRemoteViewInteraction(view: View) {
    view.isClickable = false
    view.isFocusable = false
    view.isLongClickable = false
    if (view is ViewGroup) {
        view.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        for (i in 0 until view.childCount) {
            disableRemoteViewInteraction(view.getChildAt(i))
        }
    }
}
