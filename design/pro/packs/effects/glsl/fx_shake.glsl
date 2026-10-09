// Original ApexStudio video effect
// uv: texture coords | time: seconds | tex(uv): sample input

vec3 effect(vec2 uv, float time, sampler2D tex) {
  vec2 off = vec2(sin(time * 8.0 * 6.28318), cos(time * 6.3 * 6.28318)) * 0.02;
  return texture2D(tex, clamp(uv + off, 0.0, 1.0)).rgb;
}