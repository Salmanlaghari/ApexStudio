// Original ApexStudio video effect
// uv: texture coords | time: seconds | tex(uv): sample input

vec3 effect(vec2 uv, float time, sampler2D tex) {
  vec3 col = texture2D(tex, uv).rgb;
  vec2 lp = vec2(0.85, 0.15);
  float d = distance(uv, lp);
  float leak = exp(-d * d * 5.0) * 0.6;
  vec3 leakCol = vec3(1.0, 0.6, 0.3) * leak;
  return col * (1.0 - leak * 0.25) + leakCol;
}