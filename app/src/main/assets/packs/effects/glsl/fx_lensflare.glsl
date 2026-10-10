// Original ApexStudio video effect
// uv: texture coords | time: seconds | tex(uv): sample input

vec3 effect(vec2 uv, float time, sampler2D tex) {
  vec3 col = texture2D(tex, uv).rgb;
  vec2 fp = vec2(0.7, 0.3);
  vec2 d = uv - fp;
  float dist = length(d);
  float flare = exp(-dist * dist * 40.0);
  float ring = exp(-pow((dist - 0.25) * 18.0, 2.0)) * 0.5;
  float streak = exp(-pow(d.y * 60.0, 2.0)) * exp(-abs(d.x) * 4.0) * 0.6;
  vec3 fcol = vec3(1.0, 0.85, 0.6) * (flare + ring + streak) * 0.7;
  return col + fcol;
}