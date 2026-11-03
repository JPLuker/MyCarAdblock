package io.github.sirionero.mycar.car

import io.github.sirionero.mycar.speech.VoiceLanguage
import java.text.Normalizer

/**
 * What the driver's words mean to [VoiceReply]: a yes to "Do you want to reply?", a command while
 * the reply counts down. The words of the language the assistant works in count besides the
 * English ones. Nothing here needs Android (ReplyWordsTest).
 */
internal object ReplyWords {

    enum class Command { CANCEL, CHANGE, SEND }

    fun isYes(text: String, l: VoiceLanguage = AssistantLanguage.language): Boolean {
        if (says(text, NO_WORDS + AssistantLanguage.no(l))) return false
        // "Do not reply", "Nicht antworten", "Ne réponds pas": the yes word is there, and denied.
        if (denied(text, l, REPLY)) return false
        return says(text, YES_WORDS + AssistantLanguage.yes(l))
    }

    fun command(text: String, l: VoiceLanguage = AssistantLanguage.language): Command? = when {
        says(text, CANCEL_WORDS + AssistantLanguage.cancel(l)) -> Command.CANCEL
        // "Do not send it", "Nicht senden", "Non inviare", "Не шли", "不要发送": the verb alone made it a
        // SEND, or nothing, and the reply went. A "not" with no verb of sending is still nothing.
        denied(text, l, SEND) || deniedInChinese(text) -> Command.CANCEL
        says(text, CHANGE_WORDS + AssistantLanguage.change(l)) -> Command.CHANGE
        says(text, SEND_WORDS + AssistantLanguage.send(l)) -> Command.SEND
        else -> null
    }

    /** Whether [text] has one of [phrases] in it, as whole words ("va bene", "laisse tomber"). */
    private fun says(text: String, phrases: Set<String>): Boolean {
        val said = " " + words(text).joinToString(" ") + " "
        return phrases.any { said.contains(" $it ") }
    }

    /** Letters of any language: "sì", "ändern" and "réponds" are words too. "don’t" is "don't". */
    private fun words(text: String) =
        text.lowercase().replace('’', '\'').split(Regex("[^\\p{L}']+")).filter { it.isNotEmpty() }

    /**
     * Whether [text] denies one of [verbs]: a "not" of the language shortly before it, in the same
     * part of the phrase ("Do not send it", "Ne pas envoyer", "Не шли"), or, the way German and
     * spoken French say it, right after it at the end ("Schick es nicht ab", "Envoie-le pas"). Parts
     * are split at punctuation: in "Send it now, not later" the "not" is about something else.
     */
    private fun denied(text: String, l: VoiceLanguage, verbs: Map<VoiceLanguage, Verbs>): Boolean {
        val not = listOfNotNull(NOT[VoiceLanguage.EN], NOT[l])
        val verb = listOfNotNull(verbs[VoiceLanguage.EN], verbs[l])
        for (part in parts(text)) {
            val w = words(part).flatMap(::elided).map(::bare)
            for (j in w.indices) {
                if (verb.none { it.matches(w[j]) }) continue
                for (i in maxOf(0, j - NEAR) until j) if (not.any { w[i] in it.before }) return true
                for (i in j + 1..minOf(w.lastIndex, j + NEAR)) {
                    val n = not.firstOrNull { w[i] in it.after } ?: continue
                    if (w.drop(i + 1).all { it in n.tail }) return true
                }
            }
        }
        return false
    }

    /**
     * Chinese, written without spaces, is a run of characters to [words]: a "don't" and a "send" in
     * it ("不要发送这条消息", "先别发给他", "别给妈妈发消息"), not 发 in another word ("不会发生", "别发火").
     */
    private fun deniedInChinese(text: String): Boolean = parts(text).any { part ->
        CHINESE_DENIED.containsMatchIn(part.replace(Regex("(?<=\\p{IsHan})\\s+(?=\\p{IsHan})"), "").trim())
    }

    private fun parts(text: String) = text.split(Regex("[.,;:!?¿¡…，。！？；：、;]+"))

    /** "n'envoie" as "n'" and "envoie", "l'envoie" as "l'" and "envoie"; "don't" stays one word. */
    private fun elided(word: String): List<String> {
        val at = word.indexOf('\'')
        if (at != 1 && !(at == 2 && word.startsWith("qu"))) return listOf(word)
        return listOf(word.substring(0, at + 1), word.substring(at + 1)).filter { it.isNotEmpty() }
    }

