// Original ApexStudio video effect
// uv: texture coords | time: seconds | tex(uv): sample input

vec3 effect(vec2 uv, float time, sampler2D tex) {
  vec3 col = texture2D(tex, uv).rgb;
  float flicker = 0.94 + 0.06 * sin(time * 24.0 * 6.28318 + sin(time * 7.0));
  col *= flicker;
  float dust = step(0.997, fract(sin(dot(floor(uv * 200.0) + floor(time * 24.0), vec2(12.9898, 78.233))) * 43758.5453));
  col = mix(col, vec3(1.0), dust * 0.4);
  float scratch_x = abs(fract(uv.x * 3.0 + sin(time) * 0.5) - 0.5);
  float scratch = smoothstep(0.002, 0.0, scratch_x) * step(0.7, fract(sin(floor(time * 24.0) * 12.9) * 4375.85));
  col = mix(col, vec3(0.9), scratch * 0.5);
  col = mix(col, col * vec3(1.05, 0.95, 0.8), 0.4);
  return col;
}