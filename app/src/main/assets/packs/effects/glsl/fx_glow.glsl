// Original ApexStudio video effect
// uv: texture coords | time: seconds | tex(uv): sample input

vec3 effect(vec2 uv, float time, sampler2D tex) {
  vec3 col = texture2D(tex, uv).rgb;
  float lum = dot(col, vec3(0.299, 0.587, 0.114));
  float mask = smoothstep(0.7, 0.95, lum);
  vec3 blur = vec3(0.0);
  for (float i = -2.0; i <= 2.0; i += 1.0)
    for (float j = -2.0; j <= 2.0; j += 1.0)
      blur += texture2D(tex, clamp(uv + vec2(i, j) * 0.008, 0.0, 1.0)).rgb;
  blur /= 25.0;
  return col + blur * mask * 0.8;
}