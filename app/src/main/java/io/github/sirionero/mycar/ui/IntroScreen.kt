package io.github.sirionero.mycar.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.BuildConfig
import io.github.sirionero.mycar.CertProvider
import io.github.sirionero.mycar.AppSettings
import io.github.sirionero.mycar.R
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.foundation.layout.wrapContentHeight
import io.github.sirionero.mycar.car.AssistantLanguage
import io.github.sirionero.mycar.speech.SpeechModels
import io.github.sirionero.mycar.speech.VoiceLanguage
import io.github.sirionero.mycar.speech.VoiceLanguages
import io.github.sirionero.mycar.stats.UsageStats
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * The release intro: a few screens shown once after updating to a new release, before setup. New
 * installs skip it. Its look follows the 0.3 mockup: Material shapes in the phone's own colours,
 * with every size taken from the screen's width so it scales from small phones to tablets.
 */
object Intro {
    private const val PREFS = "intro"
    private const val KEY_SEEN = "seen_release"
    private const val KEY_STEP = "step"
    private const val KEY_STEP_RELEASE = "step_release"

    /**
     * Bugs found and fixed from the release before to this one (0.6.0 to 0.7.0), said on the welcome
     * page: counted commit by commit, a doubtful one as a feature (the list is in the release notes).
     */
    const val BUGS_FIXED = 216

    /** "0.3" from "0.3.00-alpha": the intro belongs to a release, not to each patch. */
    val release: String = BuildConfig.VERSION_NAME.split('.').take(2).joinToString(".")

    /**
     * True once per release for people who used myCar before. A new install, or one with its
     * data cleared, gets the setup guide instead: there's nothing "new" yet, so this release
     * counts as seen and the next one shows. Install dates can't tell those apart, since
     * clearing data keeps them, so this goes by what setup left behind; 0.2 saved the same.
     */
    fun due(context: Context): Boolean {
        if (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SEEN, null) == release) return false
        if (!usedBefore(context)) {
            markSeen(context)
            return false
        }
        return true
    }

    /** Whether myCar was set up before: an install that wasn't is new, with nothing "new" to show yet. */
    internal fun usedBefore(context: Context) = AppSettings.hasSeenPermissionsSetup(context) ||
        AppSettings.hasSkippedCertSetup(context) || CertProvider.hasAnyCert(context)

    fun markSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SEEN, release)
            .remove(KEY_STEP)
            .remove(KEY_STEP_RELEASE)
            .apply()
    }

    /** A replay from Settings starts at the first page. */
    fun replay(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_STEP).remove(KEY_STEP_RELEASE).apply()
    }

    /**
     * The page the intro was on, so an app restart part-way through resumes there. Only a page of
     * this release's intro: one left half-way through an older intro (saved without its release
     * before 0.7) would skip this one's first pages, so that starts at the top.
     */
    internal fun savedStep(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_STEP, null)?.takeIf { prefs.getString(KEY_STEP_RELEASE, null) == release }
    }

    internal fun saveStep(context: Context, step: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_STEP, step)
            .putString(KEY_STEP_RELEASE, release)
            .apply()
    }
}

/** The intro's tones, taken from the phone's own Material You colours like the rest of the app. */
internal class IntroColors(
    val surface: Color, val onSurface: Color, val onVariant: Color, val outline: Color,
    val primary: Color, val onPrimary: Color, val primaryC: Color, val onPrimaryC: Color,
    val tertiaryC: Color, val onTertiaryC: Color, val secondaryC: Color, val onSecondaryC: Color,
)

@Composable
internal fun introColors(): IntroColors {
    val m = MaterialTheme.colorScheme
    return IntroColors(
        surface = m.surface, onSurface = m.onSurface, onVariant = m.onSurfaceVariant, outline = m.outline,
        primary = m.primary, onPrimary = m.onPrimary, primaryC = m.primaryContainer, onPrimaryC = m.onPrimaryContainer,
        tertiaryC = m.tertiaryContainer, onTertiaryC = m.onTertiaryContainer,
        secondaryC = m.secondaryContainer, onSecondaryC = m.onSecondaryContainer,
    )
}

