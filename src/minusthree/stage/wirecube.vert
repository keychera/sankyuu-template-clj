#version 330
precision mediump float;

in vec3 POSITION;
in vec3 NORMAL;
in vec2 TEXCOORD_0;

uniform mat4 u_model;
uniform mat4 u_view;
uniform mat4 u_projection;

out vec3 Normal;
out vec2 TexCoord;

void main() {
    gl_Position = u_projection * u_view * u_model * vec4(POSITION, 1.0);
    Normal = NORMAL;
    TexCoord = TEXCOORD_0;
}
