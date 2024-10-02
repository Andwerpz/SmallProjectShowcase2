#version 430 core
layout (location = 0) out vec4 out_color;

in vec2 frag_uv;

uniform float window_width;
uniform float window_height;

uniform sampler2D shadow_map;

void main() {
	vec3 background_color = vec3(0.8);
	
	float ambient = 0.7;
	float direct = 1.0 - texture(shadow_map, frag_uv).r;
	float total = ambient + (1.0 - ambient) * direct;
	
	out_color = vec4(background_color * total, 1);
}