/**
 * In the order shown: what's new since 0.6 (music inside myCar, the mini player, lyrics, the map's
 * position, the car's settings on the phone), the voice download, then housekeeping. A name saved
 * by an older intro that's gone from here just starts at the top (see [Intro.savedStep]).
 */
private enum class IntroStep { WELCOME, MUSIC, MINI_PLAYER, LYRICS, LOCATION, CAR_ON_PHONE, VOICE, GRAPHENE }

private val Pop = CubicBezierEasing(0.3f, 1.6f, 0.5f, 1f)
private val Rise = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

@Composable
fun IntroScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val c = introColors()
    // The GrapheneOS screen is only news to people running it.
    val steps = remember {
        IntroStep.entries.filter { it != IntroStep.GRAPHENE || UsageStats.osName(context) == "GrapheneOS" }
    }
    // Turning exploit protection back on restarts the app, so the intro picks up where it was.
    var index by remember {
        mutableIntStateOf(steps.indexOfFirst { it.name == Intro.savedStep(context) }.coerceAtLeast(0))
    }
    val step = steps[index]
    LaunchedEffect(step) { Intro.saveStep(context, step.name) }
    val finish = {
        Intro.markSeen(context)
        onDone()
    }
    BackHandler(enabled = index > 0) { index-- }

    BoxWithConstraints(Modifier.fillMaxSize().background(c.surface)) {
        val w = maxWidth
        val h = maxHeight
        // The mockup's cqw: one percent of the screen's width.
        fun cq(n: Float): Dp = w * (n / 100f)
        val density = LocalDensity.current
        fun cqSp(n: Float): TextUnit = with(density) { cq(n).toSp() }

        // The step on top, Back and Next under it, the same as the setup guide.
        Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
        when (step) {
            IntroStep.WELCOME -> Welcome(c, ::cq, ::cqSp, h)
            IntroStep.MUSIC -> BadgeStep(c, ::cq, ::cqSp, c.tertiaryC, stringResource(R.string.intro_music_title),
                stringResource(R.string.intro_music_body), step,
                extra = { ServiceTicker(c, ::cq, ::cqSp, step) }) { DancingNoteIcon(c.onTertiaryC) }
            IntroStep.MINI_PLAYER -> BadgeStep(c, ::cq, ::cqSp, c.secondaryC, stringResource(R.string.intro_mini_title),
                stringResource(R.string.intro_mini_body), step) { MiniPlayerIcon(c.onSecondaryC, c.secondaryC) }
            IntroStep.LYRICS -> BadgeStep(c, ::cq, ::cqSp, c.primaryC, stringResource(R.string.intro_lyrics_title),
                stringResource(R.string.intro_lyrics_body), step) { LyricsIcon(c.onPrimaryC) }
            IntroStep.LOCATION -> BadgeStep(c, ::cq, ::cqSp, c.tertiaryC, stringResource(R.string.intro_location_title),
                stringResource(R.string.intro_location_body), step) { LocationIcon(c.onTertiaryC) }
            IntroStep.CAR_ON_PHONE -> BadgeStep(c, ::cq, ::cqSp, c.secondaryC, stringResource(R.string.intro_phone_title),
                stringResource(R.string.intro_phone_body), step) { PhoneSwitchesIcon(c.onSecondaryC, c.secondaryC) }
            IntroStep.VOICE -> VoiceStep(c, ::cq, ::cqSp, step)
            IntroStep.GRAPHENE -> BadgeStep(c, ::cq, ::cqSp, c.secondaryC, stringResource(R.string.intro_graphene_title),
                stringResource(R.string.intro_graphene_body), step,
                action = stringResource(R.string.intro_graphene_open_settings) to {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    }
                },
                outlined = true,
            ) { ShieldIcon(c.onSecondaryC, c.secondaryC) }
        }
        }
        // Above the three-button navigation bar, which covered Next and Start.
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = cq(8f))) {
            Dots(c, steps.size, index, ::cq, Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(cq(5f)))
            PillRow(
                c, ::cq, ::cqSp,
                onBack = if (index > 0) ({ index-- }) else null,
                label = if (index == steps.lastIndex) stringResource(R.string.intro_start) else stringResource(R.string.common_next),
            ) { if (index < steps.lastIndex) index++ else finish() }
            Spacer(Modifier.height(cq(6f)))
        }
        }
    }
}

