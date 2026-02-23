#version 330
precision mediump float;

in vec3 Normal;
in vec2 TexCoord;
      
out vec4 o_color;

void main() {
    o_color = vec4(0.9, 0.2, 0.2, 0.7);
}
