#version 330
precision mediump float;
 
in vec3 Normal;
in vec2 TexCoord;

uniform sampler2D u_mat_diffuse;

out vec4 o_color;

void main() {
    o_color = vec4(texture(u_mat_diffuse, TexCoord).rgb, 1.0); 
}
