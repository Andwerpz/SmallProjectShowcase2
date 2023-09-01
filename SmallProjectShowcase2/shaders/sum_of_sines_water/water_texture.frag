#version 330 core
layout (location = 0) out vec4 out_height;
layout (location = 1) out vec4 out_normal;

in vec2 frag_uv;

void main() {
	out_height = vec4(frag_uv, 0, 1);
	out_normal = vec4(0, frag_uv, 1);
}


