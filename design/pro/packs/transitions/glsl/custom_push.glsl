// Original ApexStudio transition shader
// progress: 0.0 -> 1.0 | getFromColor(uv) | getToColor(uv)

vec4 transition(vec2 uv) {
  vec2 p = uv;
  p.x += progress;
  vec4 a = getFromColor(fract(vec2(p.x, uv.y)));
  vec4 b = getToColor(fract(vec2(p.x - 1.0, uv.y)));
  return mix(a, b, step(uv.x, progress));
}