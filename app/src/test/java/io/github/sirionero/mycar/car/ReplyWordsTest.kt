package io.github.sirionero.mycar.car

import io.github.sirionero.mycar.car.ReplyWords.Command.CANCEL
import io.github.sirionero.mycar.car.ReplyWords.Command.CHANGE
import io.github.sirionero.mycar.car.ReplyWords.Command.SEND
import io.github.sirionero.mycar.speech.VoiceLanguage
import io.github.sirionero.mycar.speech.VoiceLanguage.DE
import io.github.sirionero.mycar.speech.VoiceLanguage.EL
import io.github.sirionero.mycar.speech.VoiceLanguage.EN
import io.github.sirionero.mycar.speech.VoiceLanguage.ES
import io.github.sirionero.mycar.speech.VoiceLanguage.FR
import io.github.sirionero.mycar.speech.VoiceLanguage.IT
import io.github.sirionero.mycar.speech.VoiceLanguage.PT
import io.github.sirionero.mycar.speech.VoiceLanguage.RU
import io.github.sirionero.mycar.speech.VoiceLanguage.ZH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What VoiceReply hears: the countdown's commands, a yes to "Do you want to reply?", a reply that only cancels. */
class ReplyWordsTest {

    private fun heard(l: VoiceLanguage, vararg pairs: Pair<String, ReplyWords.Command?>) {
        for ((said, command) in pairs) assertEquals("$l: $said", command, ReplyWords.command(said, l))
    }

    @Test
    fun `a do not send in the countdown cancels`() {
        heard(EN, "Do not send it." to CANCEL, "Don't send it" to CANCEL, "Don’t send it" to CANCEL, "I do not want to send it." to CANCEL)
        heard(DE, "Nicht senden." to CANCEL, "Schick es nicht ab." to CANCEL, "Sende es nicht." to CANCEL, "Bitte nicht abschicken" to CANCEL)
        heard(FR, "Ne pas envoyer." to CANCEL, "N'envoie pas." to CANCEL, "Ne l'envoie pas." to CANCEL, "Envoie-le pas." to CANCEL)
        heard(IT, "Non inviare." to CANCEL, "Non mandarlo." to CANCEL, "Non lo mandare" to CANCEL, "Non voglio inviarlo." to CANCEL)
        heard(RU, "Не шли." to CANCEL, "Не посылай." to CANCEL, "Не нужно отправлять." to CANCEL, "Не отправляй." to CANCEL)
        heard(EL, "Δεν θέλω να το στείλω." to CANCEL, "Δεν το στέλνω" to CANCEL, "δεν το στελνω" to CANCEL, "Μην το στείλεις." to CANCEL)
        heard(ES, "No lo envíes." to CANCEL, "Nunca lo mandes." to CANCEL)
        heard(PT, "Não envie." to CANCEL, "Nunca mande." to CANCEL)
        heard(ZH, "不要发送这条消息。" to CANCEL, "先别发给他" to CANCEL, "别给妈妈发消息" to CANCEL, "我不想发了" to CANCEL, "別發" to CANCEL)
    }

    @Test
    fun `sending without a not still sends`() {
        heard(EN, "Send it." to SEND, "Send it now, not later." to SEND, "Not bad. Send it." to SEND, "OK" to SEND)
        heard(DE, "Senden." to SEND, "Schick es ab." to SEND, "Sende es jetzt, nicht später." to SEND, "Sende es jetzt nicht später" to SEND)
        heard(FR, "Envoie." to SEND, "Envoie, pas de problème." to SEND, "Envoyer pas de souci" to SEND)
        heard(IT, "Invia." to SEND, "Non c'è problema, invia." to SEND, "Manda" to SEND)
        heard(RU, "Отправь." to SEND, "Да" to SEND)
        heard(EL, "Στείλε το." to SEND)
        heard(ES, "Envía." to SEND)
        heard(PT, "Envia." to SEND)
        heard(ZH, "发送吧" to SEND)
    }

    @Test
    fun `a not with no verb of sending is still nothing`() {
        heard(EN, "Not now." to null, "I'm not sure about the road" to null)
        heard(DE, "Nicht." to null, "Die Sendung nicht verpassen" to null, "Der Sender ist nicht gut" to null)
        heard(FR, "Je ne sais pas." to null)
        heard(IT, "Non." to null, "Non lo so." to null)
        heard(RU, "Не знаю." to null)
        heard(EL, "Δεν ξέρω." to null)
        heard(ZH, "不会发生的" to null, "别发火" to null)
    }

    @Test
    fun `cancel and change as before`() {
        heard(EN, "Cancel" to CANCEL, "Change it" to CHANGE, "Don't change it" to CANCEL)
        heard(IT, "Annulla" to CANCEL, "Cambia" to CHANGE)
        heard(DE, "Abbrechen" to CANCEL, "Ändern" to CHANGE)
        heard(ZH, "取消" to CANCEL, "修改" to CHANGE)
    }

    @Test
    fun `a reply of only a cancel is not sent`() {
        for ((l, said) in listOf(
            EN to "Cancel", EN to "Stop.", EN to "Cancel, cancel.", IT to "Annulla", IT to "Annulla.", IT to "annulla annulla",
            ES to "Cancela", FR to "Annule.", DE to "Abbrechen", PT to "Cancela", EL to "Ακύρωση", EL to "ακυρωση", EL to "Άσ' το",
            RU to "Отмена", RU to "Не отправляй", ZH to "取消", ZH to "取消。",
        )) assertTrue("$l: $said", ReplyWords.onlyCancel(said, l))
    }

    @Test
    fun `a no or a reply with more in it is a reply`() {
        for ((l, said) in listOf(
            EN to "No", EN to "Nope.", EN to "Don't", EN to "Cancel the dinner", EN to "", IT to "No", IT to "No.",
            IT to "Annulla la cena", ES to "No", FR to "Non", DE to "Nein", PT to "Não", EL to "Όχι", RU to "Нет", RU to "Не надо",
            ZH to "不", ZH to "不要", ZH to "取消晚饭吧",
        )) assertFalse("$l: $said", ReplyWords.onlyCancel(said, l))
    }

    @Test
    fun `a reply denied is no yes`() {
        assertFalse(ReplyWords.isYes("Do not reply.", EN))
        assertFalse(ReplyWords.isYes("Nicht antworten.", DE))
        assertFalse(ReplyWords.isYes("Ne réponds pas.", FR))
        assertFalse(ReplyWords.isYes("Non rispondi.", IT))
        assertFalse(ReplyWords.isYes("No.", EN))
        assertTrue(ReplyWords.isYes("Yes.", EN))
        assertTrue(ReplyWords.isYes("Reply.", EN))
        assertTrue(ReplyWords.isYes("Ja, antworten.", DE))
        assertTrue(ReplyWords.isYes("Oui, pas de souci.", FR))
        assertTrue(ReplyWords.isYes("Sì, rispondi.", IT))
        assertTrue(ReplyWords.isYes("Да, ответь.", RU))
    }
}
