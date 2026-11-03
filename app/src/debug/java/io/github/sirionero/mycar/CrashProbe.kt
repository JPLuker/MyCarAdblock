package io.github.sirionero.mycar

import android.util.Log
import android.view.View
import android.view.ViewGroup

/**
 * Debug only, for the crash-* shots (CarPreviewActivity --es script): what there is to know about
 * "LayoutNode should be attached to an owner" when it happens.
 *  - Compose's composition stack, as a suppressed exception on the crash (source information).
 *  - Every focus change on the car's screen, with the stack that made it: a web view that had the
 *    focus going away with its part of a page was the common thread of the crashes.
 *  - On the crash, the layout tree as it was: the node still laying out (the one placing a child that
 *    is no longer attached), its children and its parents, by measure policy and modifiers.
 * Compose's own classes are internal: read by reflection, and any part that can't be read is just
 * left out of the log.
 */
internal object CrashProbe {

    private const val TAG = "myCar"

    /** Composition stack traces from source information. Before any composition starts. */
    fun traceCompositions() {
        runCatching {
            val companion = Class.forName("androidx.compose.runtime.Composer").getField("Companion").get(null)
            companion.javaClass.getMethod("setDiagnosticStackTraceEnabled", java.lang.Boolean.TYPE).invoke(companion, true)
            MyCarLog.i("preview: Compose's composition stack traces on")
        }.onFailure { MyCarLog.w("preview: no composition stack traces (${it.javaClass.simpleName}: ${it.message})") }
    }

    fun install(root: () -> View?) {
        root()?.viewTreeObserver?.addOnGlobalFocusChangeListener { old, new ->
            logStack("focus ${name(old)} -> ${name(new)}")
            // Focus gone from a view being taken off the screen: a node part-way through losing a child
            // (detached already, still in its parent's list) shows here, with its parent's measure state.
            if (new == null) runCatching { midRemoval(root()) }.onFailure { Log.w(TAG, "preview: mid-removal not read", it) }
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            if (e.message?.contains("attached to an owner") == true) {
                runCatching { describe(root()) }.onFailure { Log.e(TAG, "preview: the layout tree couldn't be read", it) }
            }
            previous?.uncaughtException(thread, e)
        }
    }

    private fun name(view: View?): String =
        view?.let { "${it.javaClass.simpleName}@${Integer.toHexString(System.identityHashCode(it))}" } ?: "nothing"

    private fun logStack(what: String) {
        val frames = Throwable().stackTrace.drop(2).map { "${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})" }
        val parts = frames.chunked(25)
        parts.forEachIndexed { i, part ->
            Log.w(TAG, "preview: $what [${i + 1}/${parts.size}]\n  " + part.joinToString("\n  "))
        }
    }

