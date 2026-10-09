// Original ApexStudio transition shader
// progress: 0.0 -> 1.0 | getFromColor(uv) | getToColor(uv)

// params: strokes
vec4 transition(vec2 uv) {
  float strokes = 30.0;
  vec2 suv = vec2(uv.x, 1.0 - uv.y);
  float sy = floor(suv.y * strokes) / strokes;
  float rnd = fract(sin(sy * 91.7) * 43758.5);
  float edge = rnd * 0.5 + progress * 0.9 - 0.25;
  float t = smoothstep(edge - 0.06, edge + 0.06, suv.y);
  vec2 juv = uv + vec2((rnd - 0.5) * 0.02, 0.0);
  return mix(getFromColor(clamp(juv, 0.0, 1.0)), getToColor(clamp(juv, 0.0, 1.0)), t);
}