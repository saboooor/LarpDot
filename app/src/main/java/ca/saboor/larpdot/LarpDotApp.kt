package ca.saboor.larpdot

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.cutout.CutoutDetector
import ca.saboor.larpdot.flashlight.FlashlightController
import ca.saboor.larpdot.navigation.Destination
import ca.saboor.larpdot.service.DotOverlayService
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.components.PermissionsSetupDialog
import ca.saboor.larpdot.ui.screens.FlashlightScreen
import ca.saboor.larpdot.ui.screens.HomeScreen
import ca.saboor.larpdot.ui.screens.LiveActivitiesScreen
import ca.saboor.larpdot.ui.screens.MusicScreen
import ca.saboor.larpdot.ui.theme.LarpMotion

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LarpDotApp() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val tabTravel = with(LocalDensity.current) { 14.dp.roundToPx() }

    var selectedDestinationName by rememberSaveable { mutableStateOf(Destination.Home.name) }
    val selectedDestination = Destination.valueOf(selectedDestinationName)

    var isEnabled by rememberSaveable { mutableStateOf(OverlayPreferences.isOverlayEnabled(context)) }
    var showSetupDialog by remember { mutableStateOf(false) }
    var musicTabIndex by rememberSaveable { mutableStateOf(0) }
    var flashlightTabIndex by rememberSaveable { mutableStateOf(0) }

    val cutoutConfig by OverlayPreferences.cutoutConfigFlow.collectAsState()

    val isFlashlightOn by FlashlightController.isFlashlightOn.collectAsState()
    val showFlashlightIsland by OverlayPreferences.showFlashlightIslandFlow.collectAsState()

    // Initialize Flashlight controller
    LaunchedEffect(Unit) {
        FlashlightController.init(context)
    }

    // Ensure overlay service is active when overlay is enabled OR when flashlight is on
    LaunchedEffect(isEnabled, isFlashlightOn, showFlashlightIsland) {
        val shouldShow = isEnabled || (isFlashlightOn && showFlashlightIsland)
        val hasAccessibility = ca.saboor.larpdot.service.DotAccessibilityService.isServiceConnected.value
        val hasOverlay = Settings.canDrawOverlays(context)
        if (shouldShow) {
            if (!hasAccessibility && hasOverlay) {
                ca.saboor.larpdot.service.DotOverlayService.start(context)
            }
        }
    }

    // Automatically locate the camera hole punch cutout
    var cutoutInfo by remember { mutableStateOf(CutoutDetector.detect(context)) }

    // Re-detect cutout position when orientation / configuration or manual alignment changes
    LaunchedEffect(configuration, cutoutConfig) {
        cutoutInfo = CutoutDetector.detect(context)
    }

    // Synchronize overlay service when toggle state changes
    fun handleToggle(enabled: Boolean) {
        isEnabled = enabled
        ca.saboor.larpdot.service.OverlayPreferences.setOverlayEnabled(context, enabled)
        if (enabled) {
            val hasAccessibility = ca.saboor.larpdot.service.DotAccessibilityService.isAccessibilityEnabled(context)
            val hasOverlay = Settings.canDrawOverlays(context)
            if (hasAccessibility) {
                // Accessibility Service manages TYPE_ACCESSIBILITY_OVERLAY directly
                ca.saboor.larpdot.service.DotOverlayService.stop(context)
            } else if (hasOverlay) {
                // Fallback standard overlay
                ca.saboor.larpdot.service.DotOverlayService.start(context)
            } else {
                // Prompt user to grant permissions
                showSetupDialog = true
            }
        } else {
            ca.saboor.larpdot.service.DotOverlayService.stop(context)
        }
    }

    if (showSetupDialog) {
        PermissionsSetupDialog(
            onDismiss = {
                showSetupDialog = false
                val hasAccessibility = ca.saboor.larpdot.service.DotAccessibilityService.isAccessibilityEnabled(context)
                val hasOverlay = Settings.canDrawOverlays(context)
                if (isEnabled) {
                    if (hasAccessibility) {
                        ca.saboor.larpdot.service.DotOverlayService.stop(context)
                    } else if (hasOverlay) {
                        ca.saboor.larpdot.service.DotOverlayService.start(context)
                    }
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        val hasAccessibility = ca.saboor.larpdot.service.DotAccessibilityService.isAccessibilityEnabled(context)
                        val statusText = if (!isEnabled) {
                            "STANDBY"
                        } else if (hasAccessibility) {
                            "SHADE & LOCK ON"
                        } else {
                            "OVERLAY ON"
                        }

                        Column {
                            Text(
                                text = "LarpDot",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Crossfade(statusText, animationSpec = LarpMotion.fade(180), label = "overlay_status") { status ->
                                Text(
                                    text = status,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (status == "STANDBY") MaterialTheme.colorScheme.outline else Color(0xFF00E676),
                                )
                            }
                        }
                    },
                    actions = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FilledTonalButton(
                                onClick = { showSetupDialog = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier.height(32.dp),
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Setup",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Switch(
                                checked = isEnabled,
                                onCheckedChange = { handleToggle(it) },
                                modifier = Modifier.padding(end = 12.dp),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            },
            bottomBar = {
                ShortNavigationBar {
                    Destination.entries.forEach { item ->
                        ShortNavigationBarItem(
                            selected = item == selectedDestination,
                            onClick = { selectedDestinationName = item.name },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        ) { innerPadding ->
            AnimatedContent(
                targetState = selectedDestination,
                modifier = Modifier.padding(innerPadding).fillMaxSize().clipToBounds(),
                label = "root_tab",
                transitionSpec = {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    val enter = fadeIn(LarpMotion.timing(250, delayMillis = 30, easing = LarpMotion.standard)) +
                        slideInHorizontally(LarpMotion.timing(280, easing = LarpMotion.settle)) { direction * tabTravel }
                    val exit = fadeOut(LarpMotion.fade(160)) +
                        slideOutHorizontally(LarpMotion.fade(200)) { -direction * tabTravel / 2 }
                    (enter togetherWith exit).using(null)
                },
            ) { destination ->
                when (destination) {
                    Destination.Home -> HomeScreen(
                        cutoutInfo = cutoutInfo,
                        isEnabled = isEnabled,
                    )
                    Destination.Music -> MusicScreen(
                        cutoutInfo = cutoutInfo,
                        selectedTabIndex = musicTabIndex,
                        onTabSelected = { musicTabIndex = it },
                    )
                    Destination.LiveActivities -> LiveActivitiesScreen()
                    Destination.Flashlight -> FlashlightScreen(
                        cutoutInfo = cutoutInfo,
                        selectedTabIndex = flashlightTabIndex,
                        onTabSelected = { flashlightTabIndex = it },
                    )
                }
            }
        }
    }
}