/** 0 to 1 once each time [key] changes, after [delayMs]: drives the entrance of one element. */
@Composable
internal fun entrance(key: Any, delayMs: Int, durationMs: Int = 650): Float {
    val a = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(delayMs.toLong())
        a.animateTo(1f, tween(durationMs, easing = LinearEasing))
    }
    return a.value
}

/** Slides up and fades in, like the mockup's `up`. */
internal fun Modifier.up(p: Float, distance: Dp) = graphicsLayer {
    val e = Rise.transform(p)
    alpha = e
    translationY = (1 - e) * distance.toPx()
}

/** Grows in with a bounce, like the mockup's `bounce`. */
internal fun Modifier.pop(p: Float) = graphicsLayer {
    val e = Pop.transform(p)
    alpha = p.coerceAtMost(1f)
    scaleX = 0.5f + 0.5f * e
    scaleY = 0.5f + 0.5f * e
}

@Composable
private fun Welcome(c: IntroColors, cq: (Float) -> Dp, cqSp: (Float) -> TextUnit, height: Dp) {
    val key = IntroStep.WELCOME
    Box(Modifier.fillMaxSize()) {
        // The intro comes with a big release: the pirate ship sails in and runs its flag up the main
        // mast (see PirateShip), where the flag alone flew at the top before. As big as the phone
        // leaves room for above the welcome.
        val shipHeight = minOf(cq(74f), height * 0.5f)
        // The words take the room they need at the foot; the ship what's left above them, up to its size:
        // on a short phone it comes smaller instead of under the words.
        Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            PirateShip(
                key, c.onSurface,
                Modifier.align(Alignment.TopCenter).heightIn(max = shipHeight).fillMaxHeight().aspectRatio(1f / 0.74f, matchHeightConstraintsFirst = true),
            )
        }

        Column(Modifier.padding(horizontal = cq(8f)).padding(bottom = height * 0.06f)) {
            Text(stringResource(R.string.intro_welcome_to), color = c.onVariant, style = TextStyle(fontSize = cqSp(4.2f), fontWeight = FontWeight.SemiBold),
                modifier = Modifier.up(entrance(key, 100), cq(7f)))
            Text(
                Intro.release, color = c.primary,
                style = TextStyle(fontSize = cqSp(44f), fontWeight = FontWeight.Black, letterSpacing = cqSp(-2.4f), lineHeight = cqSp(38f)),
                modifier = Modifier.offset(x = -cq(1.5f)).pop(entrance(key, 250, 900)),
            )
            Text(
                stringResource(R.string.intro_welcome_tag), color = c.onTertiaryC,
                style = TextStyle(fontSize = cqSp(4f), fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(top = cq(4f)).up(entrance(key, 700), cq(7f))
                    .clip(RoundedCornerShape(50)).background(c.tertiaryC).padding(horizontal = cq(4.4f), vertical = cq(2.2f)),
            )
            // Once the ship is in: the motto by a compass, and the bugs fixed since the release before.
            WelcomeCourse(
                key, stringResource(R.string.intro_course),
                Intro.BUGS_FIXED.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.intro_bugs_fixed, it, it) },
                accent = c.primary, ink = c.onSurface, quiet = c.onVariant, cq = cq, cqSp = cqSp,
                modifier = Modifier.padding(top = cq(4.5f)),
            )
        }
        }

        // Thrown from behind the version number, under the ship.
        Confetti(c, key, Modifier.align(Alignment.BottomStart).fillMaxWidth().height(height * 0.46f))
    }
}

