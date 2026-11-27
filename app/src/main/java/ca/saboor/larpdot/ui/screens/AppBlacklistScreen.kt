package ca.saboor.larpdot.ui.screens

import ca.saboor.larpdot.ui.theme.LarpIcons

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.components.LarpButtonOption
import ca.saboor.larpdot.ui.components.LarpConnectedButtons
import ca.saboor.larpdot.ui.components.LarpCard
import ca.saboor.larpdot.ui.components.LarpListItem
import ca.saboor.larpdot.ui.components.LarpSubPageTopBar
import ca.saboor.larpdot.ui.components.LarpSwitchRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isSystemApp: Boolean,
)

private enum class BlacklistFilter(val label: String, val icon: ImageVector) {
    ALL("All", LarpIcons.Apps),
    USER("User", LarpIcons.Android),
    BLACKLISTED("Blocked", LarpIcons.Block),
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppBlacklistScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)

    val blacklistedPackages by OverlayPreferences.blacklistedPackagesFlow.collectAsState()
    val appBlacklistEnabled by OverlayPreferences.appBlacklistEnabledFlow.collectAsState()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilterIndex by rememberSaveable { mutableIntStateOf(0) }
    val currentFilter = BlacklistFilter.entries[selectedFilterIndex]

    var appList by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val list = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                pm.queryIntentActivities(mainIntent, 0)
            }

            val ourPkg = context.packageName
            val seenPackages = mutableSetOf<String>()
            val result = mutableListOf<AppEntry>()

            for (resolve in resolveInfos) {
                val pkg = resolve.activityInfo?.packageName ?: continue
                if (pkg == ourPkg || seenPackages.contains(pkg)) continue
                seenPackages.add(pkg)

                val label = runCatching { resolve.loadLabel(pm).toString() }.getOrDefault(pkg)
                val icon = runCatching { resolve.loadIcon(pm) }.getOrNull()
                val isSystem = runCatching {
                    (resolve.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                }.getOrDefault(false)

                result.add(AppEntry(packageName = pkg, label = label, icon = icon, isSystemApp = isSystem))
            }

            // Include any currently blacklisted packages even if not a standard launcher activity
            val blacklisted = OverlayPreferences.getBlacklistedPackages(context)
            for (pkg in blacklisted) {
                if (!seenPackages.contains(pkg) && pkg != ourPkg) {
                    seenPackages.add(pkg)
                    val appInfo = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
                    val label = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: pkg
                    val icon = appInfo?.let { pm.getApplicationIcon(it) }
                    val isSystem = appInfo?.let { (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 } ?: false
                    result.add(AppEntry(packageName = pkg, label = label, icon = icon, isSystemApp = isSystem))
                }
            }

            result.sortedBy { it.label.lowercase() }
        }
        appList = list
        isLoading = false
    }

    val iconCache = remember { LruCache<String, ImageBitmap>(120) }

    val filteredApps = remember(appList, searchQuery, currentFilter, blacklistedPackages) {
        val trimmedQuery = searchQuery.trim()
        appList.filter { app ->
            val matchesFilter = when (currentFilter) {
                BlacklistFilter.ALL -> true
                BlacklistFilter.USER -> !app.isSystemApp
                BlacklistFilter.BLACKLISTED -> blacklistedPackages.contains(app.packageName)
            }
            val matchesQuery = trimmedQuery.isEmpty() ||
                app.label.contains(trimmedQuery, ignoreCase = true) ||
                app.packageName.contains(trimmedQuery, ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        // Navigation Header
        LarpSubPageTopBar(
            title = "App Blacklist",
            subtitle = if (blacklistedPackages.isEmpty()) {
                "No apps blacklisted"
            } else {
                "${blacklistedPackages.size} app${if (blacklistedPackages.size == 1) "" else "s"} blacklisted"
            },
            onBack = onBack,
            trailing = if (blacklistedPackages.isNotEmpty()) {
                {
                    TextButton(onClick = { OverlayPreferences.clearBlacklistedPackages(context) }) {
                        Text("Clear All")
                    }
                }
            } else null,
        )

        LarpCard {
            LarpSwitchRow(
                title = "Enable App Blacklist",
                subtitle = "Hide media and notifications from selected apps",
                icon = LarpIcons.Block,
                checked = appBlacklistEnabled,
                onCheckedChange = { OverlayPreferences.setAppBlacklistEnabled(context, it) },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = "Search apps",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = LarpIcons.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = LarpIcons.Clear,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
            ),
        )

        Spacer(modifier = Modifier.height(10.dp))

        LarpConnectedButtons(
            options = BlacklistFilter.entries.mapIndexed { index, filter ->
                LarpButtonOption(
                    checked = selectedFilterIndex == index,
                    label = filter.label,
                    icon = filter.icon,
                    onCheckedChange = { selectedFilterIndex = index },
                )
            },
        )

        Spacer(modifier = Modifier.height(8.dp))

        // App List
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp,
                )
            }
        } else if (filteredApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (searchQuery.isNotEmpty()) {
                        "No matching apps"
                    } else if (currentFilter == BlacklistFilter.BLACKLISTED) {
                        "No blocked apps"
                    } else {
                        "No apps found"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    val isBlacklisted = blacklistedPackages.contains(app.packageName)

                    LarpListItem(
                        title = app.label,
                        subtitle = app.packageName,
                        highlighted = isBlacklisted,
                        modifier = Modifier.animateItem(placementSpec = null),
                        leading = {
                            AppIcon(
                                packageName = app.packageName,
                                drawable = app.icon,
                                iconCache = iconCache,
                                label = app.label,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                            )
                        },
                        onClick = {
                            OverlayPreferences.toggleBlacklistedPackage(context, app.packageName)
                        },
                        trailing = {
                            Switch(
                                checked = isBlacklisted,
                                onCheckedChange = {
                                    OverlayPreferences.toggleBlacklistedPackage(context, app.packageName)
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppIcon(
    packageName: String,
    drawable: Drawable?,
    iconCache: LruCache<String, ImageBitmap>,
    label: String,
    modifier: Modifier = Modifier,
) {
    val cached = remember(packageName) { iconCache.get(packageName) }
    val bitmap by produceState(initialValue = cached, key1 = packageName, key2 = drawable) {
        if (value != null || drawable == null) return@produceState
        val decoded = withContext(Dispatchers.Default) {
            runCatching { drawable.toBitmap(width = 96, height = 96).asImageBitmap() }.getOrNull()
        }
        if (decoded != null) {
            iconCache.put(packageName, decoded)
            value = decoded
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = label,
            modifier = modifier,
        )
    } else {
        Box(
            modifier = modifier.background(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                RoundedCornerShape(10.dp),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = LarpIcons.Android,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
