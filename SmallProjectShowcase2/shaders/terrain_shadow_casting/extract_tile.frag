#version 330 core
layout (location = 0) out vec4 out_color;

in vec2 frag_uv;

uniform sampler2D tex_color;
uniform sampler2D tex_normal;
uniform sampler2D tex_shadow;

uniform int tile_resolution;
uniform vec3 sun_dir;

void main() {
	vec2 uv = (gl_FragCoord.xy + tile_resolution) / (tile_resolution * 3);
	vec3 terrain_color = texture2D(tex_color, uv).rgb;
	vec3 terrain_normal = texture2D(tex_normal, uv).rgb;	
	terrain_normal = (terrain_normal * 2.0) - 1.0;
	float shadow = texture2D(tex_shadow, uv).r;
	float ambient = texture2D(tex_shadow, uv).g;
	
	float direct = dot(terrain_normal, sun_dir);
	//direct = direct * 0.5 + 0.5;
	direct = max(0, direct);
	
	float total = direct * shadow + ambient * 0.5;
	total = min(1, total);
	out_color = vec4(terrain_color * total, 1);
}