    private fun composeView(view: View?): View? {
        if (view == null) return null
        if (view.javaClass.methods.any { it.name == "getRoot" && it.parameterTypes.isEmpty() && it.returnType.name.endsWith(".LayoutNode") }) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) composeView(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    /** A getter of a Compose internal, by the start of its name: internal names carry a suffix. */
    private fun getter(type: Class<*>, start: String) =
        type.methods.first { it.name == start || it.name.startsWith("$start$") }.apply { isAccessible = true }

    private fun describe(view: View?) {
        val compose = composeView(view) ?: run {
            Log.e(TAG, "preview: no Compose view on the car's screen")
            return
        }
        val rootNode = compose.javaClass.getMethod("getRoot").invoke(compose) ?: return
        val type = rootNode.javaClass
        val children = getter(type, "getChildren")
        val parent = getter(type, "getParent")
        val state = getter(type, "getLayoutState")
        val laying = ArrayList<Any>()
        fun walk(node: Any) {
            if (state.invoke(node).toString() == "LayingOut") laying.add(node)
            (children.invoke(node) as List<*>).forEach { it?.let(::walk) }
        }
        walk(rootNode)
        Log.e(TAG, "preview: on the crash, ${laying.size} node(s) still laying out")
        laying.forEach { node ->
            Log.e(TAG, "preview: laying out: ${describeNode(node)} (node ${Integer.toHexString(System.identityHashCode(node))})")
            (children.invoke(node) as List<*>).forEachIndexed { i, child ->
                child ?: return@forEachIndexed
                Log.e(TAG, "preview:   child $i: ${describeNode(child)}; texts ${texts(child, children)}")
            }
            var up = parent.invoke(node)
            var level = 1
            while (up != null && level <= 16) {
                Log.e(TAG, "preview:   parent $level: ${describeNode(up)}")
                up = parent.invoke(up)
                level++
            }
        }
    }

    /**
     * Every node with a child no longer attached in its list: a removal under way (LayoutNode.removeAt
     * detaches a child, and runs its callbacks, before taking it off the list). Its measurePending says
     * whether it has been measured again since the detach asked for it.
     */
    private fun midRemoval(view: View?) {
        val compose = composeView(view) ?: return
        val rootNode = compose.javaClass.getMethod("getRoot").invoke(compose) ?: return
        val type = rootNode.javaClass
        val children = getter(type, "getFoldedChildren")
        val attached = type.getMethod("isAttached")
        val measurePending = getter(type, "getMeasurePending")
        val layoutPending = getter(type, "getLayoutPending")
        val parent = getter(type, "getParent")
        var found = 0
        fun walk(node: Any) {
            (children.invoke(node) as List<*>).forEach { child ->
                child ?: return@forEach
                if (attached.invoke(child) == false) {
                    found++
                    Log.w(TAG, "preview: mid-removal: a detached child still listed: ${describeNode(child)}")
                    // The node losing it and the ones above: the subtree's root still attached, and its
                    // parent, whose measure the detach asked for first.
                    var up: Any? = node
                    var level = 0
                    while (up != null && level < 6) {
                        Log.w(
                            TAG,
                            "preview: mid-removal:   up $level: ${describeNode(up)} (node ${Integer.toHexString(System.identityHashCode(up))}), " +
                                "measurePending ${measurePending.invoke(up)}, layoutPending ${layoutPending.invoke(up)}",
                        )
                        up = parent.invoke(up)
                        level++
                    }
                } else {
                    walk(child)
                }
            }
        }
        walk(rootNode)
        Log.w(TAG, "preview: mid-removal: $found node(s) with a detached child in their list")
    }

    private fun describeNode(node: Any): String {
        val policy = runCatching { node.javaClass.getMethod("getMeasurePolicy").invoke(node)?.javaClass?.name?.substringAfterLast('.') }.getOrNull()
        val modifiers = runCatching {
            (node.javaClass.getMethod("getModifierInfo").invoke(node) as List<*>).map { info ->
                info?.javaClass?.getMethod("getModifier")?.invoke(info)?.toString()?.substringAfterLast('.')?.take(70)
            }
        }.getOrNull()
        val attached = runCatching { node.javaClass.getMethod("isAttached").invoke(node) }.getOrNull()
        return "$policy, attached $attached, modifiers $modifiers"
    }

    /** The first few texts drawn under [node], to tell which part of the screen it is. */
    private fun texts(node: Any, children: java.lang.reflect.Method): List<String> {
        val found = ArrayList<String>()
        fun walk(n: Any) {
            if (found.size >= 6) return
            runCatching {
                (n.javaClass.getMethod("getModifierInfo").invoke(n) as List<*>).forEach { info ->
                    val modifier = info?.javaClass?.getMethod("getModifier")?.invoke(info) ?: return@forEach
                    if (modifier.javaClass.simpleName.startsWith("Text")) {
                        runCatching {
                            modifier.javaClass.getDeclaredField("text").apply { isAccessible = true }.get(modifier)?.toString()?.take(40)
                        }.getOrNull()?.let(found::add)
                    }
                }
            }
            (children.invoke(n) as List<*>).forEach { it?.let(::walk) }
        }
        walk(node)
        return found
    }
}
