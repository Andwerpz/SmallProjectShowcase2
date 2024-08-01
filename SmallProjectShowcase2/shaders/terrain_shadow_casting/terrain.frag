#version 330 core
layout (location = 0) out vec4 color;

in vec2 frag_uv;

uniform sampler2D tex_terrain_color;

void main() {
	vec3 terrain_color = texture(tex_terrain_color, frag_uv).rgb;
	color = vec4(terrain_color, 1);
}


