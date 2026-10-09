// Original ApexStudio transition shader
// progress: 0.0 -> 1.0 | getFromColor(uv) | getToColor(uv)

// params: intensity
vec4 transition(vec2 uv) {
  float inten = 0.8 * sin(progress * 3.14159);
  float band = floor(uv.y * 24.0);
  float shift = (fract(sin(band * 91.7) * 43758.5) - 0.5) * inten * 0.2;
  vec2 guv = vec2(fract(uv.x + shift), uv.y);
  float slice = step(fract(sin(band * 17.3) * 24634.6), inten * 0.5);
  vec4 a = getFromColor(guv);
  vec4 b = getToColor(guv);
  vec3 rgb_a = vec3(a.r, getFromColor(guv + vec2(0.02 * inten, 0.0)).g, getFromColor(guv - vec2(0.02 * inten, 0.0)).b);
  vec3 rgb_b = vec3(b.r, getToColor(guv + vec2(0.02 * inten, 0.0)).g, getToColor(guv - vec2(0.02 * inten, 0.0)).b);
  vec3 col = mix(rgb_a, rgb_b, smoothstep(0.3, 0.7, progress + (slice - 0.5) * 0.3));
  return vec4(col, 1.0);
}