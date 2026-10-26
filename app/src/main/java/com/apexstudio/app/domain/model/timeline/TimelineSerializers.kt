package com.apexstudio.app.domain.model.timeline

import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object AndroidUriSerializer : KSerializer<Uri> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("android.net.Uri", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Uri) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Uri = Uri.parse(decoder.decodeString())
}

@Serializable
private data class EncodedRectF(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

object AndroidRectFSerializer : KSerializer<RectF> {
    private val delegate = EncodedRectF.serializer()
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: RectF) = delegate.serialize(
        encoder,
        EncodedRectF(value.left, value.top, value.right, value.bottom)
    )

    override fun deserialize(decoder: Decoder): RectF = delegate.deserialize(decoder).let {
        RectF(it.left, it.top, it.right, it.bottom)
    }
}

@Serializable
private data class EncodedColor(
    val red: Float,
    val green: Float,
    val blue: Float,
    val alpha: Float
)

object ComposeColorSerializer : KSerializer<Color> {
    private val delegate = EncodedColor.serializer()
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: Color) = delegate.serialize(
        encoder,
        EncodedColor(value.red, value.green, value.blue, value.alpha)
    )

    override fun deserialize(decoder: Decoder): Color = delegate.deserialize(decoder).let {
        Color(red = it.red, green = it.green, blue = it.blue, alpha = it.alpha)
    }
}

@Serializable
private data class EncodedOffset(val x: Float, val y: Float)

object ComposeOffsetSerializer : KSerializer<Offset> {
    private val delegate = EncodedOffset.serializer()
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: Offset) = delegate.serialize(
        encoder,
        EncodedOffset(value.x, value.y)
    )

    override fun deserialize(decoder: Decoder): Offset = delegate.deserialize(decoder).let {
        Offset(it.x, it.y)
    }
}

/** Serializer for normalized point lists used by drawing strokes. */
object ComposeOffsetListSerializer : KSerializer<List<Offset>> {
    private val delegate = ListSerializer(ComposeOffsetSerializer)
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: List<Offset>) = delegate.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): List<Offset> = delegate.deserialize(decoder)
}

@Serializable
private data class EncodedPath(val points: List<EncodedOffset>)

/**
 * Stores a flattened representation of a platform Path. Drawing input is already sampled into points;
 * paths with curves are flattened at sub-pixel tolerance for portable project JSON.
 */
object AndroidPathSerializer : KSerializer<Path> {
    private val delegate = EncodedPath.serializer()
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: Path) {
        val approximation = value.approximate(0.5f)
        val points = approximation.asList().chunked(3).mapNotNull { sample ->
            if (sample.size == 3) EncodedOffset(sample[1], sample[2]) else null
        }
        delegate.serialize(encoder, EncodedPath(points))
    }

    override fun deserialize(decoder: Decoder): Path {
        val points = delegate.deserialize(decoder).points
        return Path().apply {
            points.forEachIndexed { index, point ->
                if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
            }
        }
    }
}
