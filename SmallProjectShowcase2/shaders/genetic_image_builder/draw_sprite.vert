#version 430 core

layout (location = 0) in vec3 pos;
uniform mat4 md_matrix;
uniform vec4 hue;
uniform vec4 uv;

uniform mat4 pr_matrix;

out vec2 frag_pos;
out vec2 frag_uv;
out vec4 frag_hue;

void main() {
    gl_Position = pr_matrix * md_matrix * vec4(pos, 1.0);
    frag_pos = vec2(md_matrix * vec4(pos, 1.0));
    frag_hue = hue;
    
    //uv depends on vertex location
    frag_uv = vec2(0);
    frag_uv.x = pos.x == 0.0? uv.x : uv.z;
    frag_uv.y = pos.y == 0.0? uv.y : uv.w;
}