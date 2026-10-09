// Original ApexStudio transition shader
// progress: 0.0 -> 1.0 | getFromColor(uv) | getToColor(uv)

// params: color [1.0, 0.6, 0.3]
vec4 transition(vec2 uv) {
  float amt = sin(progress * 3.14159);
  vec2 leakPos = vec2(1.0 - progress * 0.6, 0.15);
  float d = distance(uv, leakPos);
  float leak = exp(-d * d * 6.0) * amt;
  vec3 leakCol = vec3(1.0, 0.55, 0.25) * leak * 1.2;
  vec4 a = getFromColor(uv);
  vec4 b = getToColor(uv);
  vec3 col = mix(a.rgb, b.rgb, smoothstep(0.3, 0.7, progress));
  col = col * (1.0 - leak * 0.3) + leakCol;
  return vec4(col, 1.0);
}