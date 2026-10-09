// Original ApexStudio video effect
// uv: texture coords | time: seconds | tex(uv): sample input

vec3 effect(vec2 uv, float time, sampler2D tex) {
  vec2 c = vec2(0.5 + 0.1 * sin(time), 0.5);
  vec2 d = uv - c;
  float dist = length(d);
  float pull = exp(-dist * dist / (0.3 * 0.3)) * 0.05 * sin(time * 2.0);
  return texture2D(tex, clamp(uv - normalize(d + 0.0001) * pull, 0.0, 1.0)).rgb;
}