// Original ApexStudio transition shader
// progress: 0.0 -> 1.0 | getFromColor(uv) | getToColor(uv)

// params: distance
vec4 transition(vec2 uv) {
  float d = 0.05 * sin(progress * 3.14159);
  vec2 dir = vec2(1.0, 0.0);
  vec4 a = progress < 0.5 ? getFromColor(uv) : getToColor(uv);
  vec4 b = progress < 0.5 ? getFromColor(uv) : getToColor(uv);
  vec3 col;
  col.r = (progress < 0.5 ? getFromColor(uv + dir * d) : getToColor(uv + dir * d)).r;
  col.g = a.g;
  col.b = (progress < 0.5 ? getFromColor(uv - dir * d) : getToColor(uv - dir * d)).b;
  float m = smoothstep(0.4, 0.6, progress);
  vec3 cola = vec3((progress < 0.5 ? getFromColor(uv + dir*d) : getFromColor(uv + dir*d)).r, 0.0, 0.0);
  return vec4(mix(a.rgb, col, sin(progress*3.14159)), 1.0);
}