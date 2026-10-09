// Original ApexStudio transition shader
// progress: 0.0 -> 1.0 | getFromColor(uv) | getToColor(uv)

// params: threshold
vec4 transition(vec2 uv) {
  float n = fract(sin(dot(uv + progress, vec2(12.9898, 78.233))) * 43758.5453);
  float cut = step(n, abs(progress - 0.5) * 2.0);
  vec4 a = getFromColor(uv);
  vec4 b = getToColor(uv);
  vec3 col = mix(a.rgb, b.rgb, step(0.5, progress));
  col = mix(col, vec3(n), cut * 0.7);
  return vec4(col, 1.0);
}