package io.github.sirionero.mycar.audio

import io.github.sirionero.mycar.Protobuf
import io.github.sirionero.mycar.ServiceDiscovery
import java.util.Collections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How the media sink follows the car's audio focus. A car that takes the speakers for a call, or
 * for the phone's assistant over Bluetooth, gives them back with GAIN; myCar used to let go of its
 * focus there, and the music never came back for the rest of the drive. The car's radio, once the
 * driver picks it or keeps it until the phone plays, is left alone by anything but a player.
 */
class AudioLinkFocusTest {
    /** What myCar sent, in order: "start", "stop" and "data" on the sink, "focus:<type>" to the car. */
    private val sent: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private var takenBack = 0
    /** A player playing on the phone, as CarServices would say. */
    private var playing = true
    /** The links' clock, in milliseconds: moved on by the capture as it plays, and by hand. */
    @Volatile private var now = 0L

    private fun link(): AudioLink = AudioLink(
        ServiceDiscovery.AudioService(
            5, ServiceDiscovery.STREAM_MEDIA, ServiceDiscovery.CODEC_PCM,
            listOf(ServiceDiscovery.AudioConfig(48_000, 16, 2)),
        ),
        sendOnChannel = { id, _ -> sent += SINK_MESSAGES[id] ?: "message $id" },
        sendControl = { _, body -> sent += "focus:${Protobuf.readInt32Field(body, 1)}" },
        onReady = {},
        onClose = {},
        onTakenBack = { takenBack++ },
        playerPlaying = { playing },
        clock = { now },
    ).apply {
        onMessage(CHANNEL_OPEN_RESPONSE, Protobuf.varintField(1, 0))
        onMessage(CONFIG, Protobuf.varintField(1, READY) + Protobuf.varintField(2, 4) + Protobuf.varintField(3, 0))
        sent.clear()
    }

    /** The directions' sink, which asks for the speakers only while [media] has none of them. */
    private fun guidance(media: AudioLink): AudioLink = AudioLink(
        ServiceDiscovery.AudioService(
            6, ServiceDiscovery.STREAM_GUIDANCE, ServiceDiscovery.CODEC_PCM,
            listOf(ServiceDiscovery.AudioConfig(16_000, 16, 1)),
        ),
        sendOnChannel = { id, _ -> sent += "guidance " + (SINK_MESSAGES[id] ?: "message $id") },
        sendControl = { _, body -> sent += "focus:${Protobuf.readInt32Field(body, 1)}" },
        tag = "guidance",
        sessionId = 3,
        asksFocus = false,
        onReady = {},
        onClose = {},
        musicHasSpeakers = { media.speakersAsked },
        clock = { now },
    ).apply {
        onMessage(CHANNEL_OPEN_RESPONSE, Protobuf.varintField(1, 0))
        onMessage(CONFIG, Protobuf.varintField(1, READY) + Protobuf.varintField(2, 4) + Protobuf.varintField(3, 0))
        sent.clear()
    }

    /** The link streaming, the car having granted it the speakers. */
    private fun streaming(): AudioLink = link().apply {
        start()
        onFocus(GAIN)
        sent.clear()
    }

    /** [ms] of capture: silence, or a tone well above a whisper. */
    private fun AudioLink.play(ms: Int, loud: Boolean) {
        val chunk = ByteArray(CHUNK)
        if (loud) for (i in chunk.indices step 2) chunk[i + 1] = 0x20
        repeat(ms * BYTES_PER_MS / CHUNK + 1) {
            write(chunk, chunk.size)
            now += CHUNK / BYTES_PER_MS
        }
    }

    /** The directions asking for the speakers on their own thread, as they do, until [answered] says the car replied. */
    private fun asking(guidance: AudioLink, answered: () -> Unit): Long {
        var asked = false
        val began = System.nanoTime()
        val thread = Thread { asked = guidance.askForSpeakers() }.apply { start() }
        while (sent.none { it == "focus:$MAY_DUCK" }) Thread.sleep(1)
        answered()
        thread.join(2_000)
        assertTrue(asked)
        return (System.nanoTime() - began) / 1_000_000
    }

    @Test
    fun `a call holds the music and the car's GAIN brings it back`() {
        val link = streaming()
        link.onFocus(LOSS_TRANSIENT)
        assertEquals(listOf("stop"), sent)
        link.onFocus(GAIN)
        assertEquals(listOf("stop", "start"), sent)
        assertEquals(0, takenBack)
    }

    @Test
    fun `a car that never gives the speakers back is asked again while the phone plays on`() {
        val link = streaming()
        link.onFocus(LOSS_TRANSIENT)
        sent.clear()
        link.play(19_000, loud = true)
        assertEquals(emptyList<String>(), sent)
        link.play(2_000, loud = true)
        assertEquals(listOf("focus:1"), sent)
        link.onFocus(GAIN)
        assertEquals(listOf("focus:1", "start"), sent)
    }

