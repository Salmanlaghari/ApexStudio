package com.apexstudio.app.data.captions

import com.apexstudio.app.domain.model.ClipTimeMapping
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.domain.model.TextOverlay
import com.apexstudio.app.domain.model.TextOverlayKaraoke
import java.util.Locale

/** A single measured caption word; timestamps are project-clock milliseconds, end-exclusive. */
data class TimedCaptionWord(
    val id: String,
    val word: String,
    val startMs: Long,
    val endMs: Long,
    val clipId: String,
    val overlayId: String,
    val phraseGroupId: String? = null,
    val phraseText: String? = null,
    val tokenStartUtf16: Int = 0,
    val tokenEndUtf16Exclusive: Int = word.length
)

enum class FillerMatchKind { WORD_LIST, REPEATED_WORD, WORD_LIST_AND_REPEATED }

data class DetectedCaptionFiller(val timedWord: TimedCaptionWord, val kind: FillerMatchKind) {
    val id: String get() = timedWord.id
}

/** Deterministic matching over the existing measured word stream; this does not transcribe or infer timings. */
object CaptionFillerDetector {
    val defaultWords: List<String> = listOf("um", "uh", "uhm", "er", "ah", "hmm", "mm")

    private val singleWord = Regex("^[\\p{L}\\p{N}]+(?:['’][\\p{L}\\p{N}]+)*$")
    private val tokenPattern = Regex("[\\p{L}\\p{N}]+(?:['’][\\p{L}\\p{N}]+)*")

    fun normalizeList(words: Iterable<String>): List<String> = words.asSequence()
        .map { it.trim().lowercase(Locale.ROOT) }
        .filter { singleWord.matches(it) }
        .distinct()
        .toList()

    /** Return the one whole lexical token in [text], allowing surrounding punctuation. */
    fun singleToken(text: String): MatchResult? {
        val matches = tokenPattern.findAll(text).toList()
        return matches.singleOrNull()
    }

    fun detect(
        words: List<TimedCaptionWord>,
        fillerWords: Iterable<String> = defaultWords
    ): List<DetectedCaptionFiller> {
        val configured = normalizeList(fillerWords).toSet()
        val ordered = words.mapNotNull { word ->
            val token = singleToken(word.word) ?: return@mapNotNull null
            if (word.endMs <= word.startMs) null else word.copy(word = token.value)
        }
            .sortedWith(compareBy<TimedCaptionWord> { it.startMs }.thenBy { it.endMs }.thenBy { it.id })
        return ordered.mapIndexedNotNull { index, word ->
            val inList = word.word.lowercase(Locale.ROOT) in configured
            val previous = ordered.getOrNull(index - 1)?.word?.lowercase(Locale.ROOT)
            val repeated = previous != null && previous == word.word.lowercase(Locale.ROOT)
            if (!inList && !repeated) null else DetectedCaptionFiller(
                timedWord = word,
                kind = when {
                    inList && repeated -> FillerMatchKind.WORD_LIST_AND_REPEATED
                    repeated -> FillerMatchKind.REPEATED_WORD
                    else -> FillerMatchKind.WORD_LIST
                }
            )
        }
    }
}

/** Stable source phrase cluster for full-phrase karaoke overlays. */
internal data class CaptionPhraseGroup(val id: String, val overlays: List<TextOverlay>)

