package com.apexstudio.app.data.aireport

import com.apexstudio.app.domain.model.Project
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** A pointer to editable project content; report files never contain media, prompts, or output text. */
@Serializable
data class AiContentReportTarget(
    val projectId: String,
    val clipId: String,
    val clipType: String,
    val textOverlayId: String? = null
)

@Serializable
enum class AiContentReportReason(val label: String) {
    SEXUAL("Sexual content"),
    VIOLENT("Violent content"),
    HATEFUL("Hateful content"),
    OTHER("Other")
}

@Serializable
data class AiContentReport(
    val id: String = UUID.randomUUID().toString(),
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val target: AiContentReportTarget,
    val reason: AiContentReportReason
)

/** Submission destination does not exist: this journal only records a local reference and reason. */
fun resolveAiContentReportTarget(
    expectedProjectId: String,
    project: Project?,
    selectedClipId: String?,
    selectedTextOverlayId: String?
): AiContentReportTarget? {
    if (expectedProjectId.isBlank() || project == null || project.id != expectedProjectId) return null
    val clipId = selectedClipId?.takeIf(String::isNotBlank) ?: return null
    val clip = project.clips.firstOrNull { it.id == clipId } ?: return null
    val textOverlayId = selectedTextOverlayId?.takeIf { id -> clip.textOverlays.any { it.id == id } }
    return AiContentReportTarget(project.id, clip.id, clip.type.name, textOverlayId)
}

interface AiContentReportStore {
    /** Called off the main thread by the controller. Must either persist the complete report or throw. */
    fun append(report: AiContentReport)
}

/** Private app-files journal; atomic replacement prevents truncated JSON after process interruption. */
class FileAiContentReportStore(private val directory: File) : AiContentReportStore {
    private val file = File(directory, FILE_NAME)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun append(report: AiContentReport): Unit = synchronized(lock) {
        directory.mkdirs()
        val existing = if (file.exists()) json.decodeFromString<List<AiContentReport>>(file.readText()) else emptyList()
        val updated = existing + report
        val temp = File(directory, "$FILE_NAME.tmp")
        try {
            temp.writeText(json.encodeToString(updated))
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (temp.exists()) temp.delete()
        }
        Unit
    }

    fun readAll(): List<AiContentReport> = synchronized(lock) {
        if (!file.exists()) emptyList() else json.decodeFromString(file.readText())
    }

    companion object {
        const val FILE_NAME = "ai-content-reports.json"
        // Several editor ViewModels may write the same app-private journal.
        private val lock = Any()
    }
}
