// Original ApexStudio transition shader
// progress: 0.0 -> 1.0 | getFromColor(uv) | getToColor(uv)

vec4 transition(vec2 uv) {
  float n = fract(sin(dot(uv * (progress * 40.0 + 10.0), vec2(12.9898, 78.233))) * 43758.5453);
  float t = smoothstep(progress - 0.2, progress + 0.2, n);
  return mix(getFromColor(uv), getToColor(uv), t);
}