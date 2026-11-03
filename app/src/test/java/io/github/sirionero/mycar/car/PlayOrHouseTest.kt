package io.github.sirionero.mycar.car

import io.github.sirionero.mycar.speech.VoiceLanguage
import io.github.sirionero.mycar.speech.VoiceLanguage.DE
import io.github.sirionero.mycar.speech.VoiceLanguage.EN
import io.github.sirionero.mycar.speech.VoiceLanguage.ES
import io.github.sirionero.mycar.speech.VoiceLanguage.FR
import io.github.sirionero.mycar.speech.VoiceLanguage.IT
import io.github.sirionero.mycar.speech.VoiceLanguage.PT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * "Metti", "pon", "mets", "põe" and "put on" become "play" in the phrasebooks: what follows is a song,
 * the music's pause, or the house for Home Assistant.
 */
class PlayOrHouseTest {

    /** [said] as AssistantCommands has it: the phrasebook's English, lower case, without punctuation. */
    private fun heard(said: String, l: VoiceLanguage) =
        AssistantLanguage.toEnglish(said, l).lowercase().replace(Regex("[.,!?]"), " ").replace(Regex("\\s+"), " ").trim()

    /** What AssistantCommands' playSearchCommand gets after "play": the phrasebook, lower case, the trigger gone. */
    private fun afterPlay(said: String, l: VoiceLanguage): String? {
        val heard = heard(said, l)
        val trigger = listOf("play me ", "play ", "put on ", "listen to ", "i want to hear ", "shuffle ").firstOrNull { heard.startsWith(it) }
            ?: return null
        return heard.removePrefix(trigger).trim().removePrefix("some ").trim()
    }

    private fun check(expected: PlayAsked, l: VoiceLanguage, vararg said: String, app: String? = null) {
        for (s in said) {
            val query = afterPlay(s, l)
            assertNotNull("$s: no \"play\" (${AssistantLanguage.toEnglish(s, l)})", query)
            assertEquals("$s -> play $query", expected, playAsked(query!!, app))
            // Past "play X", the house is no button of the music's either: on to Home Assistant.
            if (expected == PlayAsked.HOUSE) assertFalse(s, musicCommandOnly(heard(s, l), app))
        }
    }

    @Test fun theHouseInItalian() = check(
        PlayAsked.HOUSE, IT,
        "Metti il riscaldamento a 21 gradi.", "Metti il riscaldamento.", "Metti l'aria condizionata.", "Metti l'aria a 22 gradi.",
        "Metti il clima a 20.", "Metti il condizionatore a 24 gradi", "Metti il termostato a 19 gradi", "Metti la sveglia alle 7.",
        "Metti la sveglia.", "Metti un timer di 10 minuti.", "Metti la TV.", "Metti la tv su Rai 1.", "Metti le luci.",
        "Metti la luce al 50%.", "Metti le tapparelle a metà.", "Metti in pausa la TV.", "Metti la lavatrice.",
        "Metti l'antifurto.", "Metti il soggiorno a 21 gradi.", "Metti il cancello.", "Metti il garage.",
    )

    @Test fun theHouseInSpanish() = check(
        PlayAsked.HOUSE, ES,
        "Pon la calefacción a 21 grados.", "Pon la calefacción.", "Pon el aire acondicionado.", "Pon el aire a 22 grados.",
        "Pon la alarma a las 7.", "Pon el despertador.", "Pon la tele.", "Pon las luces.", "Pon en pausa la tele.", "Pon la lavadora.",
    )

    @Test fun theHouseInFrench() = check(
        PlayAsked.HOUSE, FR,
        "Mets le chauffage à 21 degrés.", "Mets le chauffage.", "Mets la clim.", "Mets la climatisation à 22 degrés.",
        "Mets la télé.", "Mets le réveil à 7 heures.", "Mets un minuteur de 5 minutes.", "Mets en pause la télé.", "Mets les volets.",
    )

