// Original ApexStudio video effect
// uv: texture coords | time: seconds | tex(uv): sample input

vec3 effect(vec2 uv, float time, sampler2D tex) {
  vec2 muv = vec2(abs(uv.x * 2.0 - 1.0) * 0.5, uv.y);
  return texture2D(tex, muv).rgb;
}