/** Extracts only explicit single-word or valid karaoke word intervals; phrase captions are not split artificially. */
object CaptionFillerWordStream {
    fun fromMainVideoClips(clips: List<MediaClip>): List<TimedCaptionWord> {
        var projectStartMs = 0L
        val words = mutableListOf<TimedCaptionWord>()
        clips.forEach { clip ->
            val isMainVideo = clip.trackIndex == 0 && clip.type == ClipType.VIDEO
            if (isMainVideo) {
                val duration = clip.effectiveDurationMs()
                val auto = clip.textOverlays.filter { it.presetId == "caption_auto" }
                val phraseGroupByOverlay = phraseGroups(clip.id, auto).flatMap { group ->
                    group.overlays.map { it.id to group.id }
                }.toMap()
                auto.forEach { overlay ->
                    val karaokeRange = TextOverlayKaraoke.validRange(overlay)
                    val token = if (karaokeRange != null) {
                        CaptionFillerDetector.singleToken(overlay.text.substring(karaokeRange.startUtf16, karaokeRange.endUtf16Exclusive))
                            ?.let { tokenResult ->
                                Triple(tokenResult.value, karaokeRange.startUtf16 + tokenResult.range.first,
                                    karaokeRange.startUtf16 + tokenResult.range.last + 1)
                            }
                    } else {
                        CaptionFillerDetector.singleToken(overlay.text)?.let { Triple(it.value, it.range.first, it.range.last + 1) }
                    } ?: return@forEach
                    // A non-karaoke overlay is word-timed only when its text contains one lexical token.
                    if (karaokeRange == null && CaptionFillerDetector.singleToken(overlay.text) == null) return@forEach
                    val localStart = overlay.startMs.coerceIn(0L, duration)
                    val measuredEnd = if (karaokeRange == null) overlay.endMs else overlayEndExclusive(overlay)
                    val localEndExclusive = measuredEnd.coerceIn(0L, duration)
                    if (localEndExclusive <= localStart) return@forEach
                    val phraseGroup = phraseGroupByOverlay[overlay.id]
                    val id = listOf(clip.id, overlay.id, token.second, token.third, localStart, localEndExclusive).joinToString(":")
                    words += TimedCaptionWord(
                        id = id,
                        word = token.first,
                        startMs = projectStartMs + localStart,
                        endMs = projectStartMs + localEndExclusive,
                        clipId = clip.id,
                        overlayId = overlay.id,
                        phraseGroupId = phraseGroup,
                        phraseText = phraseGroup?.let { overlay.text },
                        tokenStartUtf16 = token.second,
                        tokenEndUtf16Exclusive = token.third
                    )
                }
                projectStartMs += duration
            } else if (clip.trackIndex == 0 && clip.type == ClipType.IMAGE) {
                projectStartMs += clip.effectiveDurationMs()
            }
        }
        return words.sortedWith(compareBy<TimedCaptionWord> { it.startMs }.thenBy { it.endMs }.thenBy { it.id })
    }

    internal fun phraseGroups(clipId: String, overlays: List<TextOverlay>): List<CaptionPhraseGroup> {
        val eligible = overlays.filter {
            it.presetId == "caption_auto" && it.highlightSourceFingerprint != null &&
                it.highlightSourceFingerprint == TextOverlayKaraoke.fingerprint(it.text)
        }.sortedWith(compareBy<TextOverlay> { it.startMs }.thenBy { it.endMs }.thenBy { it.id })
        val result = mutableListOf<CaptionPhraseGroup>()
        var cluster = mutableListOf<TextOverlay>()
        var clusterEnd = Long.MIN_VALUE
        var clusterKey: Pair<String, String>? = null
        fun flush() {
            if (cluster.isNotEmpty()) {
                val id = "$clipId:${cluster.first().id}"
                result += CaptionPhraseGroup(id, cluster.toList())
            }
            cluster = mutableListOf()
            clusterEnd = Long.MIN_VALUE
        }
        eligible.forEach { overlay ->
            val key = overlay.highlightSourceFingerprint!! to overlay.text
            if (cluster.isNotEmpty() && (key != clusterKey || overlay.startMs > clusterEnd)) flush()
            if (cluster.isEmpty()) clusterKey = key
            cluster += overlay
            clusterEnd = maxOf(clusterEnd, overlayEndExclusive(overlay))
        }
        flush()
        return result
    }

    fun overlayEndExclusive(overlay: TextOverlay): Long = if (overlay.rangeEndExclusive) {
        overlay.endMs
    } else if (overlay.endMs == Long.MAX_VALUE) {
        Long.MAX_VALUE
    } else {
        overlay.endMs + 1L
    }
}

