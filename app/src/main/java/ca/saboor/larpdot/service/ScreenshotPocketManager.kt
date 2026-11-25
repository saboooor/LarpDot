package ca.saboor.larpdot.service

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import ca.saboor.larpdot.notification.NotificationActivityInfo
import ca.saboor.larpdot.notification.NotificationActivityKind
import ca.saboor.larpdot.notification.NotificationActivityState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Zero-step Screenshot & File Pocket:
 * - Watches MediaStore for newly captured screenshots and surfaces them as a transient island bubble
 *   with 1-tap Share, Copy Image, and Delete actions.
 * - Accepts files/images shared via [ShareToIslandActivity] ("Hold in Island").
 */
object ScreenshotPocketManager {
    private const val TAG = "ScreenshotPocket"
    private const val DISMISS_DELAY_MS = 10_000L

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var appContext: Context? = null
    private var observer: ContentObserver? = null
    private var lastHandledScreenshotId: Long = -1L
    private val dismissJobs = mutableMapOf<String, Job>()

    fun requiredPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(requiredPermission()) == PackageManager.PERMISSION_GRANTED

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        syncObserver(app)
    }

    fun syncObserver(context: Context) {
        val app = context.applicationContext
        appContext = app
        val settings = OverlayPreferences.getNotificationActivitySettings(app)
        val shouldWatch = settings.enabled && settings.screenshotPocket && hasPermission(app)
        if (shouldWatch && observer == null) {
            val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    handleMediaStoreChange(app, uri)
                }
            }
            runCatching {
                app.contentResolver.registerContentObserver(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    true,
                    contentObserver,
                )
                observer = contentObserver
            }.onFailure {
                Log.w(TAG, "Failed registering screenshot observer", it)
            }
        } else if (!shouldWatch && observer != null) {
            runCatching { app.contentResolver.unregisterContentObserver(observer!!) }
            observer = null
        }
    }

    private val HTTP_URL_REGEX = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)
    private val URL_HOST_REGEX = Regex("""^https?://(?:www\.)?([^/:?#]+)""", RegexOption.IGNORE_CASE)

    internal fun isScreenshotCandidate(relativePath: String?, displayName: String?): Boolean {
        val path = relativePath.orEmpty().lowercase()
        val name = displayName.orEmpty().lowercase()
        return path.contains("screenshot") || name.contains("screenshot")
    }

    internal fun extractFirstHttpUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val raw = HTTP_URL_REGEX.find(text)?.value ?: return null
        return raw.trimEnd('.', ',', ';', '!', '?', ')', ']', '}')
    }

    internal fun formatHeldTextTitle(text: String, subject: String? = null): String {
        val cleanSubject = subject?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanSubject != null) return cleanSubject.take(40)
        val url = extractFirstHttpUrl(text)
        if (url != null) {
            val host = URL_HOST_REGEX.find(url)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
            if (host != null) return host.take(40)
        }
        return "Held Note"
    }

    private fun handleMediaStoreChange(context: Context, changedUri: Uri?) {
        val settings = OverlayPreferences.getNotificationActivitySettings(context)
        if (!settings.enabled || !settings.screenshotPocket || !hasPermission(context)) return

        scope.launch {
            val latest = withContext(Dispatchers.IO) { queryLatestScreenshot(context, changedUri) } ?: return@launch
            if (latest.id == lastHandledScreenshotId) return@launch
            lastHandledScreenshotId = latest.id

            val thumb = withContext(Dispatchers.IO) { loadThumbnail(context, latest.uri) }
            postPocketItem(
                key = "system_screenshot_${latest.id}",
                title = "Screenshot Ready",
                subtitle = latest.displayName,
                uri = latest.uri,
                mimeType = latest.mimeType,
                thumbnail = thumb,
                permanent = settings.permanentScreenshotPocket,
            )
        }
    }

    fun holdSharedItem(context: Context, uri: Uri, mimeType: String?, label: String? = null) {
        val app = context.applicationContext
        appContext = app
        scope.launch {
            val resolvedName = label?.takeIf { it.isNotBlank() }
                ?: withContext(Dispatchers.IO) { resolveDisplayName(app, uri) }
                ?: "Held Item"
            val effectiveMime = mimeType ?: app.contentResolver.getType(uri) ?: "*/*"
            val thumb = if (effectiveMime.startsWith("image/")) {
                withContext(Dispatchers.IO) { loadThumbnail(app, uri) }
            } else null

            postPocketItem(
                key = "system_screenshot_held_${System.currentTimeMillis()}_${uri.hashCode()}",
                title = "Held in Island",
                subtitle = resolvedName,
                uri = uri,
                mimeType = effectiveMime,
                thumbnail = thumb,
                permanent = true,
            )
        }
    }

    fun holdSharedText(context: Context, text: String, subject: String? = null) {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return
        appContext = context.applicationContext
        val key = "system_screenshot_text_${System.currentTimeMillis()}"
        val info = NotificationActivityInfo(
            key = key,
            packageName = "android",
            appName = "Pocket",
            title = formatHeldTextTitle(cleaned, subject),
            text = cleaned.take(240),
            kind = NotificationActivityKind.SCREENSHOT,
            progress = null,
            progressMax = null,
            progressIndeterminate = false,
            chronometerBaseElapsedRealtime = null,
            chronometerCountsDown = false,
            postedAtMillis = System.currentTimeMillis(),
            contentIntent = null,
            actions = emptyList(),
            heldText = cleaned,
            isPinnedShortTerm = true,
        )
        NotificationActivityState.postSystemActivity(info)
    }

    private fun postPocketItem(
        key: String,
        title: String,
        subtitle: String,
        uri: Uri,
        mimeType: String,
        thumbnail: Bitmap?,
        permanent: Boolean,
    ) {
        val info = NotificationActivityInfo(
            key = key,
            packageName = "android",
            appName = "Pocket",
            title = title,
            text = subtitle,
            kind = NotificationActivityKind.SCREENSHOT,
            progress = null,
            progressMax = null,
            progressIndeterminate = false,
            chronometerBaseElapsedRealtime = null,
            chronometerCountsDown = false,
            postedAtMillis = System.currentTimeMillis(),
            contentIntent = null,
            actions = emptyList(),
            appIcon = thumbnail,
            mediaUri = uri.toString(),
            mediaMimeType = mimeType,
            isPinnedShortTerm = permanent,
        )
        NotificationActivityState.postSystemActivity(info)

        dismissJobs[key]?.cancel()
        if (!permanent) {
            dismissJobs[key] = scope.launch {
                delay(DISMISS_DELAY_MS)
                NotificationActivityState.removeSystemActivity(key)
                dismissJobs.remove(key)
            }
        }
    }

    fun openItem(context: Context, uriString: String, mimeType: String?) {
        val uri = uriString.toUri()
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType ?: "image/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openUrl(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun shareItem(context: Context, uriString: String, mimeType: String?) {
        val uri = uriString.toUri()
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType ?: "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Share from Island").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(chooser) }
    }

    fun shareText(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(send, "Share from Island").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(chooser) }
    }

    fun copyItem(context: Context, uriString: String) {
        val uri = uriString.toUri()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = ClipData.newUri(context.contentResolver, "Screenshot", uri)
        runCatching {
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyText(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        runCatching {
            clipboard.setPrimaryClip(ClipData.newPlainText("Held Text", text))
            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    fun dismissHeldItem(key: String) {
        dismissJobs.remove(key)?.cancel()
        NotificationActivityState.removeSystemActivity(key)
    }

    fun deleteItem(context: Context, key: String, uriString: String) {
        val uri = uriString.toUri()
        dismissJobs.remove(key)?.cancel()
        NotificationActivityState.removeSystemActivity(key)
        scope.launch(Dispatchers.IO) {
            val deleted = runCatching { context.contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    if (deleted) "Screenshot deleted" else "Dismissed from island",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private data class ScreenshotRecord(
        val id: Long,
        val uri: Uri,
        val displayName: String,
        val mimeType: String,
    )

    private fun queryLatestScreenshot(context: Context, changedUri: Uri?): ScreenshotRecord? {
        val nowSeconds = System.currentTimeMillis() / 1000L
        val projection = buildList {
            add(MediaStore.Images.Media._ID)
            add(MediaStore.Images.Media.DISPLAY_NAME)
            add(MediaStore.Images.Media.MIME_TYPE)
            add(MediaStore.Images.Media.DATE_ADDED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Images.Media.RELATIVE_PATH)
                add(MediaStore.Images.Media.IS_PENDING)
            }
        }.toTypedArray()

        val queryUri = if (changedUri != null && changedUri.toString().startsWith(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString())) {
            changedUri
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val cursor = runCatching {
            context.contentResolver.query(
                queryUri,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )
        }.getOrNull() ?: return null

        cursor.use { c ->
            val idIdx = c.getColumnIndex(MediaStore.Images.Media._ID)
            val nameIdx = c.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
            val mimeIdx = c.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
            val addedIdx = c.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
            val pathIdx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                c.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
            } else -1
            val pendingIdx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                c.getColumnIndex(MediaStore.Images.Media.IS_PENDING)
            } else -1

            var inspected = 0
            while (c.moveToNext() && inspected < 5) {
                inspected++
                if (pendingIdx >= 0 && c.getInt(pendingIdx) != 0) continue
                val dateAdded = if (addedIdx >= 0) c.getLong(addedIdx) else 0L
                if (nowSeconds - dateAdded > 12L) break
                val name = if (nameIdx >= 0) c.getString(nameIdx) else null
                val path = if (pathIdx >= 0) c.getString(pathIdx) else null
                if (!isScreenshotCandidate(path, name)) continue
                val id = if (idIdx >= 0) c.getLong(idIdx) else continue
                val mime = (if (mimeIdx >= 0) c.getString(mimeIdx) else null) ?: "image/png"
                val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                return ScreenshotRecord(id, contentUri, name ?: "Screenshot", mime)
            }
        }
        return null
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private fun loadThumbnail(context: Context, uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        var sample = 1
        while (maxDim / (sample * 2) >= 256) sample *= 2
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
    }.getOrNull()
}
