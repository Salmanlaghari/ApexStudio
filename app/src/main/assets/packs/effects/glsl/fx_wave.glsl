// Original ApexStudio video effect
// uv: texture coords | time: seconds | tex(uv): sample input

vec3 effect(vec2 uv, float time, sampler2D tex) {
  float w = sin(uv.y * 12.0 * 6.28318 + time * 2.0 * 6.28318) * 0.03;
  return texture2D(tex, clamp(uv + vec2(w, 0.0), 0.0, 1.0)).rgb;
}