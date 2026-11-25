package ca.saboor.larpdot.service

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Log
import android.util.SizeF
import android.view.MotionEvent
import android.widget.RemoteViews
import android.widget.Toast
import ca.saboor.larpdot.ui.WidgetColorKeyRenderer
import kotlin.math.roundToInt

internal object CustomIslandWidgets {
    private const val HOST_ID = 1141
    private const val REQUEST_RECONFIGURE = 0x2000
    private const val REQUEST_BIND = 0x4000
    private const val REQUEST_CONFIGURE = 0x6000
    private const val WIDGET_REQUEST_ID_MASK = 0x1fff
    private lateinit var host: IslandWidgetHost
    private var manager: AppWidgetManager? = null
    private var activeViews = 0
    private val mountedViews = mutableMapOf<Int, MutableList<CustomIslandWidgetView>>()

    fun initialize(context: Context) {
        manager = AppWidgetManager.getInstance(context.applicationContext)
        host = IslandWidgetHost(context.applicationContext)
        val settings = CustomIslandStore.settings.value
        val referenced = buildSet {
            settings.islands.forEach { island ->
                CustomIslandPosition.entries.forEach { position -> island.slot(position).widgetId?.let(::add) }
            }
            settings.pending?.widgetId?.let(::add)
        }
        runCatching { host.appWidgetIds.filterNot { it in referenced }.forEach(::delete) }
    }

    fun providers(): List<AppWidgetProviderInfo> = runCatching {
        manager?.getInstalledProvidersForProfile(Process.myUserHandle()).orEmpty()
    }.getOrDefault(emptyList())

    fun info(widgetId: Int): AppWidgetProviderInfo? =
        runCatching { manager?.getAppWidgetInfo(widgetId) }.getOrNull()

    fun widgetLabels(context: Context, widgetId: Int): CustomIslandWidgetLabels? {
        val provider = info(widgetId) ?: return null
        val packages = context.packageManager
        val packageName = provider.provider.packageName
        val appName = runCatching {
            packages.getApplicationLabel(packages.getApplicationInfo(packageName, 0)).toString()
        }.getOrNull()?.takeIf { it.isNotBlank() }
        val label = provider.loadLabel(packages)?.takeIf { it.isNotBlank() } ?: appName ?: "Widget"
        return CustomIslandWidgetLabels(label = label, appName = appName)
    }