/** Material shapes thrown once from behind the version number. */
@Composable
private fun Confetti(c: IntroColors, key: Any, modifier: Modifier) {
    val p = entrance(key, 450, 1300)
    val bits = remember {
        val r = Random(7)
        List(22) { i ->
            val a = i / 22f * 2 * PI.toFloat() + r.nextFloat() * 0.4f
            val d = 30f + r.nextFloat() * 45f
            Bit(cos(a) * d, sin(a) * d * 1.2f, r.nextInt(-270, 270).toFloat(), i % 4, i % 3)
        }
    }
    if (p <= 0f || p >= 1f) return
    val tones = listOf(c.primary, c.primaryC, c.tertiaryC, c.secondaryC)
    Canvas(modifier) {
        val unit = size.width / 100f
        val e = CubicBezierEasing(0.15f, 0.7f, 0.3f, 1f).transform(p)
        val alpha = if (p < 0.7f) 1f else 1f - (p - 0.7f) / 0.3f
        val origin = Offset(size.width * 0.3f, size.height * 0.42f)
        bits.forEach { b ->
            val at = origin + Offset(b.dx * unit * e, b.dy * unit * e)
            val s = 3.6f * unit * (0.3f + 0.7f * e)
            rotate(b.spin * e, at) {
                val color = tones[b.tone].copy(alpha = alpha)
                when (b.shape) {
                    0 -> drawCircle(color, s / 2, at)
                    1 -> drawRoundRect(color, at - Offset(s / 2, s / 2), Size(s, s), androidx.compose.ui.geometry.CornerRadius(unit))
                    else -> drawRoundRect(color, at - Offset(s / 2, s / 4), Size(s, s / 2), androidx.compose.ui.geometry.CornerRadius(s / 4))
                }
            }
        }
    }
}

private class Bit(val dx: Float, val dy: Float, val spin: Float, val tone: Int, val shape: Int)

/** One idea per screen: a spinning cookie with an icon, a headline, a line of text, maybe a button. */
@Composable
private fun BadgeStep(
    c: IntroColors, cq: (Float) -> Dp, cqSp: (Float) -> TextUnit, badge: Color,
    title: String, body: String, key: Any,
    action: Pair<String, () -> Unit>? = null, outlined: Boolean = false,
    secondAction: Pair<String, () -> Unit>? = null,
    extra: (@Composable () -> Unit)? = null,
    icon: @Composable () -> Unit,
) {
    val spin by rememberInfiniteTransition(label = "spin").animateFloat(
        0f, 360f, infiniteRepeatable(tween(18_000, easing = LinearEasing)), label = "spin",
    )
    Column(
        Modifier.fillMaxSize().padding(horizontal = cq(8f)),
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(cq(34f)).pop(entrance(key, 50, 800)), contentAlignment = Alignment.Center) {
            Cookie(badge, Modifier.fillMaxSize().graphicsLayer { rotationZ = spin })
            Box(Modifier.size(cq(15f))) { icon() }
        }
        Spacer(Modifier.height(cq(8f)))
        Text(
            title, color = c.onSurface,
            style = TextStyle(fontSize = cqSp(13.5f), fontWeight = FontWeight.Black, letterSpacing = cqSp(-0.6f), lineHeight = cqSp(12.5f)),
            modifier = Modifier.up(entrance(key, 200, 700), cq(7f)),
        )
        Spacer(Modifier.height(cq(4f)))
        Text(
            body, color = c.onVariant,
            style = TextStyle(fontSize = cqSp(4.5f), lineHeight = cqSp(6.5f)),
            modifier = Modifier.up(entrance(key, 350, 700), cq(7f)),
        )
        extra?.invoke()
        listOfNotNull(action, secondAction).forEachIndexed { i, (label, onClick) ->
            Spacer(Modifier.height(if (i > 0) cq(3f) else if (outlined) cq(7f) else cq(9f)))
            Text(
                label,
                color = if (outlined) c.primary else c.onPrimary,
                style = TextStyle(fontSize = cqSp(if (outlined) 4.4f else 4.6f), fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .up(entrance(key, 500 + i * 150, 700), cq(7f))
                    .clip(RoundedCornerShape(50))
                    .then(
                        if (outlined) Modifier.border(BorderStroke(2.dp, c.outline), RoundedCornerShape(50))
                        else Modifier.background(c.primary),
                    )
                    .clickable(onClick = onClick)
                    .padding(horizontal = if (outlined) cq(7f) else cq(9f), vertical = if (outlined) cq(3.6f) else cq(4.2f)),
            )
        }
    }
}

