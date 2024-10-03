#version 430 core
layout (location = 0) out vec4 out_color;

in vec3 fish_color;

void main() {
    out_color.rgba = vec4(fish_color, 1);   
} 

