package io.github.sirionero.mycar.car

/**
 * Whether [heard] (as AssistantCommands has it: lower case, the fillers gone) is a command for the
 * car's music: every word one of those the music's commands are made of ("skip to the next song",
 * "pause the music", "play it again", the phrasebooks' "next", "pause", "continue"), or a word of the
 * music app's name, [appName] ("pause Spotify"). "When is my next appointment", "will it rain next
 * week", "pause the TV" or "stop the vacuum" are not: they go on to the other commands and to Home
 * Assistant (AssistantBrain), where before the song skipped or the music paused, and they never got there.
 */
internal fun musicCommandOnly(heard: String, appName: String? = null): Boolean {
    val name = appName.orEmpty().lowercase().split(NOT_A_WORD).filter { it.isNotEmpty() }.toSet()
    return heard.split(' ').filter { it.isNotEmpty() }.all { it in MUSIC_COMMAND_WORDS || it in name }
}

private val NOT_A_WORD = Regex("[^\\p{L}\\p{N}']+")

private val MUSIC_COMMAND_WORDS = setOf(
    // What to do.
    "next", "skip", "previous", "pause", "unpause", "stop", "resume", "play", "replay", "playing", "continue",
    "keep", "going", "start", "change", "turn", "on", "off", "back", "go", "again", "over", "ahead", "forward",
    "quiet", "shut", "up", "listen",
    // What to do it to.
    "song", "songs", "track", "tracks", "music", "tune", "playback", "episode", "podcast", "chapter", "album",
    "playlist", "radio", "station", "one", "it", "this", "that", "something", "else", "another", "different",
    // The little words around them.
    "the", "a", "my", "some", "to", "last", "please",
    // "Pause for a second", "skip the ad".
    "for", "second", "moment", "minute", "ad", "ads",
    // Said in the driver's language where the phrasebook leaves it as it is: "stop la musica",
    // "next canzone", "stop a la música", "pause la musique", "stopp die Musik", "pause a música".
    // Without them those went on to Home Assistant and the music played on.
    "il", "lo", "la", "le", "al", "alla", "musica", "canzone", "brano", "pezzo", "traccia",
    "el", "los", "las", "música", "canción", "cancion", "tema",
    "les", "du", "musique", "chanson", "morceau", "titre",
    "stopp", "der", "die", "das", "den", "musik", "lied", "titel",
    "o", "os", "as", "canção", "cancao", "faixa",
)
