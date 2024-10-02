#version 430 core
layout (location = 0) out vec4 out_color;

in vec3 particle_color;

void main() {
    out_color.rgba = vec4(particle_color, 1);    
} 

