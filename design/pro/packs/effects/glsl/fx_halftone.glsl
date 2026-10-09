// Original ApexStudio video effect
// uv: texture coords | time: seconds | tex(uv): sample input

vec3 effect(vec2 uv, float time, sampler2D tex) {
  vec3 col = texture2D(tex, uv).rgb;
  float lum = dot(col, vec3(0.299, 0.587, 0.114));
  vec2 guv = fract(uv * 100.0) - 0.5;
  float dot_ = smoothstep(0.5, 0.4, length(guv) - lum * 0.4);
  return mix(vec3(1.0), col * 1.2, dot_);
}