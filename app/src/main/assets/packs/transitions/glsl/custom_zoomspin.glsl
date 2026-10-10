// Original ApexStudio transition shader
// progress: 0.0 -> 1.0 | getFromColor(uv) | getToColor(uv)

// params: spins
vec4 transition(vec2 uv) {
  vec2 c = uv - 0.5;
  float ang = progress * 6.28318 * 1.0;
  float s = mix(1.0, 2.5, sin(progress * 3.14159));
  vec2 ruv = vec2(c.x * cos(ang) - c.y * sin(ang), c.x * sin(ang) + c.y * cos(ang)) / s + 0.5;
  vec4 col = progress < 0.5 ? getFromColor(clamp(ruv, 0.0, 1.0)) : getToColor(clamp(ruv, 0.0, 1.0));
  float edge = smoothstep(0.5, 0.45, length(c));
  return mix(vec4(0.0,0.0,0.0,1.0), col, edge);
}