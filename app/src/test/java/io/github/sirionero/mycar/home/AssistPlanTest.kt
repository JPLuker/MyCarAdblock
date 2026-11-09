package io.github.sirionero.mycar.home

import org.junit.Assert.assertEquals
import org.junit.Test

/** Which of the driver's Home Assistant voice assistants myCar asks, and how. */
class AssistPlanTest {

    private val homeEn = AssistPipeline("home-en", "en", "conversation.home_assistant")
    private val homeIt = AssistPipeline("home-it", "it", "conversation.home_assistant")
    private val claudeEn = AssistPipeline("claude-en", "en", "conversation.claude")
    private val claudeIt = AssistPipeline("claude-it", "it", "conversation.claude")

    @Test
    fun `the preferred pipeline, when it speaks the driver's language`() {
        assertEquals(AssistPlan.Run("home-en"), AssistPlan.of(listOf(homeEn, homeIt), "home-en", "en"))
        assertEquals(AssistPlan.Run("claude-it"), AssistPlan.of(listOf(homeIt, claudeIt), "claude-it", "it"))
    }

    @Test
    fun `another pipeline in the driver's language comes before the preferred one`() {
        assertEquals(AssistPlan.Run("home-it"), AssistPlan.of(listOf(homeEn, homeIt), "home-en", "it"))
        assertEquals(AssistPlan.Run("claude-it"), AssistPlan.of(listOf(claudeEn, claudeIt), "claude-en", "it"))
    }

    @Test
    fun `with none in the driver's language, the preferred agent is asked in it`() {
        // Home Assistant's own agent understood no Italian through its English pipeline, and
        // answered in Italian when asked in Italian.
        assertEquals(AssistPlan.Process("conversation.home_assistant", "it"), AssistPlan.of(listOf(homeEn), "home-en", "it"))
        assertEquals(AssistPlan.Process("conversation.claude", "es"), AssistPlan.of(listOf(homeEn, claudeEn), "claude-en", "es"))
    }

    @Test
    fun `a regional language counts as its language`() {
        val brazil = AssistPipeline("pt", "pt-BR", null)
        assertEquals(AssistPlan.Run("pt"), AssistPlan.of(listOf(homeEn, brazil), "home-en", "pt"))
    }

    @Test
    fun `without a preferred one, the first that speaks the language, or else the first`() {
        assertEquals(AssistPlan.Run("claude-it"), AssistPlan.of(listOf(homeEn, claudeIt, homeIt), null, "it"))
        assertEquals(AssistPlan.Process("conversation.home_assistant", "de"), AssistPlan.of(listOf(homeEn, claudeEn), null, "de"))
    }

    @Test
    fun `no pipelines at all, Home Assistant's default agent`() {
        assertEquals(AssistPlan.Process(null, "fr"), AssistPlan.of(emptyList(), null, "fr"))
    }

    private val claudeAll = AssistPipeline("claude", "*", "conversation.claude")

    // An Italian voice assistant whose agent Home Assistant no longer has (its integration removed or
    // not loaded): Assist answers only "Intent recognition engine conversation.jarvis is not found".
    private val jarvisIt = AssistPipeline("jarvis-it", "it", "conversation.jarvis")
    private val agents = setOf("conversation.home_assistant", "conversation.claude")

    @Test
    fun `a pipeline whose agent Home Assistant doesn't have is left out`() {
        assertEquals(AssistPlan.Run("home-it"), AssistPlan.of(listOf(homeEn, jarvisIt, homeIt), "jarvis-it", "it", agents))
        assertEquals(AssistPlan.Run("claude"), AssistPlan.of(listOf(jarvisIt, claudeAll), "jarvis-it", "it", agents))
    }

    @Test
    fun `with no other in the language, Home Assistant's own agent is asked in it`() {
        assertEquals(AssistPlan.Process("conversation.home_assistant", "it"), AssistPlan.of(listOf(homeEn, jarvisIt), "jarvis-it", "it", agents))
        // Only the missing one: Home Assistant's default agent.
        assertEquals(AssistPlan.Process(null, "it"), AssistPlan.of(listOf(jarvisIt), "jarvis-it", "it", agents))
    }

    @Test
    fun `agents not known, as before`() {
        assertEquals(AssistPlan.Run("jarvis-it"), AssistPlan.of(listOf(homeEn, jarvisIt, homeIt), "jarvis-it", "it", null))
    }

    @Test
    fun `Home Assistant's own agent, by either id, and a pipeline that names none are always there`() {
        val old = AssistPipeline("old-it", "it", "homeassistant")
        val none = AssistPipeline("none-it", "it", null)
        val listed = setOf("conversation.claude")
        assertEquals(listOf(homeIt, old, none), AssistPlan.usable(listOf(homeIt, old, none, jarvisIt), listed))
        assertEquals(AssistPlan.Run("old-it"), AssistPlan.of(listOf(jarvisIt, old), "jarvis-it", "it", listed))
    }

    @Test
    fun `a pipeline with a model speaks every language`() {
        // Home Assistant saves "*" for an agent that is a model (Claude, Gemini, ChatGPT, Ollama).
        assertEquals(AssistPlan.Run("claude"), AssistPlan.of(listOf(homeIt, claudeAll), "claude", "it"))
        assertEquals(AssistPlan.Run("claude"), AssistPlan.of(listOf(homeEn, claudeAll), "home-en", "it"))
        assertEquals(AssistPlan.Run("home-it"), AssistPlan.of(listOf(homeIt, claudeAll), "home-it", "it"))
    }
}