/** Mutates only the caption overlays selected for removal; retained karaoke words get fresh valid offsets. */
object CaptionFillerCaptionEditor {
    fun removeSelectedWords(
        clip: MediaClip,
        selected: List<TimedCaptionWord>
    ): List<TextOverlay> {
        if (selected.isEmpty()) return clip.textOverlays
        val plainOverlayIds = selected.filter { it.phraseGroupId == null }.map { it.overlayId }.toSet()
        val rangesByGroup = selected.filter { it.phraseGroupId != null }
            .groupBy { it.phraseGroupId!! }
            .mapValues { (_, matches) -> matches.map { it.tokenStartUtf16 to it.tokenEndUtf16Exclusive }.distinct() }
        if (plainOverlayIds.isEmpty() && rangesByGroup.isEmpty()) return clip.textOverlays
        val groupByOverlay = CaptionFillerWordStream.phraseGroups(clip.id, clip.textOverlays)
            .flatMap { group -> group.overlays.map { it.id to group.id } }.toMap()
        return clip.textOverlays.mapNotNull { overlay ->
            if (overlay.id in plainOverlayIds) return@mapNotNull null
            val groupId = groupByOverlay[overlay.id] ?: return@mapNotNull overlay
            val removedRanges = rangesByGroup[groupId] ?: return@mapNotNull overlay
            val valid = TextOverlayKaraoke.validRange(overlay)
            if (valid != null && removedRanges.any { (start, end) -> start == valid.startUtf16 && end == valid.endUtf16Exclusive }) {
                return@mapNotNull null
            }
            val expanded = mergedDeletionRanges(overlay.text, removedRanges)
            val newText = removeRanges(overlay.text, expanded)
            if (newText.isBlank()) return@mapNotNull null
            val newRange = valid?.let { old ->
                val start = shiftOffset(old.startUtf16, expanded)
                val end = shiftOffset(old.endUtf16Exclusive, expanded)
                if (end > start) start to end else null
            }
            overlay.copy(
                text = newText,
                highlightStartUtf16 = newRange?.first,
                highlightEndUtf16 = newRange?.second,
                highlightSourceFingerprint = TextOverlayKaraoke.fingerprint(newText)
            )
        }
    }

    private fun mergedDeletionRanges(text: String, tokenRanges: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
        val expanded = tokenRanges.mapNotNull { (rawStart, rawEnd) ->
            if (rawStart < 0 || rawEnd <= rawStart || rawEnd > text.length) return@mapNotNull null
            var start = rawStart
            var end = rawEnd
            while (start > 0 && isPunctuation(text[start - 1])) start--
            while (end < text.length && isPunctuation(text[end])) end++
            if (end < text.length) {
                while (end < text.length && text[end].isWhitespace()) end++
            } else {
                while (start > 0 && text[start - 1].isWhitespace()) start--
            }
            start to end
        }.sortedBy { it.first }
        if (expanded.isEmpty()) return emptyList()
        val merged = mutableListOf<Pair<Int, Int>>()
        expanded.forEach { range ->
            val last = merged.lastOrNull()
            if (last == null || range.first > last.second) merged += range
            else merged[merged.lastIndex] = last.first to maxOf(last.second, range.second)
        }
        return merged
    }

    private fun isPunctuation(char: Char): Boolean = !char.isLetterOrDigit() && !char.isWhitespace() && char != '\'' && char != '’'

    private fun removeRanges(text: String, ranges: List<Pair<Int, Int>>): String {
        val result = StringBuilder(text.length)
        var cursor = 0
        ranges.forEach { (start, end) ->
            result.append(text, cursor, start)
            cursor = end
        }
        result.append(text, cursor, text.length)
        return result.toString().trim()
    }

    private fun shiftOffset(offset: Int, ranges: List<Pair<Int, Int>>): Int =
        offset - ranges.filter { it.second <= offset }.sumOf { it.second - it.first }
}

