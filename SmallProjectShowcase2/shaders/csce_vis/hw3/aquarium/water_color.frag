#version 430 core
layout (location = 0) out vec4 out_color;

in vec2 frag_uv;

uniform float window_width;
uniform float window_height;

uniform sampler2D density_map;
uniform sampler2D normal_map;

const vec2 light_dir = normalize(vec2(0.5, 1));
const vec3 light_dir_3D = normalize(vec3(light_dir, 1));
const float step_size = 2;

vec3 lerp(vec3 x0, vec3 x1, float t0, float t1, float t){
	return mix(x0, x1, (t - t0) / (t1 - t0));
}

float sampleDensity(vec2 pos) {
	if(pos.x < 0 || pos.y < 0 || pos.x > window_width || pos.y > window_height) {
		return 0;
	}
	return texture(density_map, vec2(pos.x / window_width, pos.y / window_height)).x;
}

void main() {
	vec2 pos = vec2(frag_uv.x * window_width, frag_uv.y * window_height);
	vec3 water_color = vec3(14.0, 100.0, 150.0) / 255.0;
	vec3 normal = (texture(normal_map, frag_uv).rgb - 0.5) * 2;
	
	if(sampleDensity(pos) == 0) {
		discard;
	}	

	//go until we hit a region with no density, or go outside of the screen
	float transmittance = dot(normal, light_dir_3D);
	vec3 result = water_color;
	while(transmittance > 0.01) {
		pos += light_dir * step_size;
		float density = sampleDensity(pos);
		if(density < 1){
			break;
		}
		transmittance *= 0.95;
	}
	
	float ambient = 0.4;
	float total = ambient + (1.0 - ambient) * transmittance;
	
	out_color = vec4(total * water_color, 1);
}