    @Suppress("DEPRECATION")
    fun bind(activity: Activity, islandId: String, position: CustomIslandPosition, provider: AppWidgetProviderInfo) {
        val manager = manager ?: return showSetupError(activity)
        val settings = CustomIslandStore.settings.value
        val slot = settings.islands.firstOrNull { it.id == islandId }?.slot(position) ?: return
        val previousPending = settings.pending
        var newId: Int? = null
        try {
            val widgetId = host.allocateAppWidgetId().also { newId = it }
            val pending = PendingIslandWidget(islandId, position, widgetId)
            if (!CustomIslandStore.setPending(pending)) {
                delete(widgetId)
                return showSetupError(activity)
            }
            delete(previousPending?.widgetId)
            val options = sizeOptions(slot.width, slot.height)
            if (manager.bindAppWidgetIdIfAllowed(widgetId, provider.profile, provider.provider, options)) {
                configureOrSave(activity, pending)
            } else {
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, provider.profile)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options)
                activity.startActivityForResult(intent, widgetRequestCode(REQUEST_BIND, widgetId))
            }
        } catch (exception: Exception) {
            Log.w("CustomIslands", "Could not bind widget", exception)
            val pending = CustomIslandStore.settings.value.pending
            if (pending?.widgetId == newId && pending != null) cancel(pending)
            else delete(newId)
            showSetupError(activity)
        }
    }

    fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode !in REQUEST_RECONFIGURE..0x7fff) return false
        val request = requestCode and WIDGET_REQUEST_ID_MASK.inv()
        if (request == REQUEST_RECONFIGURE) return true
        val pending = CustomIslandStore.settings.value.pending ?: return true
        if (widgetRequestCode(request, pending.widgetId) != requestCode) return true
        val returnedId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pending.widgetId) ?: pending.widgetId
        if (returnedId > 0 && returnedId != pending.widgetId) return true
        if (resultCode != Activity.RESULT_OK) {
            cancel(pending)
            return true
        }
        if (request == REQUEST_BIND) configureOrSave(activity, pending)
        else saveWidget(activity, pending)
        return true
    }

    fun reconfigure(activity: Activity, widgetId: Int) {
        if (info(widgetId)?.configure == null) return
        runCatching {
            host.startAppWidgetConfigureActivityForResult(
                activity, widgetId, 0, widgetRequestCode(REQUEST_RECONFIGURE, widgetId), null,
            )
        }.onFailure {
            Log.w("CustomIslands", "Could not reopen widget configuration", it)
            Toast.makeText(activity, "Couldn’t open widget settings", Toast.LENGTH_SHORT).show()
        }
    }

    // AndroidX allocates activity-result codes above the 16-bit range used by the widget host.
    private fun widgetRequestCode(request: Int, widgetId: Int) = request or (widgetId and WIDGET_REQUEST_ID_MASK)

    private fun configureOrSave(activity: Activity, pending: PendingIslandWidget) {
        val provider = info(pending.widgetId)
        if (provider == null) {
            cancel(pending)
            return showSetupError(activity)
        }
        if (provider.configure == null) {
            saveWidget(activity, pending)
        } else {
            try {
                host.startAppWidgetConfigureActivityForResult(
                    activity, pending.widgetId, 0, widgetRequestCode(REQUEST_CONFIGURE, pending.widgetId), null,
                )
            } catch (exception: Exception) {
                Log.w("CustomIslands", "Could not open widget configuration", exception)
                // Some providers mark initial configuration as optional.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    provider.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
                ) saveWidget(activity, pending)
                else {
                    cancel(pending)
                    showSetupError(activity)
                }
            }
        }
    }

    private fun saveWidget(activity: Activity, pending: PendingIslandWidget) {
        if (info(pending.widgetId) == null || !CustomIslandStore.finishPending(pending)) {
            cancel(pending)
            showSetupError(activity)
        }
    }

    private fun cancel(pending: PendingIslandWidget) {
        if (CustomIslandStore.settings.value.pending == pending) CustomIslandStore.setPending(null)
        delete(pending.widgetId)
    }

    private fun showSetupError(activity: Activity) {
        Toast.makeText(activity, "Couldn’t add that widget", Toast.LENGTH_SHORT).show()
    }

    fun delete(widgetId: Int?) {
        if (widgetId != null) {
            runCatching { host.deleteAppWidgetId(widgetId) }
                .onFailure { Log.w("CustomIslands", "Could not release widget", it) }
        }
    }

    fun createView(context: Context, widgetId: Int, info: AppWidgetProviderInfo): CustomIslandWidgetView {
        val view = host.createView(context, widgetId, info) as CustomIslandWidgetView
        view.setPadding(0, 0, 0, 0)
        view.bindId(widgetId)
        mountedViews.getOrPut(widgetId) { mutableListOf() }.add(view)
        return view
    }

    fun releaseView(widgetId: Int, view: CustomIslandWidgetView) {
        val list = mountedViews[widgetId] ?: return
        list.remove(view)
        if (list.isEmpty()) mountedViews.remove(widgetId)
    }

    private fun forwardRemoteViews(widgetId: Int, source: CustomIslandWidgetView, remoteViews: RemoteViews?) {
        mountedViews[widgetId]?.forEach { target ->
            if (target !== source) target.applyForwardedRemoteViews(remoteViews)
        }
    }

    fun startShowing() {
        if (activeViews++ == 0) runCatching { host.startListening() }
    }

    fun stopShowing() {
        activeViews = (activeViews - 1).coerceAtLeast(0)
        if (activeViews == 0) {
            mountedViews.clear()
            host.releaseViews()
        }
    }

    fun updateSize(widgetId: Int, width: Float, height: Float) {
        if (width <= 0f || height <= 0f) return
        runCatching { manager?.updateAppWidgetOptions(widgetId, sizeOptions(width.roundToInt(), height.roundToInt())) }
    }

    private fun sizeOptions(width: Int, height: Int) = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(width.toFloat(), height.toFloat())))
        }
    }

    private class IslandWidgetHost(context: Context) : AppWidgetHost(context, HOST_ID) {
        override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo): AppWidgetHostView =
            CustomIslandWidgetView(context) { id, source, remoteViews ->
                forwardRemoteViews(id, source, remoteViews)
            }

        fun releaseViews() {
            runCatching { stopListening() }
            clearViews()
        }
    }
}

@android.annotation.SuppressLint("ViewConstructor")
internal class CustomIslandWidgetView(
    context: Context,
    private val onRemoteViewsUpdate: ((Int, CustomIslandWidgetView, RemoteViews?) -> Unit)? = null,
) : AppWidgetHostView(context) {
    var interactive = false
    private var boundWidgetId: Int = -1
    private var applyingForwarded = false
    private val colorKeyRenderer = WidgetColorKeyRenderer(this)

    fun bindId(widgetId: Int) {
        boundWidgetId = widgetId
    }

    fun setColorKey(key: WidgetColorKey?) = colorKeyRenderer.update(key)

    fun applyForwardedRemoteViews(remoteViews: RemoteViews?) {
        applyingForwarded = true
        try {
            super.updateAppWidget(remoteViews)
        } finally {
            applyingForwarded = false
        }
    }

    override fun updateAppWidget(remoteViews: RemoteViews?) {
        super.updateAppWidget(remoteViews)
        if (!applyingForwarded && boundWidgetId > 0) {
            onRemoteViewsUpdate?.invoke(boundWidgetId, this, remoteViews)
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean =
        interactive && super.dispatchTouchEvent(event)
}
