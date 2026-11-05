package io.github.adit0132.rockscreen.ui

import android.app.KeyguardManager
import android.os.PowerManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import io.github.adit0132.rockscreen.R
import io.github.adit0132.rockscreen.service.LockScreenLauncher
import io.github.adit0132.rockscreen.ui.theme.RockscreenTheme
import io.github.adit0132.rockscreen.util.lockScreenEnabledFlow
import java.text.DateFormatSymbols
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.palette.graphics.Palette
import android.graphics.drawable.BitmapDrawable
import io.github.adit0132.rockscreen.data.model.LyricsResult
import io.github.adit0132.rockscreen.data.model.TrackInfo
import io.github.adit0132.rockscreen.service.MediaListenerService
import io.github.adit0132.rockscreen.ui.theme.DmSansFamily
import io.github.adit0132.rockscreen.ui.theme.PlayfairDisplayFamily
import io.github.adit0132.rockscreen.ui.theme.SpaceGroteskFamily
import io.github.adit0132.rockscreen.util.LyricFont
import io.github.adit0132.rockscreen.util.VisualPrefs
import io.github.adit0132.rockscreen.util.visualPrefsFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class LockScreenActivity : ComponentActivity() {

    companion object {
        /** True while an instance exists, so the launcher doesn't re-post its notification. */
        @Volatile
        var isAlive = false
            private set

        fun intent(context: Context) = Intent(context, LockScreenActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                Intent.FLAG_ACTIVITY_NO_ANIMATION
        )
    }

    private val viewModel: LockScreenViewModel by viewModels()

    // The lyrics screen belongs to the lock screen: once the user unlocks, get out of the way.
    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isAlive = true
        LockScreenLauncher.dismissNotification(this)

        // Show over the lock screen. The screen is never turned on by the app; the user wakes it.
        setShowWhenLocked(true)
        // USER_PRESENT is sent by SystemUI (not uid 1000), so a NOT_EXPORTED receiver never gets it
        // (verified on Android 16). It's a protected broadcast, so apps can't spoof it.
        ContextCompat.registerReceiver(
            this, unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_EXPORTED
        )

        // Hide system navigation and status bars fully for immersive lock screen
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            val track by viewModel.currentTrack.collectAsStateWithLifecycle()
            val lyricsResult by viewModel.lyricsResult.collectAsStateWithLifecycle()
            val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
            val position by viewModel.position.collectAsStateWithLifecycle()
            val visualPrefs by applicationContext.visualPrefsFlow()
                .collectAsStateWithLifecycle(initialValue = VisualPrefs())
            val lockScreenEnabled by applicationContext.lockScreenEnabledFlow()
                .collectAsStateWithLifecycle(initialValue = true)

            // Turning the feature off in Settings closes the lyrics screen.
            LaunchedEffect(lockScreenEnabled) {
                if (!lockScreenEnabled) finish()
            }

            // Backstop for USER_PRESENT: a fast (e.g. fingerprint) unlock can complete before this screen
            // appears, and then no broadcast arrives. Never stay on top of an unlocked, awake phone.
            LaunchedEffect(Unit) {
                val keyguard = getSystemService(KeyguardManager::class.java)
                val power = getSystemService(PowerManager::class.java)
                while (true) {
                    if (power.isInteractive && !keyguard.isKeyguardLocked) {
                        finish()
                        break
                    }
                    kotlinx.coroutines.delay(500)
                }
            }

            // Keep the screen on only while lyrics are actually playing on the lock screen.
            val keepOn = isPlaying && lyricsResult is LyricsResult.Found && lockScreenEnabled
            DisposableEffect(keepOn) {
                if (keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                onDispose { }
            }

            // Finish activity if playback stops
            LaunchedEffect(isPlaying) {
                if (!isPlaying) {
                    // Give a 30s grace period before dismissing
                    kotlinx.coroutines.delay(30_000)
                    if (!viewModel.isPlaying.value) finish()
                }
            }

            // Active line, computed here while visible (no background ticker). -1 before the first line.
            val lines = (lyricsResult as? LyricsResult.Found)?.lines.orEmpty()
            var lyricIndex by remember { mutableIntStateOf(-1) }
            LaunchedEffect(lines, position) {
                while (true) {
                    val pos = position.now()
                    lyricIndex = lines.indexOfLast { it.timestampMs <= pos }
                    if (!position.playing) break
                    kotlinx.coroutines.delay(100)
                }
            }

            RockscreenTheme {
                LockScreenContent(
                    track = track,
                    lyricsResult = lyricsResult,
                    activeIndex = lyricIndex,
                    isPlaying = isPlaying,
                    visualPrefs = visualPrefs
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        LockScreenLauncher.dismissNotification(this)
    }

    override fun onDestroy() {
        isAlive = false
        try { unregisterReceiver(unlockReceiver) } catch (_: IllegalArgumentException) {}
        super.onDestroy()
    }
}

@Composable
fun LockScreenContent(
    track: TrackInfo?,
    lyricsResult: LyricsResult,
    activeIndex: Int,
    isPlaying: Boolean,
    visualPrefs: VisualPrefs
) {
    // Clock
    var time by remember { mutableStateOf(Calendar.getInstance()) }
    LaunchedEffect(Unit) {
        while (true) {
            time = Calendar.getInstance()
            kotlinx.coroutines.delay(1000)
        }
    }

    val context = LocalContext.current
    var dominantColor by remember { mutableStateOf(Color(0xFF0A0F14)) }
    var vibrantColor by remember { mutableStateOf(Color(0xFF1A3A2A)) }
    var mutedColor by remember { mutableStateOf(Color(0xFF0D1F3C)) }

    LaunchedEffect(track?.albumArtUri) {
        if (track?.albumArtUri != null) {
            val request = ImageRequest.Builder(context)
                .data(track.albumArtUri)
                .allowHardware(false) // Required for Palette API
                .build()
            val result = context.imageLoader.execute(request)
            if (result is SuccessResult) {
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    val palette = Palette.Builder(bitmap).generate()
                    palette.dominantSwatch?.rgb?.let { dominantColor = Color(it) }
                    palette.vibrantSwatch?.rgb?.let { vibrantColor = Color(it) }
                        ?: palette.lightVibrantSwatch?.rgb?.let { vibrantColor = Color(it) }
                    palette.mutedSwatch?.rgb?.let { mutedColor = Color(it) }
                        ?: palette.darkVibrantSwatch?.rgb?.let { mutedColor = Color(it) }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0F14))
    ) {
        // Background blobs
        BackgroundBlobs(dominantColor, vibrantColor, mutedColor, visualPrefs.blobIntensity)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(70.dp)) // ~1.5cm clearance for hole-punch cameras

            // Clock (conditionally shown)
            if (visualPrefs.showClock) {
                ClockBlock(time)
                Spacer(Modifier.height(12.dp))
            }

            // Track pill
            if (track != null) {
                TrackPill(track = track, isPlaying = isPlaying)
            }

            Spacer(Modifier.height(16.dp))

            // Lyrics
            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = lyricsResult,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(500)) togetherWith fadeOut(animationSpec = tween(500))
                    },
                    label = "Lyrics Transition"
                ) { state ->
                    when (state) {
                        is LyricsResult.Found -> {
                            LyricsScroller(
                                lines = state.lines.map { it.text },
                                activeIndex = activeIndex,
                                visualPrefs = visualPrefs
                            )
                        }
                        LyricsResult.NotFound -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    stringResource(R.string.lyrics_not_found),
                                    color = Color.White.copy(alpha = 0.25f),
                                    fontFamily = DmSansFamily,
                                    fontSize = 15.sp
                                )
                            }
                        }
                        LyricsResult.Loading -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    stringResource(R.string.lyrics_loading),
                                    color = Color.White.copy(alpha = 0.25f),
                                    fontFamily = DmSansFamily,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }

        }
    }
}

