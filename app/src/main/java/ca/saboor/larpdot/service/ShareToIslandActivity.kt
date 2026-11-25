package ca.saboor.larpdot.service

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.IntentCompat

/** Lightweight share-sheet target ("Hold in Island") that tucks shared photos, files, links, or text into the island pocket. */
class ShareToIslandActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                val streamUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                val sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim()
                val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
                if (streamUri != null) {
                    runCatching {
                        contentResolver.takePersistableUriPermission(streamUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    ScreenshotPocketManager.holdSharedItem(
                        context = this,
                        uri = streamUri,
                        mimeType = intent.type,
                        label = subject,
                    )
                    Toast.makeText(this, "Held in Dynamic Island", Toast.LENGTH_SHORT).show()
                } else if (!sharedText.isNullOrBlank()) {
                    ScreenshotPocketManager.holdSharedText(
                        context = this,
                        text = sharedText,
                        subject = subject,
                    )
                    Toast.makeText(this, "Held in Dynamic Island", Toast.LENGTH_SHORT).show()
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    .orEmpty()
                    .take(5)
                if (uris.isNotEmpty()) {
                    val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
                    uris.forEach { uri ->
                        runCatching {
                            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        ScreenshotPocketManager.holdSharedItem(
                            context = this,
                            uri = uri,
                            mimeType = intent.type,
                            label = subject,
                        )
                    }
                    val count = uris.size
                    Toast.makeText(
                        this,
                        if (count == 1) "Held in Dynamic Island" else "Held $count items in Dynamic Island",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
        finish()
    }
}