/**
 * Voice: a few things to say, drifting past, and a button that fetches both speech models so the
 * assistant works the first time someone tries it in the car.
 */
@Composable
private fun VoiceStep(c: IntroColors, cq: (Float) -> Dp, cqSp: (Float) -> TextUnit, key: IntroStep) {
    val context = LocalContext.current
    remember { SpeechModels.init(context) }
    val needed = SpeechModels.recommended()
    val states = needed.map { SpeechModels.state(it).collectAsState().value }
    val ready = states.all { it == SpeechModels.State.Ready }
    val label = when {
        states.any { it is SpeechModels.State.Downloading } -> stringResource(
            R.string.intro_voice_downloading,
            needed.zip(states).sumOf { (m, st) -> m.downloadMb * percentOf(st) } / needed.sumOf { it.downloadMb },
        )
        states.any { it is SpeechModels.State.Failed } -> stringResource(R.string.intro_voice_failed)
        else -> stringResource(R.string.intro_voice_download, needed.sumOf { it.downloadMb })
    }
    val phone = VoiceLanguages.phone()
    val helpWord = AssistantLanguage.help(phone)?.last()?.first ?: "help"
    // The language's name in the words on screen.
    val screenLocale = LocalConfiguration.current.locales[0]
    BadgeStep(
        c, cq, cqSp, c.primaryC, stringResource(R.string.intro_voice_title),
        if (phone == VoiceLanguage.EN) stringResource(R.string.intro_voice_body, helpWord)
        else stringResource(R.string.intro_voice_body_language, phone.locale.getDisplayLanguage(screenLocale), helpWord),
        key,
        action = if (ready) null else label to { SpeechModels.downloadRecommended(context) },
        outlined = true,
        extra = { CommandDrift(c, cq, cqSp, key) },
    ) { MicIcon(c.onPrimaryC) }
}

private fun percentOf(state: SpeechModels.State) = when (state) {
    SpeechModels.State.Ready -> 100
    is SpeechModels.State.Downloading -> state.percent
    else -> 0
}

private val SAMPLE_COMMANDS = listOf(
    "Play Daft Punk", "What's playing?", "Next song",
    "Call Mom", "Open Organic Maps", "Pause", "Help",
)

/** Example commands rising slowly through a window that fades at both edges. */
@Composable
private fun CommandDrift(c: IntroColors, cq: (Float) -> Dp, cqSp: (Float) -> TextUnit, key: Any) {
    // In the phone's language, the one the assistant will listen in.
    val samples = remember { AssistantLanguage.samples(VoiceLanguages.phone()) ?: SAMPLE_COMMANDS }
    val scroll by rememberInfiniteTransition(label = "drift").animateFloat(
        0f, 1f, infiniteRepeatable(tween(samples.size * 2_200, easing = LinearEasing)), label = "drift",
    )
    Box(
        Modifier
            .padding(top = cq(6f))
            .fillMaxWidth()
            .height(cq(34f))
            .up(entrance(key, 500, 700), cq(7f))
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(0f to Color.Transparent, 0.25f to Color.Black, 0.75f to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
            }
            .clipToBounds(),
    ) {
        Column(
            Modifier
                .wrapContentHeight(Alignment.Top, unbounded = true)
                // The list is there twice; rising by half loops cleanly.
                .graphicsLayer { translationY = -size.height / 2f * scroll },
            verticalArrangement = Arrangement.spacedBy(cq(2.4f)),
        ) {
            repeat(2) {
                samples.forEach { say ->
                    Text(
                        stringResource(R.string.intro_voice_sample, say), color = c.onSecondaryC,
                        style = TextStyle(fontSize = cqSp(4.4f), fontWeight = FontWeight.SemiBold),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(c.secondaryC)
                            .padding(horizontal = cq(4.4f), vertical = cq(2f)),
                    )
                }
            }
        }
    }
}

