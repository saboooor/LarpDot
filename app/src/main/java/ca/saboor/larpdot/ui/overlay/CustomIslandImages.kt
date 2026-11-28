package ca.saboor.larpdot.ui.overlay

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.media.DominantColorExtractor
import ca.saboor.larpdot.media.MediaPlaybackState
import ca.saboor.larpdot.notification.NotificationActivityState
import ca.saboor.larpdot.service.CustomIsland
import ca.saboor.larpdot.service.CustomIslandCondition
import ca.saboor.larpdot.service.CustomIslandImage
import ca.saboor.larpdot.service.CustomIslandNotifications
import ca.saboor.larpdot.service.CustomIslandSources
import ca.saboor.larpdot.service.CustomIslandVisibility
import ca.saboor.larpdot.service.CustomIslandWidgets
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.service.ScreenStateTracker
import ca.saboor.larpdot.service.loadNotificationSmallIconBitmap
import ca.saboor.larpdot.service.resolveSources
import ca.saboor.larpdot.ui.theme.LarpIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val appDominantColorCache = LruCache<String, Color>(64)

@Composable
internal fun rememberAppDominantColor(packageName: String?): Color? {
    val context = LocalContext.current
    if (packageName.isNullOrBlank()) return null
    val cached = remember(packageName) { appDominantColorCache.get(packageName) }
    val color by produceState(initialValue = cached, key1 = context, key2 = packageName) {
        if (value != null) return@produceState
        val extracted = withContext(Dispatchers.IO) {
            val icon = NotificationActivityState.getOrLoadAppIcon(context, packageName) ?: return@withContext null
            DominantColorExtractor.extractArtworkColors(icon).primary
        }
        if (extracted != null) {
            appDominantColorCache.put(packageName, extracted)
            value = extracted
        }
    }
    return color
}

@Composable
internal fun rememberCustomIslandDominantColor(
    island: CustomIsland,
    sources: CustomIslandSources? = null,
): Pair<Color?, String> {
    val resolvedSources = sources ?: customIslandSources(island)
    val isMusicCondition = island.visibility == CustomIslandVisibility.CONDITIONAL &&
        island.condition == CustomIslandCondition.MUSIC
    val appPackage = island.tapAppPackage
        ?: resolvedSources.appPackage
        ?: island.notificationRule.packageName.takeIf(String::isNotBlank)
    val appDominant = rememberAppDominantColor(appPackage)

    if (isMusicCondition && resolvedSources.media.albumArt != null) {
        return resolvedSources.media.dominantColor to "Album Color"
    }
    resolvedSources.notificationColor?.let { rawColor ->
        val visible = remember(rawColor) { Color(DominantColorExtractor.ensureColorVisible(rawColor)) }
        return visible to "App Color"
    }
    if (appDominant != null) {
        return appDominant to "App Color"
    }
    if (resolvedSources.media.albumArt != null) {
        return resolvedSources.media.dominantColor to "Album Color"
    }
    return null to "App Color"
}

@Composable
internal fun customIslandSources(island: CustomIsland): CustomIslandSources {
    val media by MediaPlaybackState.currentTrack.collectAsState()
    val current by NotificationActivityState.current.collectAsState()
    val activities by NotificationActivityState.eligibleActivities.collectAsState()
    val notifications by CustomIslandNotifications.active.collectAsState()
    val locked by ScreenStateTracker.isDeviceLocked.collectAsState()
    return remember(island, media, current, activities, notifications, locked) {
        island.resolveSources(media, current, activities, notifications, locked)
    }
}

internal val CustomIslandImage.placeholderIcon
    get() = when (this) {
        CustomIslandImage.APP_ICON -> LarpIcons.Apps
        CustomIslandImage.NOTIFICATION_ICON -> LarpIcons.Notifications
        CustomIslandImage.ALBUM_ART -> LarpIcons.PhotoLibrary
        CustomIslandImage.NOTIFICATION_IMAGE -> LarpIcons.Notifications
    }

@Composable
internal fun rememberCustomIslandImageBitmap(
    source: CustomIslandImage,
    sources: CustomIslandSources,
    appOverride: String? = null,
): Bitmap? {
    val context = LocalContext.current
    val app = appOverride ?: sources.appPackage
    val notificationArtwork = sources.image
    val notificationSmallIcon = sources.notificationIcon
    return when (source) {
        CustomIslandImage.ALBUM_ART -> sources.media.albumArt
        CustomIslandImage.APP_ICON -> if (app != null) key(app) {
            val icon by produceState<Bitmap?>(null, context, app) {
                value = withContext(Dispatchers.IO) { NotificationActivityState.getOrLoadAppIcon(context, app) }
            }
            icon
        } else null
        CustomIslandImage.NOTIFICATION_ICON -> if (notificationSmallIcon != null) key(notificationSmallIcon) {
            val smallBitmap by produceState<Bitmap?>(null, context, notificationSmallIcon) {
                value = withContext(Dispatchers.IO) { loadNotificationSmallIconBitmap(context, notificationSmallIcon) }
            }
            smallBitmap
        } else null
        CustomIslandImage.NOTIFICATION_IMAGE -> if (notificationArtwork != null) key(notificationArtwork) {
            val picture by produceState<Bitmap?>(null, context, notificationArtwork) {
                value = withContext(Dispatchers.IO) { runCatching { notificationArtwork.load(context) }.getOrNull() }
            }
            picture
        } else null
    }?.takeUnless(Bitmap::isRecycled)
}

