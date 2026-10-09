// Original ApexStudio transition shader
// progress: 0.0 -> 1.0 | getFromColor(uv) | getToColor(uv)

// params: blocks
vec4 transition(vec2 uv) {
  float blocks = 24.0;
  vec2 buv = floor(uv * blocks) / blocks;
  float rnd = fract(sin(dot(buv + floor(progress * 8.0), vec2(12.9898, 78.233))) * 43758.5453);
  vec2 off = (vec2(rnd, fract(rnd * 7.0)) - 0.5) * 0.15 * sin(progress * 3.14159);
  vec2 muv = clamp(uv + off, 0.0, 1.0);
  return mix(getFromColor(muv), getToColor(muv), smoothstep(0.35, 0.65, progress));
}