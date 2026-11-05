package io.github.adit0132.rockscreen.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.adit0132.rockscreen.R
import io.github.adit0132.rockscreen.service.LockScreenLauncher
import io.github.adit0132.rockscreen.ui.theme.DmSansFamily
import io.github.adit0132.rockscreen.ui.theme.SpaceGroteskFamily
import io.github.adit0132.rockscreen.ui.theme.RockscreenTheme
import io.github.adit0132.rockscreen.util.LyricFont
import io.github.adit0132.rockscreen.util.VisualPrefs
import io.github.adit0132.rockscreen.util.lockScreenEnabledFlow
import io.github.adit0132.rockscreen.util.saveVisualPrefs
import io.github.adit0132.rockscreen.util.setLockScreenEnabled
import io.github.adit0132.rockscreen.util.visualPrefsFlow
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RockscreenTheme {
                SettingsScreen()
            }
        }
    }
}

private fun Context.hasNotificationAccess(): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

private fun Context.openSettings(vararg intents: Intent) {
    for (intent in intents) {
        try {
            startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
            // Not available on this device; try the next one.
        }
    }
}

private fun Context.appDetailsIntent() =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val lockScreenEnabled by context.lockScreenEnabledFlow()
        .collectAsStateWithLifecycle(initialValue = true)

    var visualPrefs by remember { mutableStateOf(VisualPrefs()) }
    val savedPrefs by context.visualPrefsFlow()
        .collectAsStateWithLifecycle(initialValue = VisualPrefs())

    // Sync DataStore values into local state
    LaunchedEffect(savedPrefs) {
        visualPrefs = savedPrefs
    }

    // Permission state, re-checked whenever the user comes back from system settings.
    var notificationAccessGranted by remember { mutableStateOf(context.hasNotificationAccess()) }
    var notificationsAllowed by remember { mutableStateOf(LockScreenLauncher.notificationsAllowed(context)) }
    var fullScreenAllowed by remember { mutableStateOf(LockScreenLauncher.fullScreenIntentAllowed(context)) }
    var overlayAllowed by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    fun refreshPermissions() {
        notificationAccessGranted = context.hasNotificationAccess()
        notificationsAllowed = LockScreenLauncher.notificationsAllowed(context)
        fullScreenAllowed = LockScreenLauncher.fullScreenIntentAllowed(context)
        overlayAllowed = Settings.canDrawOverlays(context)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshPermissions()
        if (!granted) {
            // Denied before ("don't ask again"): the system dialog won't show, so open the app's notification settings.
            context.openSettings(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                context.appDetailsIntent()
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080810))
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            stringResource(R.string.app_name),
            fontSize = 32.sp,
            fontFamily = SpaceGroteskFamily,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            stringResource(R.string.app_tagline),
            fontSize = 14.sp,
            fontFamily = DmSansFamily,
            color = Color.White.copy(alpha = 0.45f)
        )

        Spacer(Modifier.height(8.dp))

        // Permission warning
        if (!notificationAccessGranted) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0x20ef4444),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.notif_access_title),
                        fontSize = 13.sp,
                        fontFamily = DmSansFamily,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFef4444)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.notif_access_body),
                        fontSize = 12.sp,
                        fontFamily = DmSansFamily,
                        color = Color.White.copy(alpha = 0.60f),
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.notif_access_restricted_hint),
                        fontSize = 11.sp,
                        fontFamily = DmSansFamily,
                        color = Color.White.copy(alpha = 0.45f),
                        lineHeight = 16.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            context.openSettings(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                                context.appDetailsIntent()
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFef4444))
                    ) {
                        Text(stringResource(R.string.open_settings), fontSize = 12.sp)
                    }
                }
            }
        }

        // Main toggle
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0e0e18),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.lock_toggle_title),
                        fontSize = 14.sp,
                        fontFamily = DmSansFamily,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        stringResource(R.string.lock_toggle_body),
                        fontSize = 12.sp,
                        fontFamily = DmSansFamily,
                        color = Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.widthIn(max = 220.dp),
                        lineHeight = 18.sp
                    )
                }
                Switch(
                    checked = lockScreenEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch { context.setLockScreenEnabled(enabled) }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF7C3AED)
                    )
                )
            }
        }

        // ─── Lock screen access ─────────────────────────────────
        // Android only lets the lyrics screen onto the lock screen via a full-screen notification,
        // or (fallback) with the "Display over other apps" grant.
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0e0e18),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    stringResource(R.string.lock_access_section),
                    fontSize = 14.sp,
                    fontFamily = DmSansFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PermissionRow(
                        title = stringResource(R.string.perm_notifications_title),
                        body = stringResource(R.string.perm_notifications_body),
                        granted = notificationsAllowed,
                        actionLabel = stringResource(R.string.allow),
                        onAction = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    PermissionRow(
                        title = stringResource(R.string.perm_fsi_title),
                        body = stringResource(R.string.perm_fsi_body),
                        granted = fullScreenAllowed,
                        actionLabel = stringResource(R.string.open_settings),
                        onAction = {
                            context.openSettings(
                                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")),
                                context.appDetailsIntent()
                            )
                        }
                    )
                }
                PermissionRow(
                    title = stringResource(R.string.perm_overlay_title),
                    body = stringResource(R.string.perm_overlay_body),
                    granted = overlayAllowed,
                    actionLabel = stringResource(R.string.open_settings),
                    onAction = {
                        context.openSettings(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
                            context.appDetailsIntent()
                        )
                    }
                )
            }
        }

        // ─── Appearance Section ─────────────────────────────────
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.appearance),
            fontSize = 20.sp,
            fontFamily = SpaceGroteskFamily,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.85f)
        )

        // Background Intensity
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0e0e18),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.bg_intensity_title),
                    fontSize = 14.sp,
                    fontFamily = DmSansFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    stringResource(R.string.bg_intensity_body),
                    fontSize = 12.sp,
                    fontFamily = DmSansFamily,
                    color = Color.White.copy(alpha = 0.45f),
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = visualPrefs.blobIntensity,
                    onValueChange = { newVal ->
                        visualPrefs = visualPrefs.copy(blobIntensity = newVal)
                        scope.launch { context.saveVisualPrefs(visualPrefs) }
                    },
                    valueRange = 0.1f..0.8f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF7C3AED),
                        activeTrackColor = Color(0xFF7C3AED)
                    )
                )
            }
        }

        // Lyric Size
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0e0e18),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.lyric_size_title),
                    fontSize = 14.sp,
                    fontFamily = DmSansFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    stringResource(R.string.lyric_size_body),
                    fontSize = 12.sp,
                    fontFamily = DmSansFamily,
                    color = Color.White.copy(alpha = 0.45f),
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = visualPrefs.activeLyricSize,
                    onValueChange = { newVal ->
                        visualPrefs = visualPrefs.copy(activeLyricSize = newVal)
                        scope.launch { context.saveVisualPrefs(visualPrefs) }
                    },
                    valueRange = 20f..32f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF7C3AED),
                        activeTrackColor = Color(0xFF7C3AED)
                    )
                )
            }
        }

        // Focus Intensity
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0e0e18),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.focus_title),
                    fontSize = 14.sp,
                    fontFamily = DmSansFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    stringResource(R.string.focus_body),
                    fontSize = 12.sp,
                    fontFamily = DmSansFamily,
                    color = Color.White.copy(alpha = 0.45f),
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = visualPrefs.focusIntensity,
                    onValueChange = { newVal ->
                        visualPrefs = visualPrefs.copy(focusIntensity = newVal)
                        scope.launch { context.saveVisualPrefs(visualPrefs) }
                    },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF7C3AED),
                        activeTrackColor = Color(0xFF7C3AED)
                    )
                )
            }
        }

        // Lyric Font
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0e0e18),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.font_title),
                    fontSize = 14.sp,
                    fontFamily = DmSansFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    data class FontOption(val font: LyricFont, val label: String)
                    val options = listOf(
                        FontOption(LyricFont.DM_SANS, stringResource(R.string.font_round)),
                        FontOption(LyricFont.SPACE_GROTESK, stringResource(R.string.font_sharp)),
                        FontOption(LyricFont.PLAYFAIR_DISPLAY, stringResource(R.string.font_serif))
                    )
                    options.forEach { option ->
                        val selected = visualPrefs.lyricFont == option.font
                        FilterChip(
                            selected = selected,
                            onClick = {
                                visualPrefs = visualPrefs.copy(lyricFont = option.font)
                                scope.launch { context.saveVisualPrefs(visualPrefs) }
                            },
                            label = {
                                Text(
                                    option.label,
                                    fontSize = 13.sp,
                                    fontFamily = DmSansFamily,
                                    color = if (selected) Color.White else Color.White.copy(alpha = 0.6f)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF7C3AED),
                                containerColor = Color(0xFF1a1a2e)
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = Color.White.copy(alpha = 0.1f),
                                selectedBorderColor = Color.Transparent,
                                enabled = true,
                                selected = selected
                            )
                        )
                    }
                }
            }
        }

        // Show Clock toggle
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0e0e18),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.show_clock),
                    fontSize = 14.sp,
                    fontFamily = DmSansFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Switch(
                    checked = visualPrefs.showClock,
                    onCheckedChange = { show ->
                        visualPrefs = visualPrefs.copy(showClock = show)
                        scope.launch { context.saveVisualPrefs(visualPrefs) }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF7C3AED)
                    )
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PermissionRow(
    title: String,
    body: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontFamily = DmSansFamily, fontWeight = FontWeight.Medium, color = Color.White)
            Text(
                body,
                fontSize = 12.sp,
                fontFamily = DmSansFamily,
                color = Color.White.copy(alpha = 0.45f),
                lineHeight = 17.sp
            )
        }
        Spacer(Modifier.width(12.dp))
        if (granted) {
            Text(
                stringResource(R.string.status_allowed),
                fontSize = 12.sp,
                fontFamily = DmSansFamily,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF34D399)
            )
        } else {
            OutlinedButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                Text(actionLabel, fontSize = 12.sp, color = Color.White)
            }
        }
    }
}