@Composable
internal fun CustomIslandExpandedBackground(
    island: CustomIsland,
    sources: CustomIslandSources,
    backgroundOpacity: Float,
    preview: Boolean,
    dominantColor: Color? = null,
    modifier: Modifier = Modifier,
) {
    val style = island.backgroundStyle
    if (style == OverlayPreferences.ExpandedBackgroundStyle.NONE) return
    val imageSource = island.effectiveBackgroundImage
    val appOverride = remember(island) {
        island.associatedAppPackage { widgetId -> CustomIslandWidgets.info(widgetId)?.provider?.packageName }
    }
    val resolvedApp = when {
        island.visibility == CustomIslandVisibility.CONDITIONAL && sources.appPackage != null -> sources.appPackage
        else -> appOverride ?: sources.appPackage
    }
    val bitmap = rememberCustomIslandImageBitmap(
        source = imageSource,
        sources = sources,
        appOverride = resolvedApp,
    )
    val accent = dominantColor ?: MaterialTheme.colorScheme.primary
    if (bitmap != null) {
        ExpandedImageBackground(
            bitmap = bitmap,
            style = style,
            isVertical = false,
            backgroundOpacity = backgroundOpacity,
            tint = if (imageSource == CustomIslandImage.NOTIFICATION_ICON) accent else null,
            modifier = modifier,
        )
    } else if (preview) {
        val previewBrush = when (style) {
            OverlayPreferences.ExpandedBackgroundStyle.NONE -> return
            OverlayPreferences.ExpandedBackgroundStyle.BASIC_FADED -> Brush.horizontalGradient(
                colorStops = arrayOf(
                    0.00f to accent.copy(alpha = 0.42f),
                    0.55f to accent.copy(alpha = 0.14f),
                    1.00f to Color.Transparent,
                ),
            )
            OverlayPreferences.ExpandedBackgroundStyle.BLENDED -> Brush.horizontalGradient(
                colorStops = arrayOf(
                    0.00f to accent.copy(alpha = 0.52f),
                    0.45f to accent.copy(alpha = 0.24f),
                    0.80f to accent.copy(alpha = 0.08f),
                    1.00f to Color.Transparent,
                ),
            )
            OverlayPreferences.ExpandedBackgroundStyle.FULL_BACKGROUND -> Brush.verticalGradient(
                colorStops = arrayOf(
                    0.00f to accent.copy(alpha = 0.42f),
                    0.50f to accent.copy(alpha = 0.28f),
                    1.00f to accent.copy(alpha = 0.18f),
                ),
            )
            OverlayPreferences.ExpandedBackgroundStyle.BLURRED_FULL_BACKGROUND -> Brush.verticalGradient(
                colorStops = arrayOf(
                    0.00f to accent.copy(alpha = 0.34f),
                    0.50f to accent.copy(alpha = 0.22f),
                    1.00f to accent.copy(alpha = 0.12f),
                ),
            )
        }
        Box(
            modifier = modifier
                .graphicsLayer { alpha = backgroundOpacity }
                .background(previewBrush),
        )
    }
}

@Composable
internal fun CustomIslandImageContent(
    source: CustomIslandImage,
    sources: CustomIslandSources,
    modifier: Modifier,
    compact: Boolean,
    preview: Boolean,
    appOverride: String? = null,
    tint: Color? = null,
) {
    val bitmap = rememberCustomIslandImageBitmap(source, sources, appOverride)

    if (bitmap != null) {
        val image = remember(bitmap) { bitmap.asImageBitmap() }
        if (source == CustomIslandImage.NOTIFICATION_ICON) {
            Image(
                bitmap = image,
                contentDescription = source.label,
                modifier = modifier.padding(if (compact) 1.dp else 4.dp),
                contentScale = ContentScale.Fit,
                colorFilter = tint?.let { ColorFilter.tint(it) },
            )
        } else {
            Image(
                bitmap = image,
                contentDescription = source.label,
                modifier = modifier.clip(if (compact) CircleShape else RoundedCornerShape(12.dp)),
                contentScale = if (compact || source == CustomIslandImage.ALBUM_ART) ContentScale.Crop else ContentScale.Fit,
            )
        }
    } else if (preview || compact) {
        val fallbackTint = if (source == CustomIslandImage.NOTIFICATION_ICON) {
            tint ?: MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline
        }
        Box(modifier, contentAlignment = Alignment.Center) {
            Icon(
                source.placeholderIcon,
                source.label,
                modifier = if (compact) Modifier.fillMaxSize() else Modifier.size(24.dp),
                tint = fallbackTint,
            )
        }
    }
}
