package io.github.sirionero.mycar.car

/**
 * The questions and screens myCar answers itself, said whole ([heard] as AssistantCommands has it:
 * lower case, the fillers gone). They used to be any phrase with their words in it: "what time is
 * sunset" got the time, "how long until Christmas" the route, "how hot is the living room" the weather
 * outside, "what are the thermostat settings" myCar's settings, "turn on the heating when I go home"
 * the Home screen - and Home Assistant (AssistantBrain), which could have answered, never heard them.
 * What the phrasebooks make of the driver's language ("what time is it", "what's the weather", "when
 * do i arrive", "go home", "open settings") is among them.
 */
internal object WholePhrases {

    /** "What time is it", "what's the time", "time", "tell me the time" - not "what time is sunset". */
    fun time(heard: String) = TIME.matches(heard)

    /**
     * Anything about the weather; "how hot is it (outside)", "the temperature outside", "is it going to
     * rain (today)" - not "how hot is the living room" or "is it going to rain in Rome tomorrow".
     */
    fun weather(heard: String) =
        "weather" in heard || HOT_OR_COLD.matches(heard) || "temperature outside" in heard || RAIN.matches(heard)

    /**
     * "When do I arrive", "ETA", "how much longer", "how long until we get there", "how long to go" -
     * not "how long until Christmas" or "how long to boil an egg".
     */
    fun arrival(heard: String) = ETA.containsMatchIn(heard) || HOW_LONG.containsMatchIn(heard)

    /** "Go home", "home", "go to the home screen" - not "turn on the heating when I go home". */
    fun home(heard: String) = heard in HOME

    /** "Settings", "open settings", "go to the settings" - not "what are the thermostat settings". */
    fun settings(heard: String) = SETTINGS.matches(heard)

    private val TIME = Regex(
        "(?:what(?:'s|s| is)? (?:the )?(?:current )?time(?: is it)?|(?:tell me|give me|do you know|you know|say|check|do you have|have you got) (?:the )?(?:current )?time" +
            "|(?:tell me|do you know|you know) what time it is|(?:the )?(?:current )?time|time check)",
    )

    private val HOT_OR_COLD = Regex("how (?:hot|cold)(?: is it| it is| will it be| is it going to be| is it gonna be)?(?: outside| out| out there)?(?: today| now)?")

    private val RAIN = Regex("(?:is it |it's |it is )?(?:going to|gonna) rain(?: today| later| soon| tonight| now)?")

    /** Each of these anywhere: they only ever mean the route. */
    private val ETA = Regex(
        "\\b(?:eta|arrival time|time of arrival|how much longer|how long (?:left|is left)" +
            "|(?:when|what time) (?:do|will|are|am) (?:i|we)(?: going to)? (?:arrive|get there|be there|get to|reach))\\b",
    )

    /** "How long until" and the like: by itself, or until "we get there", "we arrive", "arrival", "go", "my destination", "home". */
    private val HOW_LONG = Regex(
        "\\bhow long (?:until|till|to|before)" +
            "(?: (?:i|we) (?:get|arrive|reach|are)\\b.*| (?:i'm|we're) (?:there|home)| go| arrival| (?:my |the )?destination| home)?$",
    )

    private val HOME = setOf(
        "go home", "home", "home screen", "the home screen", "go home screen", "go to home screen", "go to the home screen",
        "back to the home screen", "go back to the home screen",
    )

    private val SETTINGS = Regex(
        "(?:(?:open|go to|show|show me|bring up|pull up|switch to|change) )?(?:the |my )?(?:(?:mycar|mycar's|car|app) )?settings(?: screen| page| menu)?",
    )
}
