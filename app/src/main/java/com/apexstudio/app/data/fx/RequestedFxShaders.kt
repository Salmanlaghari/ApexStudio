package com.apexstudio.app.data.fx

/** Original requested effect shaders, shared by the live Media3 preview and Transformer export. */
internal object RequestedFxShaders {
    fun shaderFor(preset: FxPreset): String? = when (preset) {
        FxPreset.RAIN -> rain()
        FxPreset.CHERRY_BLOSSOM -> cherryBlossom()
        FxPreset.WEDDING_GLOW -> weddingGlow()
        FxPreset.ZOOM_PUNCH -> zoomPunch()
        FxPreset.CRASH_IMPACT -> crashImpact()
        FxPreset.DEFOCUS_BLUR -> defocusBlur()
        else -> null
    }

    private val header = """
        precision mediump float;
        varying vec2 vTextureCoord;
        uniform sampler2D uTexSampler;
        uniform float uIntensity;
        uniform float uTime;
        uniform float uSpeed;
        uniform vec2 uTexel;

        float requestedHash(vec2 p) {
            p = fract(p * vec2(123.34, 456.21));
            p += dot(p, p + 45.32);
            return fract(p.x * p.y);
        }
    """.trimIndent()

    private fun rain() = """
        $header
        void main() {
            vec2 uv = vTextureCoord;
            vec4 source = texture2D(uTexSampler, uv);
            float rain = 0.0;
            for (int layer = 0; layer < 3; layer++) {
                float layerId = float(layer);
                float density = 30.0 + layerId * 17.0;
                float lane = fract(uv.x * density + requestedHash(vec2(floor(uv.x * density), layerId)));
                float seed = requestedHash(vec2(floor(uv.x * density), layerId + 7.0));
                float phase = fract(uv.y * (7.0 + seed * 5.0) + uTime * uSpeed * (0.75 + seed * 1.65) + seed);
                float streak = 1.0 - smoothstep(0.025, 0.075, abs(lane - 0.5));
                float tail = smoothstep(0.06, 0.20, phase) * (1.0 - smoothstep(0.78, 1.0, phase));
                rain = max(rain, streak * tail * (0.34 + 0.18 * layerId));
            }
            vec3 cool = source.rgb * vec3(0.91, 0.97, 1.08);
            vec3 effected = mix(cool, vec3(0.77, 0.88, 1.0), rain * 0.82);
            effected += vec3(0.22, 0.32, 0.44) * rain * 0.14;
            gl_FragColor = vec4(mix(source.rgb, clamp(effected, 0.0, 1.0), uIntensity), source.a);
        }
    """.trimIndent()

    private fun cherryBlossom() = """
        $header
        void main() {
            vec2 uv = vTextureCoord;
            vec4 source = texture2D(uTexSampler, uv);
            vec2 grid = vec2(8.0, 6.0);
            vec2 moving = uv * grid + vec2(0.0, uTime * uSpeed * 0.18);
            vec2 cell = floor(moving);
            vec2 local = fract(moving) - 0.5;
            float seed = requestedHash(cell);
            float phase = uTime * uSpeed * (0.7 + seed * 0.8) + seed * 6.28318;
            local.x += 0.19 * sin(phase);
            local.y += 0.12 * cos(phase * 0.73);
            float petals = 0.0;
            for (int i = 0; i < 5; i++) {
                float angle = 1.256637 * float(i) + seed * 6.28318;
                vec2 center = vec2(cos(angle), sin(angle)) * 0.19;
                vec2 delta = local - center;
                delta.x *= 1.12;
                petals = max(petals, 1.0 - smoothstep(0.105, 0.15, length(delta)));
            }
            float blossomCenter = 1.0 - smoothstep(0.035, 0.065, length(local));
            float visible = (petals * 0.78 + blossomCenter * 0.55) * step(0.20, seed);
            vec3 petalColor = mix(vec3(1.0, 0.72, 0.84), vec3(1.0, 0.93, 0.96), seed);
            vec3 effected = mix(source.rgb, petalColor, clamp(visible, 0.0, 0.90));
            effected = mix(effected, effected * vec3(1.025, 1.0, 1.015), 0.20);
            gl_FragColor = vec4(mix(source.rgb, effected, uIntensity), source.a);
        }
    """.trimIndent()

