#version 430 core
layout (location = 0) out vec4 out_color;

in vec3 frag_pos;
in vec3 frag_vel;
in vec3 frag_hue;

void main() {
    out_color.rgba = vec4(frag_hue, 1);
} 

