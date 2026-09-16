package ca.saboor.larpdot.service

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView

/**
 * A FrameLayout wrapper around ComposeView that restricts which touch coordinates are
 * forwarded to Compose.
 *
 * When [compactPillRect] is non-null, touches outside that rect return false, causing
 * WindowManager to pass them through to windows beneath (e.g. the launcher, notification shade).
 * This is needed because the overlay window is always sized to the full expanded canvas — even
 * when only the compact pill is visible — so the empty space below the pill must not swallow
 * touches from the rest of the UI.
 *
 * Set [compactPillRect] to null when expanded (full window is interactive).
 */
class PassthroughComposeView(context: Context) : FrameLayout(context) {

    /** Touch rect in view-local coordinates. null = no restriction (full window touchable). */
    var compactPillRect: Rect? = null

    val composeView: ComposeView = ComposeView(context).also { addView(it) }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val rect = compactPillRect
        if (rect != null && !rect.contains(ev.x.toInt(), ev.y.toInt())) {
            // Touch is in the transparent area outside the compact pill — pass through
            return false
        }
        return super.dispatchTouchEvent(ev)
    }
}