    private fun weddingGlow() = """
        $header
        void main() {
            vec2 uv = vTextureCoord;
            vec4 source = texture2D(uTexSampler, uv);
            vec2 tex = uTexel * (2.0 + 4.0 * uIntensity);
            vec3 soft = source.rgb * 0.40;
            soft += texture2D(uTexSampler, clamp(uv + vec2(tex.x, 0.0), 0.0, 1.0)).rgb * 0.15;
            soft += texture2D(uTexSampler, clamp(uv - vec2(tex.x, 0.0), 0.0, 1.0)).rgb * 0.15;
            soft += texture2D(uTexSampler, clamp(uv + vec2(0.0, tex.y), 0.0, 1.0)).rgb * 0.15;
            soft += texture2D(uTexSampler, clamp(uv - vec2(0.0, tex.y), 0.0, 1.0)).rgb * 0.15;
            vec2 cell = floor(uv * vec2(8.0, 5.0));
            vec2 local = fract(uv * vec2(8.0, 5.0)) - 0.5;
            float seed = requestedHash(cell);
            float drift = uTime * uSpeed * (0.3 + seed * 0.3);
            float radius = 0.075 + 0.035 * seed;
            float bokeh = 1.0 - smoothstep(radius * 0.55, radius, length(local + vec2(0.12 * sin(drift + seed * 6.0), 0.12 * cos(drift))));
            bokeh *= step(0.70, seed);
            float luminance = dot(source.rgb, vec3(0.299, 0.587, 0.114));
            float glow = smoothstep(0.45, 0.95, luminance) * 0.22;
            vec3 warm = source.rgb * vec3(1.055, 1.015, 0.94) + vec3(0.025, 0.012, 0.006);
            vec3 effected = mix(warm, soft * vec3(1.08, 1.02, 0.92), 0.20 + glow);
            effected += vec3(1.0, 0.78, 0.42) * bokeh * 0.28;
            gl_FragColor = vec4(mix(source.rgb, clamp(effected, 0.0, 1.0), uIntensity), source.a);
        }
    """.trimIndent()

    private fun zoomPunch() = """
        $header
        void main() {
            vec2 fromCenter = vTextureCoord - vec2(0.5);
            float pulse = pow(0.5 + 0.5 * sin(uTime * uSpeed * 4.2), 8.0);
            float zoom = 1.0 + pulse * 0.18 * uIntensity;
            vec2 uv = vec2(0.5) + fromCenter / zoom;
            vec3 center = texture2D(uTexSampler, clamp(uv, 0.001, 0.999)).rgb;
            vec2 blur = fromCenter * uTexel * (5.0 + pulse * 24.0) * uIntensity;
            vec3 outward = texture2D(uTexSampler, clamp(uv + blur, 0.001, 0.999)).rgb;
            vec3 inward = texture2D(uTexSampler, clamp(uv - blur, 0.001, 0.999)).rgb;
            vec3 effected = mix(center, (center * 2.0 + outward + inward) * 0.25, pulse * 0.58);
            gl_FragColor = vec4(mix(texture2D(uTexSampler, vTextureCoord).rgb, effected, uIntensity), 1.0);
        }
    """.trimIndent()

    private fun crashImpact() = """
        $header
        void main() {
            vec2 uv = vTextureCoord;
            float beat = fract(uTime * uSpeed * 1.15);
            float impact = exp(-pow(abs(beat - 0.08) * 18.0, 2.0));
            float shake = impact * uIntensity;
            vec2 direction = uv - vec2(0.5);
            vec2 offset = vec2(sin(uTime * uSpeed * 71.0), cos(uTime * uSpeed * 59.0))
                * uTexel * (2.0 + 13.0 * shake) * shake;
            vec2 zoomed = vec2(0.5) + direction * (1.0 + 0.12 * shake) + offset;
            float chroma = uTexel.x * (2.0 + 9.0 * shake) * shake;
            vec4 base = texture2D(uTexSampler, clamp(zoomed, 0.001, 0.999));
            float red = texture2D(uTexSampler, clamp(zoomed + vec2(chroma, 0.0), 0.001, 0.999)).r;
            float blue = texture2D(uTexSampler, clamp(zoomed - vec2(chroma, 0.0), 0.001, 0.999)).b;
            vec3 effected = vec3(red, base.g, blue) + vec3(1.0, 0.72, 0.42) * impact * 0.24;
            vec3 original = texture2D(uTexSampler, uv).rgb;
            gl_FragColor = vec4(mix(original, clamp(effected, 0.0, 1.0), uIntensity), base.a);
        }
    """.trimIndent()

    private fun defocusBlur() = """
        $header
        void main() {
            vec2 uv = vTextureCoord;
            vec2 radius = uTexel * (2.0 + 8.0 * uIntensity);
            vec3 color = texture2D(uTexSampler, uv).rgb * 0.28;
            color += texture2D(uTexSampler, clamp(uv + vec2(radius.x, 0.0), 0.0, 1.0)).rgb * 0.12;
            color += texture2D(uTexSampler, clamp(uv - vec2(radius.x, 0.0), 0.0, 1.0)).rgb * 0.12;
            color += texture2D(uTexSampler, clamp(uv + vec2(0.0, radius.y), 0.0, 1.0)).rgb * 0.12;
            color += texture2D(uTexSampler, clamp(uv - vec2(0.0, radius.y), 0.0, 1.0)).rgb * 0.12;
            color += texture2D(uTexSampler, clamp(uv + radius, 0.0, 1.0)).rgb * 0.06;
            color += texture2D(uTexSampler, clamp(uv - radius, 0.0, 1.0)).rgb * 0.06;
            color += texture2D(uTexSampler, clamp(uv + vec2(radius.x, -radius.y), 0.0, 1.0)).rgb * 0.06;
            color += texture2D(uTexSampler, clamp(uv + vec2(-radius.x, radius.y), 0.0, 1.0)).rgb * 0.06;
            vec3 original = texture2D(uTexSampler, uv).rgb;
            gl_FragColor = vec4(mix(original, color, uIntensity), 1.0);
        }
    """.trimIndent()
}