@Composable
fun BackgroundBlobs(dominantColor: Color, vibrantColor: Color, mutedColor: Color, blobIntensity: Float) {
    val animDominant by animateColorAsState(targetValue = dominantColor, animationSpec = tween(durationMillis = 2000), label = "animDominant")
    val animVibrant by animateColorAsState(targetValue = vibrantColor, animationSpec = tween(durationMillis = 2000), label = "animVibrant")
    val animMuted by animateColorAsState(targetValue = mutedColor, animationSpec = tween(durationMillis = 2000), label = "animMuted")

    // Normalize so that default blobIntensity=0.5f produces the original alphas
    val scale = blobIntensity / 0.5f

    Canvas(modifier = Modifier.fillMaxSize()) {
        // Top-left blob -> Muted
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(animMuted.copy(alpha = (0.7f * scale).coerceAtMost(1f)), Color.Transparent),
                center = Offset(-60f, -60f),
                radius = 500f
            ),
            radius = 500f,
            center = Offset(-60f, -60f)
        )
        // Top-right blob -> Vibrant
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(animVibrant.copy(alpha = (0.6f * scale).coerceAtMost(1f)), Color.Transparent),
                center = Offset(size.width + 80f, size.height * 0.25f),
                radius = 400f
            ),
            radius = 400f,
            center = Offset(size.width + 80f, size.height * 0.25f)
        )
        // Bottom-left blob -> Dominant
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(animDominant.copy(alpha = (0.5f * scale).coerceAtMost(1f)), Color.Transparent),
                center = Offset(80f, size.height - 150f),
                radius = 450f
            ),
            radius = 450f,
            center = Offset(80f, size.height - 150f)
        )
    }
}

