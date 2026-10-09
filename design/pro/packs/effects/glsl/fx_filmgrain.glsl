// Original ApexStudio video effect
// uv: texture coords | time: seconds | tex(uv): sample input

vec3 effect(vec2 uv, float time, sampler2D tex) {
  vec3 col = texture2D(tex, uv).rgb;
  float g = fract(sin(dot(uv * (time * 60.0 + 1.0), vec2(12.9898, 78.233))) * 43758.5453);
  return col + (g - 0.5) * 0.25;
}