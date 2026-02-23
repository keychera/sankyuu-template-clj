#version 330
precision mediump float;

// #define MAX_JOINTS 500, our parser cant handle #define yet

in vec3 POSITION;
in vec3 NORMAL;
in vec2 TEXCOORD_0;
in vec4 WEIGHTS_0;
in uvec4 JOINTS_0;

uniform mat4 u_model;
uniform mat4 u_view;
uniform mat4 u_projection;
layout(std140) uniform Skinning {
    mat4[500] u_joint_mats;
};

out vec3 Normal;
out vec2 TexCoord;

void main() {
    vec4 pos = vec4(POSITION, 1.0);
    mat4 skin = (WEIGHTS_0.x * u_joint_mats[JOINTS_0.x]) 
            + (WEIGHTS_0.y * u_joint_mats[JOINTS_0.y]) 
            + (WEIGHTS_0.z * u_joint_mats[JOINTS_0.z])
            + (WEIGHTS_0.w * u_joint_mats[JOINTS_0.w]);
    gl_Position = u_projection * u_view * u_model * skin * pos;
    Normal = NORMAL;
    TexCoord = TEXCOORD_0;
}
