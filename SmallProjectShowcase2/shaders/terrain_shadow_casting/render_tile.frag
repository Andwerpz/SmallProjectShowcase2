#version 330 core
layout (location = 0) out vec4 out_color;

in vec2 frag_uv;

uniform sampler2D tex_color;

void main() {
	vec3 color = texture2D(tex_color, frag_uv).rgb;
	out_color = vec4(color, 1);
}


