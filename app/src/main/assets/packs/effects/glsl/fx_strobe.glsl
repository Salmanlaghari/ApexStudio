// Original ApexStudio video effect
// uv: texture coords | time: seconds | tex(uv): sample input

vec3 effect(vec2 uv, float time, sampler2D tex) {
  vec3 col = texture2D(tex, uv).rgb;
  float s = step(0.5, fract(time * 6.0));
  return col * mix(0.25, 1.0, s);
}