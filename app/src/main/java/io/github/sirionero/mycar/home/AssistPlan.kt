package io.github.sirionero.mycar.home

/** A voice assistant set up in Home Assistant: the [language] its agent speaks, and the [agent]. */
class AssistPipeline(val id: String, val language: String, val agent: String?)

/**
 * How myCar asks Home Assistant's Assist (home/Assist) something the driver said. Without Android,
 * so every case can be tried on the JVM.
 */
sealed interface AssistPlan {
    /** That pipeline, as Home Assistant runs it for its own voice devices. */
    data class Run(val pipeline: String) : AssistPlan

    /** No pipeline speaks the driver's language: its [agent] asked straight, in [language]. */
    data class Process(val agent: String?, val language: String) : AssistPlan

    companion object {
        /**
         * For a driver speaking [language] ("it"): the [preferred] pipeline if it speaks it, else
         * another one that does, as set up there (an Italian one with Claude, say). With none, the
         * preferred pipeline's agent is asked in the driver's language: Home Assistant's own agent
         * knows dozens, while its English pipeline understood no Italian at all. With the [agents]
         * Home Assistant has (null: not known), a pipeline whose agent isn't among them is left out
         * (see [usable]).
         */
        fun of(pipelines: List<AssistPipeline>, preferred: String?, language: String, agents: Set<String>? = null): AssistPlan {
            val kept = usable(pipelines, agents)
            // "*": a pipeline whose agent is a model (Claude, Gemini, ChatGPT...) speaks every language.
            fun speaks(p: AssistPipeline) = p.language == "*" || p.language.substringBefore('-').equals(language, ignoreCase = true)
            val speaking = kept.filter(::speaks)
            (speaking.firstOrNull { it.id == preferred } ?: speaking.firstOrNull())?.let { return Run(it.id) }
            val main = kept.firstOrNull { it.id == preferred } ?: kept.firstOrNull()
            return Process(main?.agent, language)
        }

        /**
         * The pipelines whose conversation agent Home Assistant has, of the [agents] it lists
         * (conversation/agent/list; null: not known, all of them). One whose agent is gone - the
         * integration removed or renamed, or not loaded (a key expired, the model's server off) - only
         * ever answers "Intent recognition engine … is not found". Home Assistant's own agent is always
         * there, also by its old id, and so is a pipeline that names none.
         */
        fun usable(pipelines: List<AssistPipeline>, agents: Set<String>?): List<AssistPipeline> {
            if (agents == null) return pipelines
            return pipelines.filter { p -> p.agent == null || p.agent in HOME_ASSISTANT_AGENTS || p.agent in agents }
        }
    }
}

/** Home Assistant's own conversation agent, and the id it had before it was an entity. */
private val HOME_ASSISTANT_AGENTS = setOf("conversation.home_assistant", "homeassistant")
