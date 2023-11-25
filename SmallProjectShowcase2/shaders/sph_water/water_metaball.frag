#version 430 core
layout (location = 0) out vec4 color;

layout(rgba32f, binding = 0) uniform image1D predictedPosLUTTexture;

in vec2 frag_uv;

uniform int nr_particles;
uniform float smoothing_radius;
uniform int LUT_P1;
uniform int LUT_P2;
uniform int LUT_P3;
uniform int LUT_P4;
uniform int LUT_P5;

uniform float window_width;
uniform float window_height;
uniform float water_position_scale;

uniform float density_threshold;

uniform float water_mass;
uniform float density_smoothing_kernel_volume;
uniform float near_density_smoothing_kernel_volume;

int computeHash(int x, int y) {
	x += LUT_P4;
	y += LUT_P5;
	return abs(x * LUT_P1 + y * LUT_P2 + LUT_P3) % nr_particles;
}

int computeHash(vec2 pos) {
	int hash_x = int(pos.x / smoothing_radius);
	int hash_y = int(pos.y / smoothing_radius);
	return computeHash(hash_x, hash_y);
}

float densitySmoothingKernel(float dist) {
	float tmp = max(0.0, smoothing_radius - dist);
	float ans = pow(tmp, 3.0) * water_mass;
	ans /= density_smoothing_kernel_volume;
	return ans;
}

float nearDensitySmoothingKernel(float dist) {
	float tmp = max(0.0, smoothing_radius - dist);
	float ans = pow(tmp, 6.0) * water_mass;
	ans /= near_density_smoothing_kernel_volume;
	return ans;
}

const int dx[9] = int[9](-1, -1, -1, 0, 0, 0, 1, 1, 1);
const int dy[9] = int[9](-1, 0, 1, -1, 0, 1, -1, 0, 1);

vec2 computeDensity(vec2 pos) {
	float density = 0;
	float near_density = 0;
	int hash_x = int(pos.x / smoothing_radius);
	int hash_y = int(pos.y / smoothing_radius);
	for(int i = 0; i < 9; i++){
		int nx = hash_x + dx[i];
		int ny = hash_y + dy[i];
		int nhash = computeHash(nx, ny);
		int start_ind = int(imageLoad(predictedPosLUTTexture, nhash).w);
		for(int j = start_ind; j < nr_particles; j++){
			vec4 lut = imageLoad(predictedPosLUTTexture, j);
			vec2 cpos = lut.xy;
			int chash = int(lut.z);
			if(chash != nhash) {
				break;
			}
			density += densitySmoothingKernel(distance(pos, cpos));
			near_density += nearDensitySmoothingKernel(distance(pos, cpos));
		}
	}
	return vec2(density, near_density);
}

void main() {
	vec2 screen_pos = vec2(frag_uv.x * window_width, frag_uv.y * window_height);
	vec2 pos = screen_pos / water_position_scale;
	
	vec2 density_vec = computeDensity(pos);
	float density = density_vec.x + density_vec.y;
	
	vec3 water_color = vec3(14.0, 125.0, 204.0) / 255.0;
	vec3 water_color_light = water_color * 1.1;
	
	vec4 result = vec4(0);
	if(density > density_threshold) {
		result = vec4(water_color, 1);
	}
	else if(density > density_threshold * 0.60) {
		result = vec4(water_color_light, 1);
	}
	
	color = result;
}