@Composable
fun ClockBlock(time: Calendar) {
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val hour = if (is24Hour) time.get(Calendar.HOUR_OF_DAY) else time.get(Calendar.HOUR).let { if (it == 0) 12 else it }
    val minute = String.format("%02d", time.get(Calendar.MINUTE))
    val ampm = if (is24Hour) "" else DateFormatSymbols(Locale.getDefault()).amPmStrings[time.get(Calendar.AM_PM)]
    val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
    val dateStr = dateFormat.format(time.time)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "$hour:$minute",
                fontSize = 76.sp,
                fontFamily = SpaceGroteskFamily,
                fontWeight = FontWeight.Light,
                color = Color.White.copy(alpha = 0.88f),
                letterSpacing = (-3).sp
            )
            if (ampm.isNotEmpty()) Text(
                text = " $ampm",
                fontSize = 22.sp,
                fontFamily = SpaceGroteskFamily,
                fontWeight = FontWeight.Normal,
                color = Color.White.copy(alpha = 0.45f),
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }
        Text(
            text = dateStr,
            fontSize = 13.sp,
            fontFamily = DmSansFamily,
            fontWeight = FontWeight.Normal,
            color = Color.White.copy(alpha = 0.35f)
        )
    }
}

@Composable
fun TrackPill(track: TrackInfo, isPlaying: Boolean) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.10f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .widthIn(max = 340.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Album art
        if (track.albumArtUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(track.albumArtUri)
                    .crossfade(true)
                    .build(),
                contentDescription = stringResource(R.string.album_art),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF4A7C5A), Color(0xFF2D5A8A), Color(0xFF7A4A2A))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("♪", fontSize = 14.sp, color = Color.White)
            }
        }

        // Title + artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                fontSize = 12.sp,
                fontFamily = DmSansFamily,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = buildString {
                append(track.artist)
                if (track.album.isNotBlank()) append(" · ${track.album}")
            }
            Text(
                text = subtitle,
                fontSize = 11.sp,
                fontFamily = DmSansFamily,
                fontWeight = FontWeight.Normal,
                color = Color.White.copy(alpha = 0.40f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Play/pause controls
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = {
                    context.startService(Intent(context, MediaListenerService::class.java).apply { action = "io.github.adit0132.rockscreen.ACTION_PREV" })
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = stringResource(R.string.action_previous),
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }
            
            IconButton(
                onClick = {
                    val action = if (isPlaying) "io.github.adit0132.rockscreen.ACTION_PAUSE" else "io.github.adit0132.rockscreen.ACTION_PLAY"
                    context.startService(Intent(context, MediaListenerService::class.java).apply { this.action = action })
                },
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(if (isPlaying) R.string.action_pause else R.string.action_play),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(
                onClick = {
                    context.startService(Intent(context, MediaListenerService::class.java).apply { action = "io.github.adit0132.rockscreen.ACTION_NEXT" })
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = stringResource(R.string.action_next),
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun LyricsScroller(lines: List<String>, activeIndex: Int, visualPrefs: VisualPrefs) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val centerOffsetPx = with(LocalDensity.current) { 152.dp.roundToPx() }

    // Resolve font family from user preference
    val fontFamily: FontFamily = when (visualPrefs.lyricFont) {
        LyricFont.DM_SANS -> DmSansFamily
        LyricFont.SPACE_GROTESK -> SpaceGroteskFamily
        LyricFont.PLAYFAIR_DISPLAY -> PlayfairDisplayFamily
    }

    // Focus intensity: derive opacity cascade via lerp
    // At focusIntensity=0.0 (low): nearest=0.55, mid=0.42, far=0.32
    // At focusIntensity=1.0 (high): nearest=0.22, mid=0.14, far=0.08
    // At focusIntensity=0.5 (default): nearest≈0.385, mid≈0.28, far≈0.20 (matches original hardcoded values)
    val fi = visualPrefs.focusIntensity
    val nearestOpacity = lerp(0.55f, 0.22f, fi)
    val midOpacity = lerp(0.42f, 0.14f, fi)
    val farOpacity = lerp(0.32f, 0.08f, fi)

    // Auto-scroll to active line
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && activeIndex < lines.size) {
            coroutineScope.launch {
                listState.animateScrollToItem(
                    index = activeIndex,
                    scrollOffset = -centerOffsetPx // negative offset to center the line
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 40.dp)
        ) {
            itemsIndexed(lines) { index, line ->
                val diff = index - activeIndex
                val isActive = diff == 0

                // Animated opacity using focus intensity-derived values
                val targetOpacity = when {
                    isActive -> 1.0f
                    diff == -1 || diff == 1 -> if (diff < 0) nearestOpacity else (nearestOpacity + 0.07f).coerceAtMost(1f)
                    diff == -2 || diff == 2 -> midOpacity
                    else -> farOpacity
                }
                val animatedOpacity by animateFloatAsState(
                    targetValue = targetOpacity,
                    animationSpec = tween(500, easing = FastOutSlowInEasing),
                    label = "lyric_opacity_$index"
                )

                val targetSize = when {
                    isActive -> visualPrefs.activeLyricSize
                    kotlin.math.abs(diff) == 1 -> 23f
                    else -> 22f
                }
                val animatedSize by animateFloatAsState(
                    targetValue = targetSize,
                    animationSpec = tween(500, easing = FastOutSlowInEasing),
                    label = "lyric_size_$index"
                )

                val targetOffset = if (isActive) 2f else 0f
                val animatedOffset by animateFloatAsState(
                    targetValue = targetOffset,
                    animationSpec = tween(500, easing = FastOutSlowInEasing),
                    label = "lyric_offset_$index"
                )

                Text(
                    text = line,
                    fontSize = animatedSize.sp,
                    fontFamily = fontFamily,
                    fontWeight = when {
                        isActive -> FontWeight.Bold
                        index < activeIndex -> FontWeight.Normal
                        else -> FontWeight.Medium
                    },
                    color = Color.White.copy(alpha = animatedOpacity),
                    lineHeight = (animatedSize * 1.35f).sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = animatedOffset.dp,
                            bottom = if (isActive) 22.dp else 18.dp
                        )
                )
            }
        }

        // Top fade
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0A0F14).copy(alpha = 0.90f), Color.Transparent)
                    )
                )
        )

        // Bottom fade
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xFF0A0F14).copy(alpha = 0.95f))
                    )
                )
        )
    }
}
