package io.github.sirionero.mycar.car

import io.github.sirionero.mycar.speech.VoiceLanguage
import io.github.sirionero.mycar.speech.VoiceLanguage.DE
import io.github.sirionero.mycar.speech.VoiceLanguage.EL
import io.github.sirionero.mycar.speech.VoiceLanguage.ES
import io.github.sirionero.mycar.speech.VoiceLanguage.FR
import io.github.sirionero.mycar.speech.VoiceLanguage.IT
import io.github.sirionero.mycar.speech.VoiceLanguage.PT
import io.github.sirionero.mycar.speech.VoiceLanguage.RU
import io.github.sirionero.mycar.speech.VoiceLanguage.ZH
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phrases that a later rule would take for something else: the next song said with "go to" (a
 * place for the map), "hör auf" (a song called "auf"), a missed call "answered" (a reply to a
 * person called "missed call"). Next to them, what must stay as it was.
 */
class AssistantLookAlikeTest {

    private fun heard(l: VoiceLanguage, vararg pairs: Pair<String, String>) {
        for ((said, command) in pairs) assertEquals(said, command, AssistantLanguage.toEnglish(said, l))
    }

    @Test
    fun `the next or the previous song, said with go`() {
        heard(
            IT,
            "Vai alla prossima canzone." to "next",
            "Passa al brano successivo." to "next",
            "Salta alla prossima." to "next",
            "Metti la prossima canzone." to "next",
            "Vai avanti di una canzone." to "next",
            "Torna alla canzone precedente." to "previous",
            "Vai al brano precedente." to "previous",
            "Torna indietro di una canzone." to "previous",
            "Vai indietro." to "go back",
        )
        heard(
            ES,
            "Ve a la siguiente canción." to "next",
            "Pasa a la próxima canción." to "next",
            "Vamos a la canción siguiente." to "next",
            "Vuelve a la canción anterior." to "previous",
            "Ve a la anterior." to "previous",
        )
        heard(
            FR,
            "Va à la chanson suivante." to "next",
            "Passe au morceau suivant." to "next",
            "Va à la prochaine chanson." to "next",
            "Reviens à la chanson précédente." to "previous",
            "Retourne au morceau précédent." to "previous",
        )
        heard(
            DE,
            "Geh zum nächsten Lied." to "next",
            "Spring zum nächsten Titel." to "next",
            "Spiel das nächste Lied." to "next",
            "Geh zum vorherigen Lied." to "previous",
            "Zurück zum vorherigen Song." to "previous",
            "Geh ein Lied zurück." to "previous",
        )
        heard(
            PT,
            "Vai para a próxima música." to "next",
            "Passa pra próxima." to "next",
            "Toca a próxima faixa." to "next",
            "Volta para a música anterior." to "previous",
            "Vai para a anterior." to "previous",
        )
        heard(
            EL,
            "Πήγαινε στο επόμενο τραγούδι." to "next",
            "Γύρνα στο προηγούμενο τραγούδι." to "previous",
            "Γύρνα πίσω ένα τραγούδι." to "previous",
        )
        heard(
            RU,
            "Перейди к следующей песне." to "next",
            "Перейди на следующий трек." to "next",
            "Переключи на следующую песню." to "next",
            "Вернись к предыдущей песне." to "previous",
            "Перейди на предыдущий трек." to "previous",
        )
        heard(
            ZH,
            "去下一首。" to "next",
            "到下一首歌。" to "next",
            "我要听下一首。" to "next",
            "去上一首。" to "previous",
            "到上一首歌曲。" to "previous",
        )
    }

    @Test
    fun `places and apps said with go stay as they were`() {
        heard(
            IT,
            "Vai alla prossima uscita." to "go to prossima uscita",
            "Vai al distributore più vicino." to "go to the nearest distributore",
            "Vai a Roma." to "go to roma",
            "Vai su Spotify." to "open spotify",
            "Torna indietro." to "go back",
        )
        heard(ES, "Ve a la gasolinera más cercana." to "go to the nearest gasolinera", "Ve a la próxima salida." to "go to próxima salida")
        heard(FR, "Va à la prochaine sortie." to "go to prochaine sortie", "Va à Paris." to "go to paris")
        heard(DE, "Geh zur nächsten Tankstelle." to "go to the nearest tankstelle", "Geh nach Hause." to "go to zuhause")
        heard(PT, "Vai para o posto mais próximo." to "go to the nearest posto", "Vai para Lisboa." to "go to lisboa")
        heard(RU, "Перейди в настройки." to "open settings", "Перейди на Spotify." to "open spotify")
        heard(ZH, "去北京。" to "go to 北京", "去下一个路口。" to "go to 下一个路口")
    }

    @Test
    fun `hör auf pauses`() = heard(
        DE,
        "Hör auf!" to "pause",
        "Hör auf damit." to "pause",
        "Höre auf." to "pause",
        "Hör jetzt auf." to "pause",
        "Aufhören." to "pause",
        "Hör auf mit der Musik." to "pause",
        "Hör Queen." to "play queen",
        "Spiel Herbert Grönemeyer" to "play herbert grönemeyer",
    )

    @Test
    fun `a missed call answered is a call back, a person answered a reply`() {
        heard(
            IT,
            "Rispondi alla chiamata persa." to "call back",
            "Rispondi all'ultima chiamata persa." to "call back",
            "Rispondi alla mamma." to "reply to mamma",
            "Rispondi a Giulia." to "reply to giulia",
        )
        heard(
            ES,
            "Responde a la llamada perdida." to "call back",
            "Contesta la llamada perdida." to "call back",
            "Responde a Carlos." to "reply to carlos",
        )
        heard(
            FR,
            "Réponds à l'appel manqué." to "call back",
            "Réponds au dernier appel manqué." to "call back",
            "Réponds à Marie." to "reply to marie",
        )
        heard(
            DE,
            "Antworte auf den verpassten Anruf." to "call back",
            "Beantworte den verpassten Anruf." to "call back",
            "Auf den verpassten Anruf antworten." to "call back",
            "Antworte Anna." to "reply to anna",
        )
        heard(
            PT,
            "Responda à chamada perdida." to "call back",
            "Responder a ligação perdida." to "call back",
            "Responda ao João." to "reply to joão",
        )
        heard(
            EL,
            "Απάντησε στην αναπάντητη κλήση." to "call back",
            "Απάντησε στη Μαρία." to "reply to μαρία",
        )
        heard(
            ZH,
            "回复未接来电。" to "call back",
            "回复刚才的未接来电。" to "call back",
            "回复张伟。" to "reply to 张伟",
        )
    }
}
