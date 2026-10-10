// Original ApexStudio video effect
// uv: texture coords | time: seconds | tex(uv): sample input

vec3 effect(vec2 uv, float time, sampler2D tex) {
  vec2 c = uv - 0.5;
  float d = length(c);
  vec2 dir = normalize(c + 0.0001) * d * d * 0.008 * 10.0;
  vec3 col;
  col.r = texture2D(tex, clamp(uv + dir, 0.0, 1.0)).r;
  col.g = texture2D(tex, uv).g;
  col.b = texture2D(tex, clamp(uv - dir, 0.0, 1.0)).b;
  return col;
}