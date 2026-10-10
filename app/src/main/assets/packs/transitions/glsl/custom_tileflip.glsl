// Original ApexStudio transition shader
// progress: 0.0 -> 1.0 | getFromColor(uv) | getToColor(uv)

// params: tiles [6,4]
vec4 transition(vec2 uv) {
  vec2 tiles = vec2(6.0, 4.0);
  vec2 tuv = fract(uv * tiles);
  vec2 tid = floor(uv * tiles);
  float rnd = fract(sin(dot(tid, vec2(12.9898, 78.233))) * 43758.5453);
  float local = clamp((progress - rnd * 0.5) * 2.0, 0.0, 1.0);
  float ang = local * 3.14159;
  vec2 fuv = vec2((tuv.x - 0.5) * cos(ang) + 0.5, tuv.y);
  vec2 guv = (tid + clamp(fuv, 0.0, 1.0)) / tiles;
  return local < 0.5 ? getFromColor(guv) : getToColor(guv);
}