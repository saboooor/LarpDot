package ca.saboor.larpdot.service

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast

/** Lightweight share-sheet target ("Hold in Island") that tucks a shared photo/file into the island pocket. */
class ShareToIslandActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == Intent.ACTION_SEND) {
            val streamUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            if (streamUri != null) {
                runCatching {
                    contentResolver.takePersistableUriPermission(streamUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ScreenshotPocketManager.holdSharedItem(
                    context = this,
                    uri = streamUri,
                    mimeType = intent.type,
                    label = intent.getStringExtra(Intent.EXTRA_SUBJECT),
                )
                Toast.makeText(this, "Held in Dynamic Island", Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }
}
