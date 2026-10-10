// Original ApexStudio video effect
// uv: texture coords | time: seconds | tex(uv): sample input

vec3 effect(vec2 uv, float time, sampler2D tex) {
  float band = floor(uv.y * 20.0 + time * 3.0);
  float shift = (fract(sin(band * 91.7 + time * 10.0) * 43758.5) - 0.5) * 0.7 * 0.15;
  vec2 guv = vec2(fract(uv.x + shift * step(0.7, fract(sin(band * 13.7) * 24634.6))), uv.y);
  vec3 col;
  col.r = texture2D(tex, guv + vec2(0.015, 0.0)).r;
  col.g = texture2D(tex, guv).g;
  col.b = texture2D(tex, guv - vec2(0.015, 0.0)).b;
  return col;
}