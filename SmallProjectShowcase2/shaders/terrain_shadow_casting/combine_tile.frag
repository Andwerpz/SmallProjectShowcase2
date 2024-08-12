#version 330 core
layout (location = 0) out vec4 out_color;
layout (location = 1) out vec4 out_rgb_height;

in vec2 frag_uv;

uniform sampler2D tex_color;
uniform sampler2D tex_rgb_height;

void main() {
	out_color = texture2D(tex_color, frag_uv);
	out_rgb_height = texture2D(tex_rgb_height, frag_uv);
}


