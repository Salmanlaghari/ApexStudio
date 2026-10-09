// Original ApexStudio transition shader
// progress: 0.0 -> 1.0 | getFromColor(uv) | getToColor(uv)

// params: intensity
vec4 transition(vec2 uv) {
  float inten = 0.9 * sin(progress * 3.14159);
  float n = fract(sin(dot(floor(uv * 60.0), vec2(12.9898, 78.233))) * 43758.5453);
  float burn = smoothstep(1.0 - inten, 1.0, uv.y + n * 0.3 * inten + (uv.x - 0.5) * 0.4 * inten);
  vec4 a = getFromColor(uv);
  vec4 b = getToColor(uv);
  vec3 col = mix(a.rgb, b.rgb, smoothstep(0.35, 0.65, progress));
  vec3 burnCol = mix(vec3(1.0, 0.85, 0.4), vec3(1.0, 0.4, 0.1), n);
  col = mix(col, burnCol, burn);
  return vec4(col, 1.0);
}