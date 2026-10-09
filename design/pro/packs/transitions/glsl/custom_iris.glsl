// Original ApexStudio transition shader
// progress: 0.0 -> 1.0 | getFromColor(uv) | getToColor(uv)

vec4 transition(vec2 uv) {
  vec2 c = uv - 0.5;
  float d = length(c) * 1.5;
  float t = smoothstep(progress - 0.15, progress + 0.15, d);
  return mix(getToColor(uv), getFromColor(uv), t);
}