// Original ApexStudio transition shader
// progress: 0.0 -> 1.0 | getFromColor(uv) | getToColor(uv)

// params: lines
vec4 transition(vec2 uv) {
  float lines = 120.0;
  float scan = fract(uv.y * lines + progress * lines * 0.5);
  float wipe = smoothstep(0.0, 0.15, uv.y - (1.0 - progress) + (scan * 0.03));
  vec4 a = getFromColor(uv);
  vec4 b = getToColor(uv);
  float glow = smoothstep(0.0, 0.06, abs(uv.y - (1.0 - progress))) ;
  vec3 col = mix(a.rgb, b.rgb, wipe);
  col += vec3(1.0, 1.0, 1.0) * (1.0 - glow) * 0.6 * sin(progress * 3.14159);
  return vec4(col, 1.0);
}