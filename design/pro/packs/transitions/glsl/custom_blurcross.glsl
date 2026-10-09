// Original ApexStudio transition shader
// progress: 0.0 -> 1.0 | getFromColor(uv) | getToColor(uv)

// params: maxBlur
vec4 transition(vec2 uv) {
  float amt = sin(progress * 3.14159) * 12.0;
  vec3 acc = vec3(0.0);
  float total = 0.0;
  for (float i = -4.0; i <= 4.0; i += 1.0) {
    float w = 1.0 - abs(i / 5.0);
    vec2 off = vec2(i * amt * 0.002, 0.0);
    vec3 sa = getFromColor(clamp(uv + off, 0.0, 1.0)).rgb;
    vec3 sb = getToColor(clamp(uv + off, 0.0, 1.0)).rgb;
    acc += mix(sa, sb, smoothstep(0.35, 0.65, progress)) * w;
    total += w;
  }
  return vec4(acc / total, 1.0);
}