package com.apexstudio.app.data.photoedit

import com.apexstudio.app.ai.mediapipe.FaceDetection
import com.apexstudio.app.domain.model.PhotoCropRect
import kotlin.math.max
import kotlin.math.min

/** Pure geometry for turning real, normalized face detections into a persistent still-photo crop. */
object PhotoFaceCropEngine {
    /**
     * Compute a bounded crop centred on the largest visible face. [outputAspectRatio] is in
     * source-pixel coordinates; null keeps the source image's original aspect ratio. No detection
     * is ever synthesized: an empty or invalid detection list returns null.
     */
    fun cropAroundLargestFace(
        sourceWidth: Int,
        sourceHeight: Int,
        faces: List<FaceDetection>,
        outputAspectRatio: Float? = null,
        margin: Float = 0.35f,
    ): PhotoCropRect? {
        require(sourceWidth > 0 && sourceHeight > 0)
        require(margin.isFinite() && margin in 0f..2f)
        require(outputAspectRatio == null || (outputAspectRatio.isFinite() && outputAspectRatio > 0f))

        val face = faces.asSequence().mapNotNull { detection ->
            val box = detection.box
            if (!box.left.isFinite() || !box.top.isFinite() ||
                !box.right.isFinite() || !box.bottom.isFinite()) return@mapNotNull null
            val left = box.left.coerceIn(0f, 1f)
            val top = box.top.coerceIn(0f, 1f)
            val right = box.right.coerceIn(0f, 1f)
            val bottom = box.bottom.coerceIn(0f, 1f)
            if (right <= left || bottom <= top) return@mapNotNull null
            detection.copy(box = box.copy(left = left, top = top, right = right, bottom = bottom))
        }.maxByOrNull { (it.box.right - it.box.left) * (it.box.bottom - it.box.top) }
            ?: return null

        val aspect = outputAspectRatio ?: sourceWidth.toFloat() / sourceHeight
        val faceWidthPx = (face.box.right - face.box.left) * sourceWidth
        val faceHeightPx = (face.box.bottom - face.box.top) * sourceHeight
        var cropWidthPx = max(faceWidthPx, faceHeightPx * aspect) * (1f + 2f * margin)
        var cropHeightPx = cropWidthPx / aspect
        val fit = min(1f, min(sourceWidth / cropWidthPx, sourceHeight / cropHeightPx))
        cropWidthPx = (cropWidthPx * fit).coerceAtMost(sourceWidth.toFloat())
        cropHeightPx = (cropHeightPx * fit).coerceAtMost(sourceHeight.toFloat())

        val centerX = face.box.centerX * sourceWidth
        val centerY = face.box.centerY * sourceHeight
        val maxLeft = (sourceWidth - cropWidthPx).coerceAtLeast(0f)
        val maxTop = (sourceHeight - cropHeightPx).coerceAtLeast(0f)
        val leftPx = (centerX - cropWidthPx / 2f).coerceIn(0f, maxLeft)
        val topPx = (centerY - cropHeightPx / 2f).coerceIn(0f, maxTop)
        val rect = PhotoCropRect(
            left = leftPx / sourceWidth,
            top = topPx / sourceHeight,
            right = (leftPx + cropWidthPx) / sourceWidth,
            bottom = (topPx + cropHeightPx) / sourceHeight,
        )
        return PhotoEditRenderer.clampRect(rect, minSize = 1f / max(sourceWidth, sourceHeight))
    }
}
