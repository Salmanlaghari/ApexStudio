package com.apexstudio.editor.thumbnail

/** Per-pass decoder budget. Only a newly claimed visible decode needs fast polling. */
internal class TimelineThumbnailRequestPass(
    private val inFlightKeys: MutableSet<Pair<String, Long>>,
    private val maxJobs: Int = 4,
) {
    var launched: Int = 0
        private set
    var visibleWorkDispatched: Boolean = false
        private set
    val pollDelayMs: Long
        get() = if (visibleWorkDispatched) 40L else 400L

    fun claim(key: Pair<String, Long>, cached: Boolean, visible: Boolean): Boolean {
        if (cached || inFlightKeys.contains(key)) return false
        if (launched >= maxJobs || inFlightKeys.size >= maxJobs || !inFlightKeys.add(key)) return false
        launched++
        if (visible) visibleWorkDispatched = true
        return true
    }
}
