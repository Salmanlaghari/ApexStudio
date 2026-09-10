#version 300 es
// ApexStudio – Passthrough Vertex Shader
// Renders a full-screen triangle; no vertex attributes needed.

in vec4 aPosition;
in vec2 aTexCoord;

out vec2 vTexCoord;

void main() {
    vTexCoord   = aTexCoord;
    gl_Position = aPosition;
}
