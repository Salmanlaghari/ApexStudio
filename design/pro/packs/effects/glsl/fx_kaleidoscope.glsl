// Original ApexStudio video effect
// uv: texture coords | time: seconds | tex(uv): sample input

vec3 effect(vec2 uv, float time, sampler2D tex) {
  vec2 c = uv - 0.5;
  float r = length(c);
  float a = atan(c.y, c.x);
  float seg = 8.0;
  a = mod(a, 6.28318 / seg);
  a = abs(a - 3.14159 / seg);
  vec2 kuv = vec2(cos(a), sin(a)) * r + 0.5;
  return texture2D(tex, clamp(kuv, 0.0, 1.0)).rgb;
}