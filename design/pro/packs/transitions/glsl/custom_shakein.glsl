// Original ApexStudio transition shader
// progress: 0.0 -> 1.0 | getFromColor(uv) | getToColor(uv)

// params: amplitude
vec4 transition(vec2 uv) {
  float amp = 0.03 * (1.0 - progress);
  float t = progress * 40.0;
  vec2 off = vec2(sin(t * 1.3), cos(t * 1.7)) * amp;
  vec2 zuv = (uv - 0.5) * (1.0 + progress * 0.3) + 0.5;
  vec4 a = getFromColor(clamp(zuv + off, 0.0, 1.0));
  vec4 b = getToColor(clamp(zuv + off, 0.0, 1.0));
  return mix(a, b, smoothstep(0.4, 0.6, progress));
}