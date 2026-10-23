package com.apexstudio.app.data.fx

/**
 * Original, social-video-inspired Creator Pack shaders. These are new profiles,
 * not claims about copying any proprietary or date-specific effect catalog.
 * Each profile selects a real family parameter set (geometry, frequency,
 * amplitude and tint), rather than aliasing a pre-existing preset.
 */
internal object ModernFxShaders {
    private enum class Family {
        PRISM, SPLIT_TONE, RIPPLE, MOSAIC, CRT, HALATION, INK, MIRROR, GLITCH, STREAK
    }

    private data class Profile(
        val preset: FxPreset,
        val family: Family,
        val a: String,
        val b: String,
        val c: String,
        val tint: String
    )

    private val profiles = listOf(
        Profile(FxPreset.CREATOR_PRISM_AURORA, Family.PRISM, "1.5", "0.12", "0.38", "0.18, 0.92, 0.78"),
        Profile(FxPreset.CREATOR_PRISM_CANDY, Family.PRISM, "2.4", "0.16", "0.46", "1.00, 0.30, 0.68"),
        Profile(FxPreset.CREATOR_PRISM_COPPER, Family.PRISM, "3.2", "0.20", "0.55", "1.00, 0.48, 0.20"),
        Profile(FxPreset.CREATOR_PRISM_ARCTIC, Family.PRISM, "4.1", "0.24", "0.64", "0.28, 0.76, 1.00"),
        Profile(FxPreset.CREATOR_PRISM_ROSE, Family.PRISM, "5.0", "0.29", "0.72", "1.00, 0.38, 0.48"),

        Profile(FxPreset.CREATOR_TONE_PEACH, Family.SPLIT_TONE, "0.24", "0.38", "0.68", "1.00, 0.48, 0.30"),
        Profile(FxPreset.CREATOR_TONE_TEAL, Family.SPLIT_TONE, "0.30", "0.44", "0.74", "0.12, 0.82, 0.78"),
        Profile(FxPreset.CREATOR_TONE_VIOLET, Family.SPLIT_TONE, "0.36", "0.50", "0.80", "0.60, 0.30, 1.00"),
        Profile(FxPreset.CREATOR_TONE_SUNSET, Family.SPLIT_TONE, "0.42", "0.56", "0.86", "1.00, 0.22, 0.14"),
        Profile(FxPreset.CREATOR_TONE_MINT, Family.SPLIT_TONE, "0.48", "0.62", "0.92", "0.38, 1.00, 0.62"),

        Profile(FxPreset.CREATOR_RIPPLE_LAGOON, Family.RIPPLE, "2.0", "0.014", "3.0", "0.08, 0.72, 0.92"),
        Profile(FxPreset.CREATOR_RIPPLE_HEAT, Family.RIPPLE, "3.2", "0.022", "4.5", "1.00, 0.32, 0.08"),
        Profile(FxPreset.CREATOR_RIPPLE_MOON, Family.RIPPLE, "4.4", "0.010", "2.4", "0.42, 0.48, 1.00"),
        Profile(FxPreset.CREATOR_RIPPLE_RAIN, Family.RIPPLE, "5.6", "0.018", "5.2", "0.10, 0.90, 0.62"),
        Profile(FxPreset.CREATOR_RIPPLE_GLASS, Family.RIPPLE, "6.8", "0.026", "3.7", "0.72, 0.90, 1.00"),

        Profile(FxPreset.CREATOR_MOSAIC_MICRO, Family.MOSAIC, "5.0", "7.0", "0.02", "0.80, 0.92, 1.00"),
        Profile(FxPreset.CREATOR_MOSAIC_TILES, Family.MOSAIC, "9.0", "6.0", "0.05", "1.00, 0.78, 0.46"),
        Profile(FxPreset.CREATOR_MOSAIC_DIAMOND, Family.MOSAIC, "8.0", "11.0", "0.08", "0.78, 0.52, 1.00"),
        Profile(FxPreset.CREATOR_MOSAIC_BOLD, Family.MOSAIC, "14.0", "10.0", "0.11", "1.00, 0.32, 0.42"),
        Profile(FxPreset.CREATOR_MOSAIC_SOFT, Family.MOSAIC, "11.0", "15.0", "0.14", "0.32, 0.88, 0.76"),

        Profile(FxPreset.CREATOR_CRT_AMBER, Family.CRT, "120.0", "0.20", "0.12", "1.00, 0.58, 0.18"),
        Profile(FxPreset.CREATOR_CRT_CYAN, Family.CRT, "150.0", "0.28", "0.16", "0.12, 0.88, 1.00"),
        Profile(FxPreset.CREATOR_CRT_GREEN, Family.CRT, "180.0", "0.36", "0.20", "0.30, 1.00, 0.36"),
        Profile(FxPreset.CREATOR_CRT_MAGENTA, Family.CRT, "210.0", "0.44", "0.24", "1.00, 0.24, 0.78"),
        Profile(FxPreset.CREATOR_CRT_MONO, Family.CRT, "240.0", "0.52", "0.28", "0.82, 0.90, 1.00"),

        Profile(FxPreset.CREATOR_HALATION_HONEY, Family.HALATION, "0.30", "7.0", "0.62", "1.00, 0.58, 0.16"),
        Profile(FxPreset.CREATOR_HALATION_BERRY, Family.HALATION, "0.40", "9.0", "0.68", "1.00, 0.18, 0.48"),
        Profile(FxPreset.CREATOR_HALATION_ICE, Family.HALATION, "0.50", "11.0", "0.74", "0.24, 0.72, 1.00"),
        Profile(FxPreset.CREATOR_HALATION_CORAL, Family.HALATION, "0.60", "13.0", "0.80", "1.00, 0.34, 0.24"),
        Profile(FxPreset.CREATOR_HALATION_LILAC, Family.HALATION, "0.70", "15.0", "0.86", "0.70, 0.38, 1.00"),

        Profile(FxPreset.CREATOR_INK_POP, Family.INK, "0.18", "0.52", "0.15", "1.00, 0.10, 0.28"),
        Profile(FxPreset.CREATOR_INK_NEWSPRINT, Family.INK, "0.24", "0.66", "0.22", "0.10, 0.12, 0.16"),
        Profile(FxPreset.CREATOR_INK_RISO, Family.INK, "0.30", "0.80", "0.29", "1.00, 0.42, 0.08"),
        Profile(FxPreset.CREATOR_INK_POSTER, Family.INK, "0.36", "0.94", "0.36", "0.20, 0.28, 1.00"),
        Profile(FxPreset.CREATOR_INK_PASTEL, Family.INK, "0.42", "1.08", "0.43", "0.32, 0.88, 0.70"),

        Profile(FxPreset.CREATOR_MIRROR_QUAD, Family.MIRROR, "4.0", "0.00", "0.72", "0.26, 0.88, 1.00"),
        Profile(FxPreset.CREATOR_MIRROR_HEX, Family.MIRROR, "6.0", "0.14", "0.78", "1.00, 0.40, 0.72"),
        Profile(FxPreset.CREATOR_MIRROR_RADIAL, Family.MIRROR, "8.0", "0.28", "0.84", "0.42, 1.00, 0.62"),
        Profile(FxPreset.CREATOR_MIRROR_KALEIDO, Family.MIRROR, "10.0", "0.42", "0.90", "1.00, 0.72, 0.24"),
        Profile(FxPreset.CREATOR_MIRROR_FAN, Family.MIRROR, "12.0", "0.56", "0.96", "0.56, 0.40, 1.00"),

        Profile(FxPreset.CREATOR_GLITCH_TANGERINE, Family.GLITCH, "7.0", "0.006", "0.18", "1.00, 0.36, 0.08"),
        Profile(FxPreset.CREATOR_GLITCH_COBALT, Family.GLITCH, "11.0", "0.010", "0.24", "0.12, 0.34, 1.00"),
        Profile(FxPreset.CREATOR_GLITCH_LIME, Family.GLITCH, "15.0", "0.014", "0.30", "0.42, 1.00, 0.10"),
        Profile(FxPreset.CREATOR_GLITCH_PLUM, Family.GLITCH, "19.0", "0.018", "0.36", "0.78, 0.14, 1.00"),
        Profile(FxPreset.CREATOR_GLITCH_SILVER, Family.GLITCH, "23.0", "0.022", "0.42", "0.72, 0.88, 1.00"),

        Profile(FxPreset.CREATOR_STREAK_ORBIT, Family.STREAK, "0.00", "0.35", "0.64", "0.24, 0.76, 1.00"),
        Profile(FxPreset.CREATOR_STREAK_RUSH, Family.STREAK, "0.62", "0.48", "0.72", "1.00, 0.28, 0.12"),
        Profile(FxPreset.CREATOR_STREAK_DREAM, Family.STREAK, "1.26", "0.61", "0.80", "0.72, 0.44, 1.00"),
        Profile(FxPreset.CREATOR_STREAK_SOLAR, Family.STREAK, "1.90", "0.74", "0.88", "1.00, 0.74, 0.20"),
        Profile(FxPreset.CREATOR_STREAK_TWILIGHT, Family.STREAK, "2.54", "0.87", "0.96", "0.28, 0.88, 0.72")
    ).associateBy { it.preset }