/** One project-clock interval to remove; end is exclusive. */
data class TimelineCutRange(val startMs: Long, val endMs: Long) {
    init { require(startMs >= 0L && endMs > startMs) }
}

data class KeptMainTrackSegment(
    val sourceClip: MediaClip,
    val sourceStartMs: Long,
    val sourceEndMs: Long,
    val localOutputStartMs: Long,
    val localOutputEndMs: Long
)

data class MainTrackCutPlan(
    val keptSegmentsByClipId: Map<String, List<KeptMainTrackSegment>>,
    val cutRangesByClipId: Map<String, List<TimelineCutRange>>,
    val removedDurationMs: Long
)

/** Plans source-preserving V1 pieces. Non-main clips and project audio/music tracks are never cut. */
object CaptionFillerCutPlanner {
    /** AudioTrack entries (music/voiceover) are intentionally excluded from source cuts. */
    fun plan(project: Project, requestedCuts: List<TimelineCutRange>): MainTrackCutPlan =
        plan(project.clips, requestedCuts)

    fun plan(clips: List<MediaClip>, requestedCuts: List<TimelineCutRange>): MainTrackCutPlan {
        val cuts = mergeCuts(requestedCuts)
        var projectStart = 0L
        var removed = 0L
        val kept = linkedMapOf<String, List<KeptMainTrackSegment>>()
        val byClip = linkedMapOf<String, List<TimelineCutRange>>()
        clips.forEach { clip ->
            val isMain = clip.trackIndex == 0 && (clip.type == ClipType.VIDEO || clip.type == ClipType.IMAGE)
            if (!isMain) return@forEach
            val duration = clip.effectiveDurationMs().coerceAtLeast(0L)
            val projectEnd = projectStart + duration
            val intersecting = cuts.mapNotNull { cut ->
                val start = maxOf(projectStart, cut.startMs)
                val end = minOf(projectEnd, cut.endMs)
                if (end > start) TimelineCutRange(start, end) else null
            }
            val keptLocal = mutableListOf<Pair<Long, Long>>()
            var localCursor = projectStart
            intersecting.forEach { cut ->
                if (cut.startMs > localCursor) keptLocal += (localCursor - projectStart) to (cut.startMs - projectStart)
                localCursor = maxOf(localCursor, cut.endMs)
                removed += cut.endMs - cut.startMs
            }
            if (localCursor < projectEnd) keptLocal += (localCursor - projectStart) to duration
            val segments = keptLocal.mapNotNull { (localStart, localEnd) ->
                if (localEnd <= localStart) return@mapNotNull null
                val sourceStart = ClipTimeMapping.sourceAtProjectTime(clips, clip, projectStart + localStart)
                    .coerceIn(clip.trimStartMs, clip.trimEndMs)
                val sourceEnd = ClipTimeMapping.sourceAtProjectTime(clips, clip, projectStart + localEnd)
                    .coerceIn(sourceStart, clip.trimEndMs)
                if (sourceEnd <= sourceStart) null else KeptMainTrackSegment(clip, sourceStart, sourceEnd, localStart, localEnd)
            }
            if (intersecting.isNotEmpty()) {
                kept[clip.id] = segments
                byClip[clip.id] = intersecting
            }
            projectStart = projectEnd
        }
        return MainTrackCutPlan(kept, byClip, removed)
    }

    private fun mergeCuts(ranges: List<TimelineCutRange>): List<TimelineCutRange> {
        val sorted = ranges.filter { it.startMs >= 0L && it.endMs > it.startMs }.sortedBy { it.startMs }
        if (sorted.isEmpty()) return emptyList()
        val merged = mutableListOf<TimelineCutRange>()
        sorted.forEach { range ->
            val previous = merged.lastOrNull()
            if (previous == null || range.startMs > previous.endMs) merged += range
            else merged[merged.lastIndex] = TimelineCutRange(previous.startMs, maxOf(previous.endMs, range.endMs))
        }
        return merged
    }
}