    /** Without accents: "não" as "nao", "στείλεις" as "στειλεις", "envíes" as "envies". */
    private fun bare(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    /** A "not" before the verb, or after it with only [tail] words left. */
    private class Negation(before: Set<String>, after: Set<String> = emptySet(), tail: Set<String> = emptySet()) {
        val before = before.map(::bare).toSet()
        val after = after.map(::bare).toSet()
        val tail = tail.map(::bare).toSet()
    }

    /** A verb in its forms: by [stems] (not the words starting with [unless]) or [exact] words. */
    private class Verbs(stems: Set<String>, exact: Set<String> = emptySet(), unless: Set<String> = emptySet()) {
        private val stems = stems.map(::bare)
        private val exact = exact.map(::bare).toSet()
        private val unless = unless.map(::bare)
        fun matches(w: String) = w in exact || (stems.any { w.startsWith(it) } && unless.none { w.startsWith(it) })
    }

    /** How far a "not" may be from its verb: "I do not want to send it", "δεν θέλω να το στείλω". */
    private const val NEAR = 4

    // A word of "no" by itself ("no", "nein", "不") is in the cancel lists already.
    private val NOT = mapOf(
        VoiceLanguage.EN to Negation(setOf("not", "don't", "dont", "won't")),
        VoiceLanguage.IT to Negation(setOf("non")),
        VoiceLanguage.ES to Negation(setOf("no", "nunca")),
        VoiceLanguage.FR to Negation(setOf("ne", "n'", "n"), after = setOf("pas"), tail = setOf("encore", "maintenant")),
        VoiceLanguage.DE to Negation(
            setOf("nicht"),
            after = setOf("nicht", "nichts"),
            tail = setOf("ab", "los", "weg", "raus", "mehr", "bitte", "jetzt", "danke"),
        ),
        VoiceLanguage.PT to Negation(setOf("não", "nunca")),
        VoiceLanguage.EL to Negation(setOf("μη", "μην", "δεν", "δε")),
        VoiceLanguage.RU to Negation(setOf("не")),
    )

    private val SEND = mapOf(
        VoiceLanguage.EN to Verbs(setOf("send"), unless = setOf("sender")),
        VoiceLanguage.IT to Verbs(setOf("invia", "invii", "invio", "manda", "mandi", "mando", "spedi")),
        VoiceLanguage.ES to Verbs(setOf("envia", "envie", "envio", "manda", "mande", "mando")),
        VoiceLanguage.FR to Verbs(setOf("envo")),
        VoiceLanguage.DE to Verbs(
            setOf("send", "schick", "abschick", "verschick", "absend", "versend"),
            unless = setOf("sender", "sendung"),
        ),
        VoiceLanguage.PT to Verbs(setOf("envia", "envie", "envio", "manda", "mande", "mando")),
        VoiceLanguage.EL to Verbs(setOf("στειλ", "στελν", "αποστολ", "αποστειλ")),
        VoiceLanguage.RU to Verbs(setOf("отправ", "посыл"), exact = setOf("шли", "шлите")),
    )

    private val REPLY = mapOf(
        VoiceLanguage.EN to Verbs(setOf("reply", "answer")),
        VoiceLanguage.IT to Verbs(setOf("rispond")),
        VoiceLanguage.ES to Verbs(setOf("respond")),
        VoiceLanguage.FR to Verbs(setOf("repond")),
        VoiceLanguage.DE to Verbs(setOf("antwort")),
        VoiceLanguage.PT to Verbs(setOf("respond")),
        VoiceLanguage.EL to Verbs(setOf("απαντ")),
        VoiceLanguage.RU to Verbs(setOf("ответ", "отвеч")),
    )

    /** 不/别 (and 不要, 别再, 别给他…) right before 发 as sending: 发送, 发出, 发给, 发了, 发吧, or 发 at the end. */
    private val CHINESE_DENIED = Regex(
        "[不别別勿甭](?:要|用|想|必|再|先){0,2}(?:[给給][^发發]{1,4})?[发發](?:送|出|过|過|给|給|了|啦|吧|这|這|那|信|消息|短信|微信|$)",
    )

    private val YES_WORDS = setOf("yes", "yeah", "yep", "yup", "sure", "ok", "okay", "reply", "please")
    private val NO_WORDS = setOf("no", "nope", "nah", "cancel", "don't", "stop")
    private val CANCEL_WORDS = setOf("cancel", "stop", "don't", "no", "nope")
    private val CHANGE_WORDS = setOf("change", "redo", "again", "edit", "wrong", "fix")
    private val SEND_WORDS = setOf("send", "yes", "yeah", "ok", "okay", "go")
}