/** Material's scalloped "cookie": a circle whose radius waves around its edge. */
@Composable
internal fun Cookie(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val unit = size.minDimension / 100f
        val path = Path()
        var deg = 0
        while (deg <= 360) {
            val a = deg * PI / 180
            val r = (44 + 4.5 * cos(9 * a)) * unit
            val x = center.x + (r * cos(a)).toFloat()
            val y = center.y + (r * sin(a)).toFloat()
            if (deg == 0) path.moveTo(x, y) else path.lineTo(x, y)
            deg += 3
        }
        path.close()
        drawPath(path, color)
    }
}

/** Draws in the mockup's 48-unit icon box. */
internal inline fun DrawScope.inIconBox(block: DrawScope.() -> Unit) {
    val s = size.minDimension / 48f
    scale(s, s, Offset.Zero) { block() }
}

@Composable
internal fun WifiIcon(color: Color) {
    val t = rememberInfiniteTransition(label = "wifi")
    val waves = (0..2).map { i ->
        t.animateFloat(
            0.35f, 1f,
            infiniteRepeatable(tween(800, delayMillis = 0, easing = FastOutSlowInEasing), RepeatMode.Reverse,
                initialStartOffset = androidx.compose.animation.core.StartOffset(i * 200)),
            label = "wave$i",
        )
    }
    Canvas(Modifier.fillMaxSize()) {
        inIconBox {
            val stroke = Stroke(5f, cap = StrokeCap.Round)
            // The mockup's three arcs, inner to outer: radius r through (24 ± half, y).
            listOf(Triple(8f, 6f, 34f), Triple(18f, 13f, 26f), Triple(28f, 20f, 18f)).forEachIndexed { i, (r, half, y) ->
                val cy = y + sqrt(r * r - half * half)
                val start = Math.toDegrees(atan2((y - cy).toDouble(), (-half).toDouble())).toFloat()
                val end = Math.toDegrees(atan2((y - cy).toDouble(), half.toDouble())).toFloat()
                drawArc(color.copy(alpha = waves[i].value), start, (end - start + 360f) % 360f, false,
                    Offset(24f - r, cy - r), Size(2 * r, 2 * r), style = stroke)
            }
            drawCircle(color, 3.6f, Offset(24f, 41f))
        }
    }
}

@Composable
internal fun MicIcon(color: Color) {
    val t = rememberInfiniteTransition(label = "mic")
    val waves = (0..1).map { i ->
        t.animateFloat(
            0.25f, 1f,
            infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse,
                initialStartOffset = androidx.compose.animation.core.StartOffset(i * 250)),
            label = "mic$i",
        )
    }
    Canvas(Modifier.fillMaxSize()) {
        inIconBox {
            drawRoundRect(color, Offset(18f, 5f), Size(12f, 22f), androidx.compose.ui.geometry.CornerRadius(6f))
            val stroke = Stroke(4f, cap = StrokeCap.Round)
            drawArc(color, 0f, 180f, false, Offset(12f, 14f), Size(24f, 22f), style = stroke)
            drawLine(color, Offset(24f, 36f), Offset(24f, 42f), 4f, StrokeCap.Round)
            // Sound waves either side, pulsing as if someone's talking.
            listOf(1f to 0, -1f to 0, 1f to 1, -1f to 1).forEach { (side, i) ->
                val r = if (i == 0) 15f else 21f
                drawArc(color.copy(alpha = waves[i].value), if (side > 0) -35f else 145f, 70f, false,
                    Offset(24f - r, 16f - r), Size(2 * r, 2 * r), style = stroke)
            }
        }
    }
}

