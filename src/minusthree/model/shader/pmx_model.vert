#version 330
precision mediump float;

in vec3 POSITION;
in vec3 NORMAL;
in vec2 TEXCOORD;
in vec4 WEIGHTS;
in uvec4 JOINTS;

uniform mat4 u_model;
uniform mat4 u_view;
uniform mat4 u_projection;
layout(std140) uniform Skinning {
    mat4[500] u_joint_mats;
};

out vec3 Normal;
out vec2 TexCoord;

void main() {
    vec4 pos;
    pos = vec4(POSITION, 1.0);
    mat4 skin = (WEIGHTS.x * u_joint_mats[JOINTS.x]) 
                + (WEIGHTS.y * u_joint_mats[JOINTS.y]) 
                + (WEIGHTS.z * u_joint_mats[JOINTS.z])
                + (WEIGHTS.w * u_joint_mats[JOINTS.w]);
    gl_Position = u_projection * u_view * u_model * skin * (pos);
    Normal = NORMAL;
    TexCoord = TEXCOORD;
}