    @Test
    fun `held back with the phone quiet, as in a call, nothing is asked`() {
        val link = streaming()
        link.onFocus(LOSS_TRANSIENT)
        sent.clear()
        link.play(30_000, loud = false)
        assertEquals(emptyList<String>(), sent)
    }

    @Test
    fun `ducking keeps the music playing`() {
        val link = streaming()
        link.onFocus(LOSS_TRANSIENT_CAN_DUCK)
        link.play(10, loud = true)
        assertEquals(listOf("data"), sent)
    }

    @Test
    fun `held back, a restarted capture waits for the car`() {
        val link = streaming()
        link.onFocus(LOSS_TRANSIENT)
        sent.clear()
        link.start()
        assertEquals(emptyList<String>(), sent)
    }

    @Test
    fun `the capture ending while held back gives the focus back`() {
        val link = streaming()
        link.onFocus(LOSS_TRANSIENT)
        sent.clear()
        link.stop()
        assertEquals(listOf("focus:$RELEASE"), sent)
    }

    @Test
    fun `the radio lets go and pauses the phone, and playing again asks the car back`() {
        val link = streaming()
        link.onFocus(LOSS)
        assertEquals(listOf("stop", "focus:$RELEASE"), sent)
        assertEquals(1, takenBack)
        sent.clear()
        link.play(1_600, loud = false)
        link.play(100, loud = true)
        assertEquals(emptyList<String>(), sent)
        link.play(300, loud = true)
        assertEquals(listOf("focus:$GAIN_REQUEST"), sent)
        link.onFocus(GAIN)
        assertEquals(listOf("focus:$GAIN_REQUEST", "start"), sent)
    }

    @Test
    fun `music that never paused doesn't pull the car back from the radio`() {
        val link = streaming()
        link.onFocus(LOSS)
        sent.clear()
        link.play(3_000, loud = true)
        assertEquals(emptyList<String>(), sent)
    }

    @Test
    fun `the capture starting takes the speakers at once, unless the radio is kept`() {
        val link = link()
        link.captureOn(radioFirst = false)
        assertEquals(listOf("focus:$GAIN_REQUEST", "start"), sent)
        sent.clear()
        val kept = link()
        kept.captureOn(radioFirst = true)
        kept.play(5_000, loud = false)
        assertEquals(emptyList<String>(), sent)
        assertFalse(kept.speakersAsked)
    }

    @Test
    fun `with the radio kept, a player's first sound takes the speakers with the moment before it`() {
        val link = link()
        link.captureOn(radioFirst = true)
        link.play(2_000, loud = false)
        link.play(200, loud = true)
        assertEquals(emptyList<String>(), sent)
        link.play(100, loud = true)
        // Asked and started at once, then the 300 ms heard before (57,600 bytes, in 4096-byte pieces).
        assertEquals(listOf("focus:$GAIN_REQUEST", "start"), sent.take(2))
        assertEquals(List(15) { "data" }, sent.drop(2))
        sent.clear()
        link.play(10, loud = true)
        assertEquals(listOf("data"), sent)
    }

    @Test
    fun `with the radio kept, sound that isn't a player's - a map's voice, a page - takes nothing`() {
        val link = link()
        link.captureOn(radioFirst = true)
        playing = false
        link.play(5_000, loud = true)
        assertEquals(emptyList<String>(), sent)
        playing = true
        link.play(100, loud = true)
        assertEquals(listOf("focus:$GAIN_REQUEST", "start"), sent.take(2))
    }

    @Test
    fun `after the radio, sound that isn't a player's doesn't take the speakers back`() {
        val link = streaming()
        link.onFocus(LOSS)
        sent.clear()
        playing = false
        link.play(1_600, loud = false)
        link.play(3_000, loud = true)
        assertEquals(emptyList<String>(), sent)
        // A player starting under it: then it asks.
        playing = true
        link.play(100, loud = true)
        assertEquals(listOf("focus:$GAIN_REQUEST"), sent)
    }

    @Test
    fun `a capture started again after the radio doesn't take the speakers back`() {
        val link = streaming()
        link.onFocus(LOSS)
        sent.clear()
        // The audio server restarted, screen sharing ended, "Ask again": stopped, then on again.
        link.stop()
        link.captureOn(radioFirst = false)
        assertEquals(emptyList<String>(), sent)
        // Still, a player playing after a pause takes them back, asking first.
        link.play(1_600, loud = false)
        link.play(400, loud = true)
        assertEquals(listOf("focus:$GAIN_REQUEST"), sent)
    }

    @Test
    fun `without the radio picked, a capture started again takes the speakers as before`() {
        val link = streaming()
        link.stop()
        assertEquals(listOf("stop", "focus:$RELEASE"), sent)
        sent.clear()
        link.captureOn(radioFirst = false)
        assertEquals(listOf("focus:$GAIN_REQUEST", "start"), sent)
    }

