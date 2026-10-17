package com.apexstudio.editor.thumbnail

/**
 * Bounded LRU store for decoded thumbnail strips — Bug 1 fix.
 *
 * The previous holder was a plain `Map` on a `MutableStateFlow` that a
 * new clip was simply ADDED to and never removed from, so leaving the
 * editor with a long project kept every decoded bitmap alive
 * indefinitely (`_thumbnails.update { current + (clip.id to thumbs) }`).
 * This store caps the number of resident clips at [maxSize] and drops
 * the least-recently-used one instead.
 *
 * [maxSize] counts ENTRIES (clips), not bytes — strips are small and
 * their count equals the project's clip count, which is the quantity
 * that actually grows. Bitmap reuse across the UI is handled by
 * `TimelineThumbnailCache`'s byte-counted cache; this store only bounds
 * the per-clip strips kept in state.
 *
 * Thread-safe: strips are produced on `Dispatchers.IO` while the UI
 * reads them, so every mutation is [synchronized] on the receiver and
 * every getter returns a copy — callers can iterate a snapshot without
 * risking `ConcurrentModificationException`.
 *
 * Uses an access-ordered `LinkedHashMap`, so a CLIP the UI re-reads
 * (e.g. the one currently on screen) survives while a forgotten one is
 * evicted first.
 *
 * Counting is exposed for diagnostics and for assertions in tests:
 * [hits] / [misses] show whether the strip cache is actually avoiding
 * decode work, and [evictions] exposes LRU pressure.
 *
 * @param maxSize Maximum number of resident strips; must be > 0.
 */
class ThumbnailStripLru<K : Any, V : Any>(val maxSize: Int) {

    init {
        require(maxSize > 0) { "maxSize must be > 0, was $maxSize" }
    }

    /** Access-ordered so `entries.iterator()` yields eldest-first. */
    private val map = LinkedHashMap<K, V>(16, 0.75f, true)

    /** Successful [get] calls. */
    var hits: Int = 0
        private set

    /** [get] calls that found nothing. */
    var misses: Int = 0
        private set

    /** Entries dropped by [put] because the store was full. */
    var evictions: Int = 0
        private set

    /** Number of resident entries. */
    val size: Int
        get() = synchronized(this) { map.size }

    /** True when nothing is resident. */
    val isEmpty: Boolean
        get() = synchronized(this) { map.isEmpty() }

    /**
     * Look up [key] and promote it to most-recently-used.
     * Returns `null` (and counts a miss) when absent.
     */
    fun get(key: K): V? = synchronized(this) {
        val value = map[key]
        if (value == null) misses++ else hits++
        value
    }

    /** Like [get] but does not touch the recency counters or ordering. */
    fun peek(key: K): V? = synchronized(this) { map[key] }

    /**
     * Insert or replace [key], evicting eldest-first until [maxSize] is
     * respected.
     *
     * The return value is what the caller must now let go of. When an
     * entry left the store to make room, THAT value is returned — it is
     * gone entirely, so a caller holding native resources (the thumbnail
     * strips hold Bitmaps) has to be told about it to recycle. When
     * nothing was evicted, the value this key overwrote is returned
     * instead, which is its own kind of dropped reference. `null` means
     * a new key landed in free space: nothing to release.
     *
     * Eviction deliberately wins over `previous` when both happen at
     * once (replace + capacity overflow together), because the evicted
     * entry is then unreferenced from here in every direction while the
     * replaced value at least shares its key with the new one.
     */
    fun put(key: K, value: V): V? = synchronized(this) {
        val previous = map.put(key, value)
        var evictedValue: V? = null
        while (map.size > maxSize) {
            val eldest = map.entries.iterator()
            if (!eldest.hasNext()) break
            val entry = eldest.next()
            eldest.remove()
            evictions++
            if (entry.key != key) evictedValue = entry.value
        }
        // Kilo warning: eviction wins over `previous`. The evicted entry
        // has left the store completely while a replaced value at least
        // precedes a consistent story for its own key - and the caller
        // that owns native resources recycles what this returns.
        evictedValue ?: previous
    }

    /** Remove [key] entirely. Returns the removed value if present. */
    fun remove(key: K): V? = synchronized(this) { map.remove(key) }

    /** Whether [key] is resident — without promoting it. */
    fun contains(key: K): Boolean = synchronized(this) { map.containsKey(key) }

    /**
     * Keys in eldest-first order (useful for pruning and for
     * mirroring the store into immutable UI state).
     */
    fun keys(): List<K> = synchronized(this) { map.keys.toList() }

    /**
     * An independent copy of the entries, eldest-first. Safe to hold
     * while other threads mutate the store — this is what UI state
     * collection should read.
     */
    fun snapshot(): Map<K, V> = synchronized(this) { LinkedHashMap<K, V>(map) }

    /** Drop every entry (e.g. when a project is closed). */
    fun clear(): Unit = synchronized(this) {
        map.clear()
        resetCountersLocked()
    }

    /** Zero the diagnostics counters without touching the entries. */
    fun resetCounters(): Unit = synchronized(this) { resetCountersLocked() }

    private fun resetCountersLocked() {
        hits = 0
        misses = 0
        evictions = 0
    }
}
