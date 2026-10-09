// Original ApexStudio transition shader
// progress: 0.0 -> 1.0 | getFromColor(uv) | getToColor(uv)

// params: radius
vec4 transition(vec2 uv) {
  float r = 0.3;
  vec2 c = vec2(1.0 - progress, 1.0 - progress);
  float d = distance(uv, c);
  if (d < r * progress * 2.0) {
    vec2 cuv = uv + normalize(c - uv) * (r * progress * 2.0 - d) * 1.5;
    return getToColor(clamp(cuv, 0.0, 1.0));
  }
  return getFromColor(uv);
}