    fun shaderFor(preset: FxPreset): String? = profiles[preset]?.let(::buildShader)

    private fun buildShader(p: Profile): String {
        val speedUniform = if (p.preset.isAnimated) "uniform float uSpeed;" else ""
        val phase = if (p.preset.isAnimated) "uTime * uSpeed" else "0.0"
        val body = when (p.family) {
            Family.PRISM -> """
                vec2 uv = vTextureCoord;
                float fringe = uTexel.x * P_A * (0.5 + 0.5 * sin(uv.y * 9.0 + PHASE));
                vec4 redSample = texture2D(uTexSampler, clamp(uv + vec2(fringe, 0.0), 0.0, 1.0));
                vec4 blueSample = texture2D(uTexSampler, clamp(uv - vec2(fringe, 0.0), 0.0, 1.0));
                float edge = smoothstep(0.25, 0.95, length(uv - vec2(0.5)) * 1.35);
                vec3 effected = vec3(redSample.r, base.g, blueSample.b) + P_TINT * edge * P_B * P_C;
            """
            Family.SPLIT_TONE -> """
                vec2 uv = vTextureCoord;
                float luma = dot(base.rgb, vec3(0.299, 0.587, 0.114));
                float field = smoothstep(P_A, min(P_A + P_B, 0.99), luma * 0.72 + uv.y * 0.28);
                vec3 shadowTint = mix(base.rgb, P_TINT, P_C * (1.0 - field));
                vec3 effected = mix(shadowTint, base.rgb * (vec3(1.0) + P_TINT * 0.28), field * P_C);
            """
            Family.RIPPLE -> """
                vec2 uv = vTextureCoord;
                float wave = sin(uv.y * P_A * 6.28318 + PHASE * P_C);
                float crossWave = cos(uv.x * (P_A + 1.7) * 6.28318 - PHASE);
                vec2 displaced = uv + vec2(wave, crossWave) * uTexel * P_B * 90.0;
                vec3 sampled = texture2D(uTexSampler, clamp(displaced, 0.001, 0.999)).rgb;
                vec3 effected = mix(sampled, sampled + P_TINT * abs(wave) * 0.12, 0.28);
            """
            Family.MOSAIC -> """
                vec2 pixel = vTextureCoord / uTexel;
                vec2 cell = vec2(P_A, P_B);
                vec2 centerUv = (floor(pixel / cell) + 0.5) * cell * uTexel;
                vec3 blockColor = texture2D(uTexSampler, clamp(centerUv, 0.001, 0.999)).rgb;
                float tileEdge = step(0.94, fract(pixel.x / cell.x)) + step(0.94, fract(pixel.y / cell.y));
                vec3 effected = mix(blockColor, blockColor * (vec3(1.0) + P_TINT * P_C), min(tileEdge, 1.0) * 0.24 + P_C * 0.08);
            """
            Family.CRT -> """
                vec2 uv = vTextureCoord - 0.5;
                float curve = dot(uv, uv) * P_B;
                vec2 bowed = uv * (1.0 + curve) + 0.5;
                vec3 tube = texture2D(uTexSampler, clamp(bowed, 0.001, 0.999)).rgb;
                float scan = 0.84 + 0.16 * sin(vTextureCoord.y / uTexel.y * 3.14159 / max(P_A, 1.0) + PHASE * 8.0);
                float phosphor = 0.90 + 0.10 * sin(vTextureCoord.x / uTexel.x * 0.333);
                vec3 effected = tube * scan * phosphor + P_TINT * P_C * (1.0 - scan);
            """
            Family.HALATION -> """
                vec2 center = vec2(P_A, 0.48);
                float radial = distance(vTextureCoord, center);
                float highlight = max(max(base.r, base.g), base.b);
                float glow = exp(-radial * P_B) * smoothstep(0.35, 0.92, highlight) * P_C;
                vec3 effected = base.rgb + P_TINT * glow;
            """
            Family.INK -> """
                vec2 pixel = vTextureCoord / uTexel;
                float angle = P_A * 0.785398;
                vec2 rotated = mat2(cos(angle), -sin(angle), sin(angle), cos(angle)) * pixel;
                vec2 grid = mod(rotated, P_B);
                float radius = length(grid - 0.5 * P_B) / max(P_B, 1.0);
                float lum = dot(base.rgb, vec3(0.299, 0.587, 0.114));
                float ink = 1.0 - smoothstep(0.08, 0.30, radius - (1.0 - lum) * 0.18);
                vec3 effected = mix(base.rgb * 0.22, P_TINT * (0.35 + lum * 0.65), ink * (0.65 + P_C * 0.35));
            """
            Family.MIRROR -> """
                vec2 centered = vTextureCoord - 0.5;
                float radius = length(centered);
                float angle = atan(centered.y, centered.x) + P_B;
                float wedge = 6.283185 / P_A;
                float folded = mod(angle, wedge);
                folded = mix(folded, wedge - folded, step(wedge * 0.5, folded));
                vec2 mirrored = vec2(cos(folded), sin(folded)) * radius + 0.5;
                vec3 kaleido = texture2D(uTexSampler, clamp(mirrored, 0.001, 0.999)).rgb;
                float facet = 0.94 + 0.06 * cos(folded * P_A);
                vec3 effected = kaleido * facet + P_TINT * P_C * (1.0 - facet);
            """
            Family.GLITCH -> """
                vec2 uv = vTextureCoord;
                float row = floor(uv.y / max(uTexel.y * P_A, 0.00001));
                float noise = fract(sin(row * 127.1 + floor(PHASE * 18.0)) * 43758.5453);
                float gate = step(0.68, noise);
                float offset = (noise - 0.5) * P_B * gate;
                vec3 shifted = texture2D(uTexSampler, clamp(uv + vec2(offset, 0.0), 0.001, 0.999)).rgb;
                vec3 effected = shifted + P_TINT * gate * P_C * 0.32;
            """
            Family.STREAK -> """
                vec2 uv = vTextureCoord - 0.5;
                float angle = P_A + sin(PHASE) * 0.08;
                vec2 direction = vec2(cos(angle), sin(angle));
                vec2 stepUv = direction * uTexel * P_B * 7.0;
                vec3 blur = texture2D(uTexSampler, clamp(vTextureCoord - stepUv * 2.0, 0.001, 0.999)).rgb;
                blur += texture2D(uTexSampler, clamp(vTextureCoord - stepUv, 0.001, 0.999)).rgb;
                blur += base.rgb * 2.0;
                blur += texture2D(uTexSampler, clamp(vTextureCoord + stepUv, 0.001, 0.999)).rgb;
                blur += texture2D(uTexSampler, clamp(vTextureCoord + stepUv * 2.0, 0.001, 0.999)).rgb;
                float radialWeight = 1.0 - smoothstep(0.1, 0.9, length(uv) * P_C);
                vec3 effected = mix(base.rgb, blur * 0.2 + P_TINT * 0.08, radialWeight * 0.72);
            """
        }
        return """
            precision highp float;
            varying vec2 vTextureCoord;
            uniform sampler2D uTexSampler;
            uniform float uIntensity;
            uniform float uTime;
            uniform vec2 uTexel;
            $speedUniform
            const float P_A = @A@;
            const float P_B = @B@;
            const float P_C = @C@;
            const vec3 P_TINT = vec3(@TINT@);
            void main() {
                vec4 base = texture2D(uTexSampler, vTextureCoord);
                float phase = $phase;
                $body
                gl_FragColor = vec4(mix(base.rgb, effected, clamp(uIntensity, 0.0, 1.0)), base.a);
            }
        """.trimIndent()
            .replace("PHASE", "phase")
            .replace("@A@", p.a)
            .replace("@B@", p.b)
            .replace("@C@", p.c)
            .replace("@TINT@", p.tint)
    }
}
