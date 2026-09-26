package ca.saboor.larpdot.ui.screens

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.components.ConnectedButtonGroup
import ca.saboor.larpdot.ui.components.LarpCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isSystemApp: Boolean,
)

private enum class BlacklistFilter(val label: String, val icon: ImageVector) {
    ALL("All Apps", Icons.Default.Apps),
    BLACKLISTED("Blacklisted", Icons.Default.Block),
}

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
    var selectedFilterIndex by rememberSaveable { mutableStateOf(0) }
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

    val filteredApps = remember(appList, searchQuery, currentFilter, blacklistedPackages) {
        appList.filter { app ->
            val matchesFilter = when (currentFilter) {
                BlacklistFilter.ALL -> true
                BlacklistFilter.BLACKLISTED -> blacklistedPackages.contains(app.packageName)
            }
            val matchesQuery = searchQuery.isBlank() ||
                    app.label.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        // Navigation Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
                Column {
                    Text(
                        text = "App Blacklist",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (blacklistedPackages.isEmpty()) {
                            "No apps blacklisted"
                        } else {
                            "${blacklistedPackages.size} app${if (blacklistedPackages.size == 1) "" else "s"} blacklisted"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (blacklistedPackages.isNotEmpty()) {
                TextButton(
                    onClick = {
                        OverlayPreferences.clearBlacklistedPackages(context)
                    },
                ) {
                    Text("Clear All")
                }
            }
        }

        // Master Switch Card
        LarpCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Column {
                        Text(
                            text = "Enable App Blacklist",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Ignore media playback and notifications from selected apps so they never appear on the dynamic island",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Switch(
                    checked = appBlacklistEnabled,
                    onCheckedChange = { isChecked ->
                        OverlayPreferences.setAppBlacklistEnabled(context, isChecked)
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = "Search installed apps...",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
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

        // Filter Tabs
        ConnectedButtonGroup(
            items = BlacklistFilter.entries,
            selectedIndex = selectedFilterIndex,
            onItemSelected = { selectedFilterIndex = it },
            label = { filter ->
                when (filter) {
                    BlacklistFilter.ALL -> "All (${appList.size})"
                    BlacklistFilter.BLACKLISTED -> "Blacklisted (${blacklistedPackages.size})"
                }
            },
            icon = { it.icon },
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp,
                    )
                    Text(
                        text = "Loading installed apps...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else if (filteredApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = if (searchQuery.isNotEmpty()) Icons.Default.SearchOff else Icons.Default.Block,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        text = if (searchQuery.isNotEmpty()) {
                            "No apps match \"$searchQuery\""
                        } else if (currentFilter == BlacklistFilter.BLACKLISTED) {
                            "No apps blacklisted yet"
                        } else {
                            "No apps found"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (searchQuery.isNotEmpty()) {
                            "Try searching by app name or package name"
                        } else if (currentFilter == BlacklistFilter.BLACKLISTED) {
                            "Select apps from the \"All Apps\" tab to ignore their media playback and notifications on the dynamic island"
                        } else {
                            ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    val isBlacklisted = blacklistedPackages.contains(app.packageName)

                    ElevatedCard(
                        onClick = {
                            OverlayPreferences.toggleBlacklistedPackage(context, app.packageName)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = if (isBlacklisted) {
                                MaterialTheme.colorScheme.surfaceContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                        ),
                        elevation = CardDefaults.elevatedCardElevation(
                            defaultElevation = 1.dp,
                            pressedElevation = 0.dp,
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(
                                drawable = app.icon,
                                label = app.label,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = app.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Switch(
                                checked = isBlacklisted,
                                onCheckedChange = {
                                    OverlayPreferences.toggleBlacklistedPackage(context, app.packageName)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIcon(
    drawable: Drawable?,
    label: String,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(drawable) {
        drawable?.let {
            runCatching {
                it.toBitmap(width = 96, height = 96).asImageBitmap()
            }.getOrNull()
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
                imageVector = Icons.Default.Android,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