@Composable
internal fun StarIcon(color: Color) {
    val turn by rememberInfiniteTransition(label = "star").animateFloat(
        -8f, 8f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "star",
    )
    Canvas(Modifier.fillMaxSize()) {
        inIconBox {
            val star = Path()
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) 20f else 8.5f
                val a = (-90 + i * 36) * PI / 180
                val x = 24f + (r * cos(a)).toFloat()
                val y = 25f + (r * sin(a)).toFloat()
                if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
            }
            star.close()
            rotate(turn, Offset(24f, 25f)) { drawPath(star, color) }
        }
    }
}

@Composable
internal fun ShieldIcon(color: Color, tick: Color) {
    val p = entrance(IntroStep.GRAPHENE, 800, 500)
    Canvas(Modifier.fillMaxSize()) {
        inIconBox {
            val shield = Path().apply {
                moveTo(24f, 4f); lineTo(40f, 10f); lineTo(40f, 22f)
                cubicTo(40f, 32f, 33f, 40f, 24f, 44f)
                cubicTo(15f, 40f, 8f, 32f, 8f, 22f)
                lineTo(8f, 10f); close()
            }
            drawPath(shield, color)
            drawPartialStroke(listOf(Offset(16f, 24f), Offset(22f, 30f), Offset(33f, 18f)), p, tick, 4.5f)
        }
    }
}

@Composable
internal fun HeartIcon(color: Color) {
    val beat by rememberInfiniteTransition(label = "beat").animateFloat(
        1f, 1f,
        infiniteRepeatable(keyframes {
            durationMillis = 1400
            1f at 0; 1.16f at 210; 1f at 420; 1.1f at 630; 1f at 840
        }),
        label = "beat",
    )
    Canvas(Modifier.fillMaxSize()) {
        inIconBox {
            scale(beat, beat, Offset(24f, 24f)) {
                val heart = Path().apply {
                    moveTo(24f, 41f)
                    cubicTo(10f, 31f, 5f, 23f, 5f, 16f)
                    cubicTo(5f, 10f, 10f, 6f, 15f, 6f)
                    cubicTo(19f, 6f, 22f, 8f, 24f, 12f)
                    cubicTo(26f, 8f, 29f, 6f, 33f, 6f)
                    cubicTo(38f, 6f, 43f, 10f, 43f, 16f)
                    cubicTo(43f, 23f, 38f, 31f, 24f, 41f)
                    close()
                }
                drawPath(heart, color)
            }
        }
    }
}

/** A polyline drawn up to [progress] of its length, so a tick can sweep in. */
private fun DrawScope.drawPartialStroke(points: List<Offset>, progress: Float, color: Color, width: Float) {
    if (progress <= 0f) return
    val lengths = points.zipWithNext { a, b -> (b - a).getDistance() }
    var left = lengths.sum() * FastOutSlowInEasing.transform(progress.coerceAtMost(1f))
    val path = Path().apply { moveTo(points[0].x, points[0].y) }
    for (i in lengths.indices) {
        if (left <= 0f) break
        val f = (left / lengths[i]).coerceAtMost(1f)
        val to = points[i] + (points[i + 1] - points[i]) * f
        path.lineTo(to.x, to.y)
        left -= lengths[i]
    }
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Composable
internal fun Dots(c: IntroColors, count: Int, current: Int, cq: (Float) -> Dp, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(cq(2f))) {
        repeat(count) { i ->
            val on = i == current
            val width by animateDpAsState(if (on) cq(7f) else cq(2f), tween(300), label = "dot")
            Box(
                Modifier
                    .size(width, cq(2f))
                    .clip(RoundedCornerShape(50))
                    .background(if (on) c.primary else c.outline.copy(alpha = 0.45f)),
            )
        }
    }
}