    @Test
    fun `a GAIN the music never asked for starts nothing`() {
        val link = link()
        link.onFocus(GAIN)
        link.onFocus(GAIN_TRANSIENT)
        assertEquals(emptyList<String>(), sent)
        link.captureOn(radioFirst = true)
        link.onFocus(GAIN)
        assertEquals(emptyList<String>(), sent)
        // Asked for, it starts the stream as before.
        link.start()
        assertEquals(listOf("focus:$GAIN_REQUEST", "start"), sent)
    }

    @Test
    fun `the car's answers inside the directions' window go to the directions`() {
        val media = link()
        val guidance = guidance(media)
        val took = asking(guidance) { FocusAnswers.route(GAIN_TRANSIENT, media, guidance) }
        assertTrue("the directions waited ${took}ms for an answer they had", took < 400)
        guidance.handBackSpeakers()
        FocusAnswers.route(LOSS, media, guidance)
        assertEquals(listOf("focus:$MAY_DUCK", "focus:$RELEASE"), sent)
        assertFalse(media.speakersAsked)
        assertEquals(0, takenBack)
    }

    @Test
    fun `the directions' late answer starts no music`() {
        val media = link()
        val guidance = guidance(media)
        val began = System.nanoTime()
        assertTrue(guidance.askForSpeakers())
        assertTrue((System.nanoTime() - began) / 1_000_000 >= 450)
        now += 600
        // GAIN_TRANSIENT after the half second: to the music, which never asked.
        FocusAnswers.route(GAIN_TRANSIENT, media, guidance)
        assertEquals(listOf("focus:$MAY_DUCK"), sent)
    }

    @Test
    fun `a GAIN in the directions' window is the music's when it waits for one`() {
        val media = streaming()
        media.onFocus(LOSS)
        media.play(1_600, loud = false)
        val guidance = guidance(media)
        val thread = Thread { guidance.askForSpeakers() }.apply { start() }
        while (sent.none { it == "focus:$MAY_DUCK" }) Thread.sleep(1)
        // A player after the pause: the music asks for the speakers and waits for the car's word.
        media.play(400, loud = true)
        FocusAnswers.route(GAIN, media, guidance)
        assertTrue(sent.contains("start"))
        // The directions' own answer is still theirs.
        assertTrue(thread.isAlive)
        FocusAnswers.route(GAIN_TRANSIENT, media, guidance)
        thread.join(1_000)
        assertFalse(thread.isAlive)
    }

    @Test
    fun `a late LOSS after the directions' RELEASE doesn't stop the music started since`() {
        val media = link()
        val guidance = guidance(media)
        asking(guidance) { FocusAnswers.route(GAIN_TRANSIENT, media, guidance) }
        now = 1_000
        guidance.handBackSpeakers()
        // Past the directions' half second, the music starts: the car's LOSS for the RELEASE comes late.
        now = 1_600
        media.start()
        sent.clear()
        now = 1_700
        FocusAnswers.route(LOSS, media, guidance)
        assertEquals(emptyList<String>(), sent)
        assertTrue(media.streaming)
        // Later, a LOSS is the driver's: the radio.
        now = 3_000
        media.onFocus(GAIN)
        FocusAnswers.route(LOSS, media, guidance)
        assertEquals(listOf("stop", "focus:$RELEASE"), sent)
        assertEquals(1, takenBack)
    }

    @Test
    fun `the directions ask nothing while the music has the speakers`() {
        val media = streaming()
        val guidance = guidance(media)
        assertFalse(guidance.askForSpeakers())
        guidance.handBackSpeakers()
        assertEquals(emptyList<String>(), sent)
    }

    @Test
    fun `the directions don't give back the speakers the music asked for meanwhile`() {
        val media = link()
        val guidance = guidance(media)
        asking(guidance) { FocusAnswers.route(GAIN_TRANSIENT, media, guidance) }
        media.start()
        guidance.handBackSpeakers()
        assertEquals(listOf("focus:$MAY_DUCK", "focus:$GAIN_REQUEST", "start"), sent)
    }

    private companion object {
        const val CHANNEL_OPEN_RESPONSE = 8
        const val CONFIG = 32771
        const val READY = 2L
        val SINK_MESSAGES = mapOf(0 to "data", 32768 to "setup", 32769 to "start", 32770 to "stop")

        // AudioFocusStateNotification states, and the AudioFocusRequest types myCar sends.
        const val GAIN = 1
        const val GAIN_TRANSIENT = 2
        const val LOSS = 3
        const val LOSS_TRANSIENT_CAN_DUCK = 4
        const val LOSS_TRANSIENT = 5
        const val GAIN_REQUEST = 1
        const val MAY_DUCK = 3
        const val RELEASE = 4

        const val CHUNK = 4096
        const val BYTES_PER_MS = 48 * 2 * 2
    }
}
