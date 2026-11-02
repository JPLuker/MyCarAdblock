package io.github.sirionero.mycar.webmusic

import android.os.Handler
import android.os.Looper
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import io.github.sirionero.mycar.MyCarLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Between the services' pages in the car and their players, which the car's music screens, dock
 * and buttons use ([WebMusicMediaService]): what each page says it plays, and the buttons pressed
 * there, sent on to the page. A button for a service with no page yet this drive makes one, out of
 * sight ([Pages]), and waits for it. Everything here happens on the main thread.
 */
object WebMusicPlayer {

    /** A service's page in the car, which takes the buttons. */
    interface Page {
        /** [action]: play, pause, nexttrack, previoustrack, or seekto [seconds]. */
        fun act(action: String, seconds: Double? = null)

        /** Signed out on the phone: the page starts again, signed out. */
        fun signedOut()

        /** Signed in on the phone: the page, still the one from before, starts again signed in. */
        fun signedIn()

        /** The voice assistant's "play [query]": the service's search, and the Play of what it finds first. */
        fun search(query: String)
    }

    /** The car screen's pages, which can make one for a service out of sight. */
    interface Pages {
        fun open(service: WebMusicService): Page?
    }

    /** Set by the car screen while it's up. */
    var pages: Pages? = null

    /**
     * The services that were playing when the car's screen was rebuilt (a new encoder, screen sharing
     * ended): the new screen plays them again, in the same drive. Their pages go with the old screen.
     */
    var resumeAfterRebuild: List<WebMusicService> = emptyList()

    private val states = mutableMapOf<String, MutableStateFlow<WebPlayerState>>()
    private val attached = mutableMapOf<String, Page>()
    private val main = Handler(Looper.getMainLooper())

    /** A button sent to a page, done once the page shows it. */
    private class Waiting(val id: String, val done: (WebPlayerState) -> Boolean, val future: SettableFuture<Unit?>)

    private val waiting = mutableListOf<Waiting>()

    private fun flow(id: String) = states.getOrPut(id) { MutableStateFlow(WebPlayerState()) }

    /** What [id]'s page last said. */
    fun state(id: String): StateFlow<WebPlayerState> = flow(id).asStateFlow()

    fun attach(id: String, page: Page) {
        attached[id] = page
    }

    /** The page went with the car's screen: nothing plays there any more. */
    fun detach(id: String, page: Page) {
        if (attached[id] !== page) return
        attached.remove(id)
        report(id, WebPlayerState())
    }

    /** What [id]'s page says now. Playing, it's the one that plays: the others pause. */
    fun report(id: String, state: WebPlayerState) {
        val was = flow(id).value
        flow(id).value = state
        if (state.playing && !was.playing) {
            attached.filterKeys { it != id && states[it]?.value?.playing == true }.forEach { (other, page) ->
                MyCarLog.i("music: $id plays - pausing $other")
                page.act("pause")
            }
        }
        waiting.filter { it.id == id && it.done(state) }.forEach {
            waiting.remove(it)
            it.future.set(null)
        }
    }

    /**
     * A button for [service]'s page. Its future is done once the page shows [done], or after a
     * moment: until then myCar's player shows what was asked for, rather than flicking back.
     */
    fun act(service: WebMusicService, action: String, seconds: Double? = null, done: (WebPlayerState) -> Boolean): ListenableFuture<*> {
        val page = attached[service.id] ?: pages?.open(service) ?: return Futures.immediateVoidFuture()
        val wait = Waiting(service.id, done, SettableFuture.create())
        waiting += wait
        main.postDelayed({ if (waiting.remove(wait)) wait.future.set(null) }, ANSWER_MS)
        page.act(action, seconds)
        return wait.future
    }

    /**
     * "Play [query]" on [service], from the voice assistant: its page searches, made out of sight
     * if there's none yet, and plays what it finds first.
     */
    fun search(service: WebMusicService, query: String) {
        val page = attached[service.id] ?: pages?.open(service) ?: return
        page.search(query)
    }

    /** [id] signed out on the phone: its page in the car, if there's one, starts again. */
    fun signedOut(id: String) {
        attached[id]?.signedOut()
    }

    /** [id]'s sign-in on the phone closed: its page in the car, if there's one, starts again. */
    fun signedIn(id: String) {
        attached[id]?.signedIn()
    }

    private const val ANSWER_MS = 2_000L
}
