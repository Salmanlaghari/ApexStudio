// Original ApexStudio video effect
// uv: texture coords | time: seconds | tex(uv): sample input

vec3 effect(vec2 uv, float time, sampler2D tex) {
  vec3 col = texture2D(tex, uv).rgb;
  vec2 c = uv - 0.5;
  float v = smoothstep(0.75, 0.25, length(c) * 1.4);
  return col * mix(1.0 - 0.5, 1.0, v);
}