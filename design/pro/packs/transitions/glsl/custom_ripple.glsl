// Original ApexStudio transition shader
// progress: 0.0 -> 1.0 | getFromColor(uv) | getToColor(uv)

// params: waves
vec4 transition(vec2 uv) {
  float waves = 8.0;
  vec2 c = uv - 0.5;
  float d = length(c);
  float ripple = sin(d * waves * 6.28318 - progress * 12.0) * 0.02 * sin(progress * 3.14159);
  vec2 ruv = uv + normalize(c + 0.0001) * ripple;
  vec4 a = getFromColor(clamp(ruv, 0.0, 1.0));
  vec4 b = getToColor(clamp(ruv, 0.0, 1.0));
  float ring = smoothstep(progress - 0.2, progress, d * 1.6);
  return mix(a, b, ring);
}