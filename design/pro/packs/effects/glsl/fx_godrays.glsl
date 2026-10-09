// Original ApexStudio video effect
// uv: texture coords | time: seconds | tex(uv): sample input

vec3 effect(vec2 uv, float time, sampler2D tex) {
  vec3 col = texture2D(tex, uv).rgb;
  vec2 lp = vec2(0.5, 0.0);
  vec2 d = uv - lp;
  float dist = length(d);
  float angle = atan(d.y, d.x);
  float rays = 0.5 + 0.5 * sin(angle * 12.0 + time * 0.5);
  float falloff = exp(-dist * 2.5);
  float lum = dot(col, vec3(0.299, 0.587, 0.114));
  return col + vec3(1.0, 0.9, 0.7) * rays * falloff * lum * 0.4;
}