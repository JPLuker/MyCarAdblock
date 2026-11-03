package io.github.sirionero.mycar.car

/** What a "play X" is really about: see [playAsked]. */
internal enum class PlayAsked { MUSIC, PAUSE, HOUSE }

/**
 * "Metti", "pon", "mets", "põe", "coloca" and "put on" are "play" for a song and "set" or "turn on"
 * for the house, and the phrasebooks make them all "play" (AssistantLanguage). This says which of
 * the two [query] is - what follows that "play", as AssistantCommands has it: lower case, the
 * fillers gone - as Russian's NOT_MUSIC does for "включи свет".
 *
 * [PlayAsked.HOUSE]: "metti il riscaldamento a 21 gradi", "pon la calefacción", "mets le chauffage",
 * "coloca o aquecimento", "put on the heating", "metti la sveglia alle 7", "metti in pausa la TV".
 * They were searched for in the music app; now they go on, to Home Assistant (AssistantBrain), with
 * the words as said. [PlayAsked.PAUSE]: "metti in pausa la musica", "pon en pausa", the music's own
 * pause. A song stays a song: "metti i Pink Floyd", "play some jazz", and the titles that share a
 * word with the house ("Luce" by Elisa, "Lights", "TV", "Alarm") without an article in front, or
 * with something after it that isn't a setting ("la luce dell'est").
 */
internal fun playAsked(query: String, appName: String? = null): PlayAsked {
    val words = words(query)
    if (words.isEmpty()) return PlayAsked.MUSIC
    val inPause = words[0] in PAUSE_IN && words.getOrNull(1) in PAUSE_WORDS
    if (inPause || words[0] in PAUSE_WORDS) {
        val name = words(appName.orEmpty()).toSet()
        val rest = words.drop(if (inPause) 2 else 1)
        if (rest.all { it in PAUSED_MUSIC || it in name }) return PlayAsked.PAUSE
        // "In pausa la TV"; "pause" by itself may be a song's name ("play Pause by…").
        if (inPause) return PlayAsked.HOUSE
    }
    if (TEMPERATURE.containsMatchIn(words.joinToString(" "))) return PlayAsked.HOUSE
    // A device anywhere: "the kitchen heating", "il riscaldamento in salotto".
    if (DEVICES.any { words.has(it) }) return PlayAsked.HOUSE
    // A word songs share with the house: only right after an article, then nothing or a setting.
    if (words[0] !in ARTICLES) return PlayAsked.MUSIC
    val thing = words.drop(1)
    val shared = SHARED.firstOrNull { thing.startsWith(it) } ?: return PlayAsked.MUSIC
    val after = thing.getOrNull(shared.size)
    return if (after == null || after in SETTINGS) PlayAsked.HOUSE else PlayAsked.MUSIC
}

/** Lower case, split at spaces, apostrophes ("l'aria") and hyphens ("lave-linge", "air-conditioning"). */
private fun words(text: String): List<String> =
    text.lowercase().replace('’', '\'').split(Regex("[\\s'-]+")).filter { it.isNotEmpty() }

private fun List<String>.startsWith(phrase: List<String>) = size >= phrase.size && subList(0, phrase.size) == phrase

private fun List<String>.has(phrase: List<String>) = size >= phrase.size && windowed(phrase.size).any { it == phrase }

private fun phrases(vararg all: String) = all.map { words(it) }

private val PAUSE_IN = setOf("in", "en", "em")
private val PAUSE_WORDS = setOf("pausa", "pause")

/** What "in pausa" may be followed by and still be the music's pause: "la musica", "questa canzone", the app. */
private val PAUSED_MUSIC = setOf(
    "il", "lo", "la", "l", "i", "gli", "le", "el", "los", "las", "les", "o", "a", "os", "as", "the", "my", "mia", "mi", "ma", "minha",
    "di", "de", "da", "do", "al", "alla", "à", "au", "to", "tutto", "todo", "tout", "tudo",
    "questa", "questo", "quella", "quello", "esta", "este", "esto", "esa", "ese", "eso", "cette", "ce", "cet", "ceci", "ça",
    "isto", "isso", "essa", "esse", "this", "that", "it",
    "musica", "música", "musique", "music", "canzone", "brano", "pezzo", "traccia", "canción", "cancion", "tema",
    "chanson", "morceau", "titre", "canção", "cancao", "faixa", "song", "track", "radio", "rádio",
    "podcast", "episodio", "episódio", "épisode", "episode", "riproduzione", "reproducción", "reprodução", "lecture", "playback",
)

