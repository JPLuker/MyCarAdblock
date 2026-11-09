package io.github.sirionero.mycar.car

import io.github.sirionero.mycar.speech.VoiceLanguage
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class JollyActionTest {

    private fun roundTrip(action: JollyAction) = assertEquals(action, JollyActions.parse(JollyActions.saved(action)))

    @Test
    fun everyKindReadsBackAsSaved() {
        roundTrip(JollyAction.Call("Anna Rossi", "+39 333 123 4567", "Mobile"))
        roundTrip(JollyAction.Call("Bob", "555-0100"))
        roundTrip(JollyAction.Assistant("phone"))
        roundTrip(JollyAction.Assistant("com.anthropic.claude"))
        roundTrip(JollyAction.Home("script.open_gate", HomeAct.RUN, "Open the gate"))
        roundTrip(JollyAction.Home("lock.front_door", HomeAct.UNLOCK, "Front door"))
        roundTrip(JollyAction.Navigate("Via Roma 1, Milano", "Casa"))
        roundTrip(JollyAction.Navigate("Piazza del Duomo"))
        roundTrip(JollyAction.Say("metti Radio Deejay su Spotify", "it"))
        roundTrip(JollyAction.OpenApp("mycar:settings", "Settings"))
        assertEquals(JollyKind.HOME, JollyActions.parse(JollyActions.saved(JollyAction.Home("scene.night", HomeAct.RUN, "Night")))?.kind)
    }

    @Test
    fun whatWouldBreakALineIsEscaped() {
        roundTrip(JollyAction.Say("a&b=c 100% \"ok\"\nnext line", "en"))
        roundTrip(JollyAction.Navigate("Rue d'Alésia & Co = 5%", "Œuvre 工作 ☕"))
        roundTrip(JollyAction.OpenApp("com.example/.Main", "R&D=1"))
        val saved = JollyActions.saved(JollyAction.Say("a&b=c%", "en"))
        assertEquals("type=say&phrase=a%26b%3Dc%25&lang=en", saved)
    }

    @Test
    fun nothingKnownReadsAsNothingChosen() {
        assertNull(JollyActions.parse(""))
        assertNull(JollyActions.parse("   "))
        assertNull(JollyActions.parse("garbage"))
        // A kind from a later version, or none.
        assertNull(JollyActions.parse("type=widget&id=42"))
        assertNull(JollyActions.parse("name=Anna&number=123"))
        // Fields missing or blank.
        assertNull(JollyActions.parse("type=call&name=Anna"))
        assertNull(JollyActions.parse("type=call&name=%20&number=123"))
        assertNull(JollyActions.parse("type=call&name=Anna&number=abc"))
        assertNull(JollyActions.parse("type=ai"))
        assertNull(JollyActions.parse("type=navigate&name=Home"))
        assertNull(JollyActions.parse("type=app&name=Spotify"))
        // A language this version doesn't speak, or none.
        assertNull(JollyActions.parse("type=say&phrase=hola&lang=xx"))
        assertNull(JollyActions.parse("type=say&phrase=hola"))
        // A broken escape spoils the line.
        assertNull(JollyActions.parse("type=navigate&place=50%"))
        assertNull(JollyActions.parse("type=navigate&place=5%G0"))
        // A sign is no hex digit, though a number reader would take it.
        assertNull(JollyActions.parse("type=navigate&place=a%+1"))
        assertNull(JollyActions.parse("type=navigate&place=a%-1"))
        assertEquals(JollyAction.Navigate("a&b"), JollyActions.parse("type=navigate&place=a%26b"))
        assertEquals(JollyAction.Navigate("a=b"), JollyActions.parse("type=navigate&place=a%3db"))
    }

    @Test
    fun unknownFieldsAreLeftAlone() {
        assertEquals(
            JollyAction.Call("Anna", "123"),
            JollyActions.parse("v=9&type=call&name=Anna&number=123&ring=loud&=x&novalue"),
        )
        // The first of a field written twice.
        assertEquals(JollyAction.Assistant("a.b"), JollyActions.parse("type=ai&app=a.b&app=c.d"))
    }

    @Test
    fun homeActionsFitTheirThing() {
        assertNull(JollyActions.parse("type=home&entity=script.gate&act=unlock&name=Gate"))
        assertNull(JollyActions.parse("type=home&entity=lock.door&act=toggle&name=Door"))
        assertNull(JollyActions.parse("type=home&entity=alarm_control_panel.house&act=run&name=Alarm"))
        assertNull(JollyActions.parse("type=home&entity=script.gate&act=explode&name=Gate"))
        assertNull(JollyActions.parse("type=home&entity=Script.Gate&act=run&name=Gate"))
        assertNull(JollyActions.parse("type=home&entity=script.gate;rm&act=run&name=Gate"))
        // No name: the entity's own id.
        assertEquals(JollyAction.Home("scene.night", HomeAct.RUN, "scene.night"), JollyActions.parse("type=home&entity=scene.night&act=run"))
    }

    @Test
    fun eachThingHasItsService() {
        assertEquals("script" to "turn_on", HomeActs.service("script.open_gate", HomeAct.RUN))
        assertEquals("scene" to "turn_on", HomeActs.service("scene.arrive_home", HomeAct.RUN))
        assertEquals("button" to "press", HomeActs.service("button.garage", HomeAct.PRESS))
        assertEquals("input_button" to "press", HomeActs.service("input_button.gate", HomeAct.PRESS))
        assertEquals("switch" to "toggle", HomeActs.service("switch.porch", HomeAct.TOGGLE))
        assertEquals("input_boolean" to "toggle", HomeActs.service("input_boolean.guests", HomeAct.TOGGLE))
        assertEquals("light" to "toggle", HomeActs.service("light.porch", HomeAct.TOGGLE))
        assertEquals("cover" to "toggle", HomeActs.service("cover.garage_door", HomeAct.TOGGLE))
        assertEquals("lock" to "lock", HomeActs.service("lock.front_door", HomeAct.LOCK))
        assertEquals("lock" to "unlock", HomeActs.service("lock.front_door", HomeAct.UNLOCK))
        assertNull(HomeActs.service("sensor.outside", HomeAct.RUN))
        assertNull(HomeActs.service("alarm_control_panel.house", HomeAct.UNLOCK))
        assertNull(HomeActs.service("light.porch", HomeAct.RUN))
        assertNull(HomeActs.service("no_dot", HomeAct.RUN))
    }

    @Test
    fun onlyUnlockingWaitsForASecondTap() {
        assertTrue(HomeActs.confirms(HomeAct.UNLOCK))
        HomeAct.entries.filter { it != HomeAct.UNLOCK }.forEach { assertFalse(it.name, HomeActs.confirms(it)) }
    }

    @Test
    fun homeAssistantsThingsAsTheListShowsThem() {
        val list = HomeActs.choices(
            listOf(
                "light.porch" to "Porch light",
                "sensor.outside" to "Outside",
                "lock.front_door" to "Front door",
                "script.open_gate" to "Open the gate",
                "scene.arrive" to "",
                "script.alarm_off" to "Alarm off",
                "weather.home" to "Home",
                "cover.garage" to "Garage",
                "script.open_gate" to "Twice",
                "Bad.Entity" to "Bad",
            ),
        )
        assertEquals(
            listOf(
                HomeActs.Choice("script.alarm_off", HomeAct.RUN, "Alarm off"),
                HomeActs.Choice("script.open_gate", HomeAct.RUN, "Open the gate"),
                HomeActs.Choice("scene.arrive", HomeAct.RUN, "scene.arrive"),
                HomeActs.Choice("light.porch", HomeAct.TOGGLE, "Porch light"),
                HomeActs.Choice("cover.garage", HomeAct.TOGGLE, "Garage"),
                HomeActs.Choice("lock.front_door", HomeAct.LOCK, "Front door"),
                HomeActs.Choice("lock.front_door", HomeAct.UNLOCK, "Front door"),
            ),
            list,
        )
    }

    @Test
    fun theSecondTapComesWithinThreeSecondsButNotAtOnce() {
        val tap = SecondTap(3_000, 700)
        assertFalse(tap.armed(1_000))
        assertEquals(SecondTap.Tap.ARMED, tap.tap(1_000))
        // A double tap, or a finger that bounced: let go, and the question stays.
        assertEquals(SecondTap.Tap.TOO_SOON, tap.tap(1_150))
        assertEquals(SecondTap.Tap.TOO_SOON, tap.tap(1_699))
        assertTrue(tap.armed(1_699))
        assertEquals(SecondTap.Tap.GO, tap.tap(1_700))
        // Gone ahead: the next tap arms again.
        assertFalse(tap.armed(1_800))
        assertEquals(SecondTap.Tap.ARMED, tap.tap(1_800))
        assertEquals(SecondTap.Tap.GO, tap.tap(4_800))
        // Too late: it arms afresh, and the one after goes.
        assertEquals(SecondTap.Tap.ARMED, tap.tap(5_000))
        assertFalse(tap.armed(8_001))
        assertEquals(SecondTap.Tap.ARMED, tap.tap(8_001))
        assertEquals(SecondTap.Tap.GO, tap.tap(9_000))
        assertEquals(SecondTap.Tap.ARMED, tap.tap(9_500))
        tap.disarm()
        assertFalse(tap.armed(9_600))
        assertEquals(SecondTap.Tap.ARMED, tap.tap(9_600))
    }

    @Test
    fun aHomeAssistantCommandDoesntRunTwiceAtOnce() {
        val guard = RepeatGuard(1_000)
        assertTrue(guard.start("cover.garage/toggle", 1_000))
        // Tapped again while it runs, and just after: the same press twice.
        assertFalse(guard.start("cover.garage/toggle", 1_200))
        assertTrue(guard.busy("cover.garage/toggle", 9_000))
        guard.end("cover.garage/toggle", 9_000)
        assertFalse(guard.start("cover.garage/toggle", 9_500))
        assertFalse(guard.start("cover.garage/toggle", 9_999))
        assertTrue(guard.start("cover.garage/toggle", 10_000))
        guard.end("cover.garage/toggle", 10_100)
        // Another command isn't held up by it.
        assertTrue(guard.start("script.gate/run", 10_200))
        assertFalse(guard.busy("cover.garage/toggle", 11_100))
    }

    @Test
    fun homeAssistantsWordOnAThingBeforeItsCommand() {
        assertEquals(HomeChecks.Before.MISSING, HomeChecks.before(null))
        assertEquals(HomeChecks.Before.UNAVAILABLE, HomeChecks.before("unavailable"))
        assertEquals(HomeChecks.Before.GO, HomeChecks.before("off"))
        assertEquals(HomeChecks.Before.GO, HomeChecks.before("locked"))
        // A scene or a button never used yet, a gate without a sensor: they still work.
        assertEquals(HomeChecks.Before.GO, HomeChecks.before("unknown"))
        assertEquals(HomeChecks.Before.GO, HomeChecks.before("2026-11-09T19:35:55+00:00"))
    }

    @Test
    fun aLockSaysWhetherItDidIt() {
        val open = HomeAct.UNLOCK
        val close = HomeAct.LOCK
        assertEquals(HomeChecks.After.DONE, HomeChecks.after(open, "unlocked"))
        assertEquals(HomeChecks.After.DONE, HomeChecks.after(open, "open"))
        assertEquals(HomeChecks.After.STILL_LOCKED, HomeChecks.after(open, "locked"))
        assertEquals(HomeChecks.After.NO_NEWS, HomeChecks.after(open, "unlocking"))
        assertEquals(HomeChecks.After.JAMMED, HomeChecks.after(open, "jammed"))
        assertEquals(HomeChecks.After.DONE, HomeChecks.after(close, "locked"))
        assertEquals(HomeChecks.After.STILL_UNLOCKED, HomeChecks.after(close, "unlocked"))
        assertEquals(HomeChecks.After.NO_NEWS, HomeChecks.after(close, "locking"))
        assertEquals(HomeChecks.After.JAMMED, HomeChecks.after(close, "jammed"))
        assertEquals(HomeChecks.After.NO_NEWS, HomeChecks.after(close, "unavailable"))
        assertEquals(HomeChecks.After.NO_NEWS, HomeChecks.after(close, null))
        // The wait ends when it did it or got stuck, not while it's on its way.
        assertTrue(HomeChecks.settled(open, "unlocked"))
        assertTrue(HomeChecks.settled(open, "jammed"))
        assertFalse(HomeChecks.settled(open, "locked"))
        assertFalse(HomeChecks.settled(open, "unlocking"))
        assertFalse(HomeChecks.settled(close, "unlocked"))
    }

    /**
     * The ready-made commands the car's chooser offers (strings_jolly.xml, in every language): each
     * one is a phrase myCar's assistant takes for the command it stands for.
     */
    @Test
    fun theReadyMadeCommandsAreUnderstoodInEveryLanguage() {
        val expected = mapOf(
            "jolly_say_messages" to "read my messages",
            "jolly_say_arrival" to "when do i arrive",
            "jolly_say_call_back" to "call back",
            "jolly_say_weather" to "what's the weather",
        )
        val res = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        for (language in VoiceLanguage.entries) {
            val folder = if (language == VoiceLanguage.EN) "values" else "values-${language.code}"
            val texts = strings(File(res, "$folder/strings_jolly.xml"))
            for ((key, command) in expected) {
                val text = texts[key] ?: error("$folder: no $key")
                assertEquals("$folder $key \"$text\"", command, plain(AssistantLanguage.toEnglish(text, language)))
            }
        }
    }

    /** As AssistantCommands reads a command: lower case, no punctuation. */
    private fun plain(text: String) = text.lowercase().replace(Regex("[.,!?¿¡;:]"), " ").replace(Regex("\\s+"), " ").trim()

    /** The strings of one resource file, unescaped as aapt does for these (an apostrophe after a backslash). */
    private fun strings(file: File): Map<String, String> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        val nodes = root.getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val e = nodes.item(i) as Element
            e.getAttribute("name") to e.textContent.trim().replace("\\'", "'").replace("\\\"", "\"")
        }
    }
}
