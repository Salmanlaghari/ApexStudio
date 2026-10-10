// Original ApexStudio transition shader
// progress: 0.0 -> 1.0 | getFromColor(uv) | getToColor(uv)

vec4 transition(vec2 uv) {
  float ang = progress * 3.14159;
  float y = uv.y - 0.5;
  float z = abs(sin(ang)) * 0.5;
  float py = y * cos(ang) / (1.0 + z);
  vec2 suv = vec2(uv.x, py + 0.5);
  if (suv.y < 0.0 || suv.y > 1.0) return vec4(0.0, 0.0, 0.0, 1.0);
  return progress < 0.5 ? getFromColor(suv) : getToColor(suv);
}