/** "A 21 gradi", "a 21 grados", "à 21 degrés", "a 22 graus", "to 21 degrees", "auf 21 Grad", "a 21,5°". */
private val TEMPERATURE = Regex(
    "(?:^| )(?:a|al|ai|alla|alle|su|sul|sui|to|at|à|au|em|para|pra|en|auf) \\d+(?: \\d+)? ?" +
        "(?:gradi|grado|grados|degrés|degré|degres|degre|graus|grau|degrees|degree|grad|°c?|ºc?)(?: |$)",
)

/** Things of the house no song is named after: anywhere in what's asked. */
private val DEVICES = phrases(
    // Italian.
    "riscaldamento", "termosifone", "termosifoni", "calorifero", "caloriferi", "termostato", "condizionatore", "condizionatori",
    "climatizzatore", "aria condizionata", "deumidificatore", "caldaia", "stufa", "scaldabagno", "tapparella", "tapparelle",
    "persiana", "persiane", "serranda", "serrande", "cancello", "basculante", "antifurto", "lavatrice", "lavastoviglie",
    "aspirapolvere", "irrigazione", "timer", "ventilatore",
    // Spanish.
    "calefacción", "calefaccion", "aire acondicionado", "climatizador", "calentador", "radiador", "radiadores",
    "persianas", "toldo", "portón", "porton", "lavadora", "lavavajillas", "lavaplatos", "aspiradora", "temporizador", "riego",
    "ventilador",
    // French.
    "chauffage", "thermostat", "climatisation", "clim", "climatiseur", "radiateur", "radiateurs", "chaudière", "chaudiere",
    "volet", "volets", "portail", "lave linge", "lave vaisselle", "aspirateur", "minuteur", "ventilateur", "arrosage",
    // Portuguese.
    "aquecimento", "ar condicionado", "aquecedor", "estore", "estores", "portão", "portao",
    "máquina de lavar", "maquina de lavar", "lava loiça", "lava louça", "aspirador", "ventoinha",
    // English.
    "heating", "heater", "air conditioning", "air conditioner", "aircon", "radiator", "radiators", "blinds", "shutters",
    "garage door", "dishwasher", "washing machine", "vacuum cleaner", "sprinklers", "kettle",
    // German.
    "heizung", "klimaanlage", "rollladen", "rolladen", "rollläden", "jalousie", "jalousien", "garagentor", "staubsauger",
    "waschmaschine", "spülmaschine",
)

/** Words of the house that are also songs' names ("Luce", "Lights", "TV", "Alarm"): only after an article. */
private val SHARED = phrases(
    "luce", "luci", "lampada", "lampade", "lampadina", "tv", "tivù", "tivu", "tele", "televisione", "televisore", "sveglia",
    "allarme", "aria", "clima", "garage", "ac", "a/c",
    "luz", "luces", "lámpara", "lampara", "televisión", "television", "alarma", "despertador", "aire", "garaje", "caldera",
    "lumière", "lumiere", "lumières", "lumieres", "lampe", "télé", "télévision", "réveil", "reveil", "alarme",
    "luzes", "lâmpada", "televisão", "televisao", "ar", "garagem", "caldeira",
    "light", "lights", "lamp", "television", "alarm", "gate", "fan",
    "licht", "lichter", "fernseher", "wecker",
)

private val ARTICLES = setOf(
    "il", "lo", "la", "l", "i", "gli", "le", "un", "uno", "una", "mia", "mio", "mie", "miei",
    "el", "los", "las", "mi", "mis",
    "les", "une", "du", "des", "ma", "mon", "mes",
    "o", "a", "os", "as", "um", "uma", "minha", "meu", "minhas", "meus",
    "the", "an", "my", "our",
    "der", "die", "das", "den", "dem", "ein", "eine", "einen", "meine", "meinen",
)

/** What may follow a shared word for it to be the house: "la sveglia alle 7", "la tv su Rai 1", "the lights on". */
private val SETTINGS = setOf(
    "a", "al", "alla", "alle", "ai", "agli", "all", "allo", "su", "sul", "sulla", "sui", "in", "nel", "nella", "nei", "per",
    "accesa", "acceso", "accese", "accesi", "spenta", "spento", "spente", "spenti", "più", "meno",
    "las", "en", "para", "por", "encendida", "encendido", "encendidas", "encendidos", "apagada", "apagado", "apagadas", "apagados",
    "à", "au", "aux", "sur", "pour", "allumée", "allumé", "allumées", "allumés", "éteinte", "éteint", "éteintes", "éteints",
    "em", "no", "na", "nos", "nas", "ligada", "ligado", "ligadas", "ligados", "desligada", "desligado", "desligadas", "desligados",
    "to", "at", "on", "off", "for",
    "auf", "an", "aus", "im", "für", "um",
)