    @Test fun theHouseInPortuguese() = check(
        PlayAsked.HOUSE, PT,
        "Coloca o aquecimento.", "Põe o aquecimento a 21 graus.", "Põe o ar condicionado a 22 graus.", "Coloca o ar a 22 graus.",
        "Põe a TV.", "Põe a TV na Globo.", "Põe o despertador.", "Põe em pausa a TV.",
    )

    @Test fun theHouseInEnglish() = check(
        PlayAsked.HOUSE, EN,
        "Put on the heating.", "Put on the air conditioning.", "Put on the AC.", "Put on the TV.", "Put on the lights.",
        "Put on the kettle.", "Put on the living room heating.", "Put on the fan.",
    )

    @Test fun theHouseInGermanIsNoPlayAnyway() {
        for (s in listOf("Mach die Heizung an.", "Stell die Heizung auf 21 Grad.", "Schalte das Licht ein.")) {
            assertNull(s, afterPlay(s, DE))
        }
    }

    @Test fun songsStaySongs() {
        check(PlayAsked.MUSIC, IT, "Metti vasco rossi.", "Metti i Pink Floyd.", "Metti Luce di Elisa.", "Metti la luce dell'est.",
            "Metti la radio.", "Metti Radio Deejay.", "Metti qualcosa di rilassante.", "Metti l'album The Wall.", "Metti Aria.",
            "Metti Pausa di Tiziano Ferro.")
        check(PlayAsked.MUSIC, ES, "Pon Shakira.", "Pon la radio.", "Pon 40 grados.", "Pon algo de reggaeton.")
        check(PlayAsked.MUSIC, FR, "Mets Stromae.", "Mets la radio.")
        check(PlayAsked.MUSIC, PT, "Põe Anitta.", "Coloca a rádio.")
        check(PlayAsked.MUSIC, EN, "Play some jazz.", "Play Pink Floyd.", "Play Lights by Ellie Goulding.", "Play TV by Billie Eilish.",
            "Play Alarm by Anne-Marie.", "Play 100 Degrees by Kylie Minogue.", "Play Pause by Pitbull.", "Put on the radio.",
            "Play AC/DC.", "Play the light of the world.")
        check(PlayAsked.MUSIC, DE, "Spiel Rammstein.")
    }

    @Test fun theMusicsPause() {
        check(PlayAsked.PAUSE, IT, "Metti in pausa la musica.", "Metti in pausa questa canzone.", "Metti pausa.")
        check(PlayAsked.PAUSE, IT, "Metti in pausa Spotify.", app = "Spotify · Web")
        check(PlayAsked.PAUSE, ES, "Pon en pausa.", "Pon en pausa la música.", "Pon pausa a la música.")
        check(PlayAsked.PAUSE, FR, "Mets en pause la musique.")
        check(PlayAsked.PAUSE, PT, "Põe em pausa.", "Coloca em pausa a música.")
        check(PlayAsked.PAUSE, EN, "Put on pause.")
        // Another app's name is not the music playing: the house's player, for Home Assistant.
        assertEquals(PlayAsked.HOUSE, playAsked("in pausa spotify", "Metrolist"))
    }

    @Test fun plainQueries() {
        assertEquals(PlayAsked.MUSIC, playAsked(""))
        assertEquals(PlayAsked.HOUSE, playAsked("il riscaldamento a 21 gradi"))
        assertEquals(PlayAsked.HOUSE, playAsked("la calefacción a 21 grados"))
        assertEquals(PlayAsked.HOUSE, playAsked("le chauffage à 21 degrés"))
        assertEquals(PlayAsked.HOUSE, playAsked("o ar condicionado a 22 graus"))
        assertEquals(PlayAsked.HOUSE, playAsked("the heating"))
        assertEquals(PlayAsked.HOUSE, playAsked("in pausa la tv"))
        assertEquals(PlayAsked.HOUSE, playAsked("a 21,5°".replace(",", " ")))
        assertEquals(PlayAsked.MUSIC, playAsked("vasco rossi"))
        assertEquals(PlayAsked.MUSIC, playAsked("jazz"))
    }
}
