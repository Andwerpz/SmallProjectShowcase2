#version 430 core
layout (location = 0) out vec4 out_shadow;

in vec2 frag_uv;

uniform float window_width;
uniform float window_height;

uniform sampler2D density_map;
uniform sampler2D obstacle_map;

const vec2 light_dir = normalize(vec2(0.5, 1));
const float step_size = 2;
const float max_shadow_len = 100;

vec3 lerp(vec3 x0, vec3 x1, float t0, float t1, float t){
	return mix(x0, x1, (t - t0) / (t1 - t0));
}

float sampleDensity(vec2 pos) {
	if(pos.x < 0 || pos.y < 0 || pos.x > window_width || pos.y > window_height) {
		return 0;
	}
	float water_density = texture(density_map, vec2(pos.x / window_width, pos.y / window_height)).x;
	float obstacle_density = texture(obstacle_map, vec2(pos.x / window_width, pos.y / window_height)).x;
	return max(water_density, obstacle_density);
}

void main() {
	vec2 pos = vec2(frag_uv.x * window_width, frag_uv.y * window_height);

	vec4 result = vec4(0);
	for(float ptr = 0; ptr < max_shadow_len; ptr += step_size){
		if(sampleDensity(pos + light_dir * ptr) != 0) {
			float shadow = (max_shadow_len - ptr) / max_shadow_len;
			shadow *= shadow;
			result = vec4(vec3(shadow), 1);
			break;
		}
	}
	
	out_shadow = result;
}


