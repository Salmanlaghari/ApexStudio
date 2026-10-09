// Original ApexStudio video effect
// uv: texture coords | time: seconds | tex(uv): sample input

vec3 effect(vec2 uv, float time, sampler2D tex) {
  float track = sin(time * 2.0) * 0.4;
  float band = smoothstep(0.0, 0.1, abs(uv.y - fract(time * 0.13) - 0.0));
  vec2 tuv = uv + vec2(0.0, track * 0.02 * (1.0 - band));
  vec3 col = texture2D(tex, clamp(tuv, 0.0, 1.0)).rgb;
  float noise = fract(sin(dot(uv * (time + 1.0), vec2(12.9898, 78.233))) * 43758.5453);
  col += (noise - 0.5) * 0.3;
  col = mix(col, col.bgr * vec3(1.0, 0.9, 1.1), (1.0 - band) * 0.5);
  float scan = 0.92 + 0.08 * sin(uv.y * 800.0);
  return col * scan;
}