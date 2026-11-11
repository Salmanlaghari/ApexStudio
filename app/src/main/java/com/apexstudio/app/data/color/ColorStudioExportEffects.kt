package com.apexstudio.app.data.color

import android.content.Context
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import com.apexstudio.app.data.adjust.AdjustmentsGlEffect
import com.apexstudio.app.data.filter.LutFilterGlEffect
import com.apexstudio.app.domain.model.VideoAdjustments

/** Media3 stages for the project timeline grade: the shared 3D LUT, then spatial sharpness. */
@OptIn(UnstableApi::class)
internal fun createColorStudioVideoEffects(
    context: Context,
    grade: ColorStudioGrade
): List<Effect> {
    require(grade.hasValidRenderParameters()) { "Color adjustment values are outside the supported range." }
    return buildList {
        add(LutFilterGlEffect(context, preset = null, preloaded = grade.toLutTexture()))
        if (grade.sharpness > 0f) {
            add(AdjustmentsGlEffect(adjustments = VideoAdjustments(sharpness = grade.sharpness)))
        }
    }
}
