#version 430 core
layout (location = 0) out vec4 out_density;
layout (location = 1) out vec4 out_normal;

struct Particle {
	vec2 pos;
	vec2 pred_pos;
	vec2 vel;
	int hash;
};

layout(binding = 0) buffer particleBuffer {
	Particle[] particleData;
};

layout(binding = 1) buffer hashLUTBuffer {
	int[] hashLUT;
};

in vec2 frag_uv;

uniform int nr_particles;
uniform float smoothing_radius;
uniform int hash_mod;
uniform int LUT_P1;
uniform int LUT_P2;
uniform int LUT_P3;
uniform int LUT_P4;
uniform int LUT_P5;

uniform float window_width;
uniform float window_height;
uniform float render_scale;
uniform vec2 window_bl_pos;

uniform float density_threshold;

uniform float density_smoothing_kernel_volume;
uniform float near_density_smoothing_kernel_volume;

int computeHash(int x, int y) {
	x += LUT_P1;
	y += LUT_P2;
	return abs(x * LUT_P3 + y * LUT_P4 + LUT_P5) % hash_mod;
}

int computeHash(ivec2 h) {
	return computeHash(h.x, h.y);
}

int computeHash(vec2 pos) {
	return computeHash(ivec2(floor(pos / smoothing_radius)));
}

float densitySmoothingKernelSlope(float dist) {
	return -3.0 * pow(max(0.0, smoothing_radius - dist), 2.0);
}

float nearDensitySmoothingKernelSlope(float dist) {
	return -6.0 * pow(max(0.0, smoothing_radius - dist), 5.0);
}

float densitySmoothingKernel(float dist) {
	float tmp = max(0.0, smoothing_radius - dist);
	float ans = pow(tmp, 3.0);
	ans /= density_smoothing_kernel_volume;
	return ans;
}

float nearDensitySmoothingKernel(float dist) {
	float tmp = max(0.0, smoothing_radius - dist);
	float ans = pow(tmp, 6.0);
	ans /= near_density_smoothing_kernel_volume;
	return ans;
}

const int dx[9] = int[9](-1, -1, -1, 0, 0, 0, 1, 1, 1);
const int dy[9] = int[9](-1, 0, 1, -1, 0, 1, -1, 0, 1);

vec2 computeDensityGradient(vec2 pos) {
	vec2 gradient = vec2(0);
	int hash_x = int(pos.x / smoothing_radius);
	int hash_y = int(pos.y / smoothing_radius);
	for(int i = 0; i < 9; i++){
		int nx = hash_x + dx[i];
		int ny = hash_y + dy[i];
		int nhash = computeHash(nx, ny);
		int start_ind = hashLUT[nhash];
		for(int j = start_ind; j < nr_particles; j++){
			int chash = particleData[j].hash;
			if(chash != nhash) {
				break;
			}
			vec2 cpos = particleData[j].pos;
			float dist = distance(pos, cpos);
			gradient += ((cpos - pos) / dist) * densitySmoothingKernelSlope(dist);
		}
	}
	return gradient;
}

vec2 computeDensity(vec2 pos) {
	float density = 0;
	float near_density = 0;
	int hash_x = int(pos.x / smoothing_radius);
	int hash_y = int(pos.y / smoothing_radius);
	for(int i = 0; i < 9; i++){
		int nx = hash_x + dx[i];
		int ny = hash_y + dy[i];
		int nhash = computeHash(nx, ny);
		int start_ind = hashLUT[nhash];
		for(int j = start_ind; j < nr_particles; j++){
			int chash = particleData[j].hash;
			if(chash != nhash) {
				break;
			}
			vec2 cpos = particleData[j].pos;
			density += densitySmoothingKernel(distance(pos, cpos));
			near_density += nearDensitySmoothingKernel(distance(pos, cpos));
		}
	}
	return vec2(density, near_density);
}

vec3 lerp(vec3 x0, vec3 x1, float t0, float t1, float t){
	return mix(x0, x1, (t - t0) / (t1 - t0));
}

void main() {
	vec2 screen_pos = vec2(frag_uv.x * window_width, frag_uv.y * window_height);
	vec2 pos = (screen_pos / render_scale) + window_bl_pos;
	
	vec2 density_vec = computeDensity(pos);
	float density = density_vec.x + density_vec.y;
	
	vec4 result = vec4(0);
	if(density > density_threshold * 0.25) {
		result = vec4(1);
	}
	out_density = result;
	
	//compute normal
	vec3 normal = vec3(0, 0, 1);
	if(density_threshold > density && density > density_threshold * 0.25) {
		vec3 gradient = vec3(normalize(computeDensityGradient(pos)), 0);
		normal = lerp(normal, gradient, density_threshold, density_threshold * 0.25, density);
	}
	normal = normalize(normal);
	normal = (normal * 0.5) + 0.5;
	out_normal = vec4(normal, result.r);
}


