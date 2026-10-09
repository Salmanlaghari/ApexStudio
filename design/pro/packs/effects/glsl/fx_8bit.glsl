// Original ApexStudio video effect
// uv: texture coords | time: seconds | tex(uv): sample input

vec3 effect(vec2 uv, float time, sampler2D tex) {
  vec2 puv = floor(uv * 64.0) / 64.0;
  vec3 col = texture2D(tex, puv).rgb;
  col = floor(col * 16.0) / 16.0;
  return col;
}