#version 440 core
layout (location = 0) out vec4 out_color;
layout (location = 1) out vec4 out_pos;

in vec3 in_frag_dir;
uniform vec3 camera_pos;

uniform sampler2D pos_tex;

struct Particle {
	vec3 pos;
	vec3 vel;
	int hash;
};

layout(binding = 0) buffer particleBuffer {
	Particle[] particleData;
};

layout(binding = 1) buffer hashLUTBuffer {
	int[] hashLUT;
};

uniform int nr_particles;
uniform float target_density;

uniform float smoothing_radius;
uniform int hash_mod;
uniform int LUT_P1;
uniform int LUT_P2;
uniform int LUT_P3;
uniform int LUT_P4;
uniform int LUT_P5;
uniform int LUT_P6;
uniform int LUT_P7;

uniform float density_smoothing_kernel_volume;

int computeHash(int x, int y, int z) {
	x += LUT_P1;
	y += LUT_P2;
	z += LUT_P3;
	return abs(x * LUT_P4 + y * LUT_P5 + z * LUT_P6 + LUT_P7) % hash_mod;
}

int computeHash(vec3 pos) {
	int hash_x = int(pos.x / smoothing_radius);
	int hash_y = int(pos.y / smoothing_radius);
	int hash_z = int(pos.z / smoothing_radius);
	return computeHash(hash_x, hash_y, hash_z);
}

float densitySmoothingKernel(float dist) {
	float tmp = max(0.0, smoothing_radius - dist);
	float ans = pow(tmp, 3.0);
	ans /= density_smoothing_kernel_volume;
	return ans;
}

float densitySmoothingKernelSlope(float dist) {
	return -3.0 * pow(max(0.0, smoothing_radius - dist), 2.0);
}

const int dx[27] = int[27](-1, -1, -1, -1, -1, -1, -1, -1, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1);
const int dy[27] = int[27](-1, -1, -1, 0, 0, 0, 1, 1, 1, -1, -1, -1, 0, 0, 0, 1, 1, 1, -1, -1, -1, 0, 0, 0, 1, 1, 1);
const int dz[27] = int[27](-1, 0, 1, -1, 0, 1, -1, 0, 1, -1, 0, 1, -1, 0, 1, -1, 0, 1, -1, 0, 1, -1, 0, 1, -1, 0, 1);

float computeDensity(vec3 pos) {
	float density = 0;
	int hash_x = int(pos.x / smoothing_radius);
	int hash_y = int(pos.y / smoothing_radius);
	int hash_z = int(pos.z / smoothing_radius);
	for(int i = 0; i < 27; i++){
		int nx = hash_x + dx[i];
		int ny = hash_y + dy[i];
		int nz = hash_z + dz[i];
		int nhash = computeHash(nx, ny, nz);
		int start_ind = hashLUT[nhash];
		for(int j = start_ind; j < nr_particles; j++){
			int chash = particleData[j].hash;
			if(chash != nhash) {
				break;
			}
			vec3 cpos = particleData[j].pos;
			density += densitySmoothingKernel(distance(pos, cpos));
		}
	}
	return density;
}

vec3 computeDensityGradient(vec3 pos) {
	vec3 gradient = vec3(0);
	int hash_x = int(pos.x / smoothing_radius);
	int hash_y = int(pos.y / smoothing_radius);
	int hash_z = int(pos.z / smoothing_radius);
	for(int i = 0; i < 27; i++){
		int nx = hash_x + dx[i];
		int ny = hash_y + dy[i];
		int nz = hash_z + dz[i];
		int nhash = computeHash(nx, ny, nz);
		int start_ind = hashLUT[nhash];
		for(int j = start_ind; j < nr_particles; j++){
			int chash = particleData[j].hash;
			if(chash != nhash) {
				break;
			}
			vec3 cpos = particleData[j].pos;
			float dist = distance(pos, cpos);
			gradient += ((cpos - pos) / dist) * densitySmoothingKernelSlope(dist);
		}
	}
	return gradient;
}

//n1 is ior of current material, n2 is incident material
float fresnelDielectric(vec3 incident, vec3 normal, float n1, float n2) {
	float cos_theta_i = dot(incident, normal);
	float n = n2 / n1;
	
	//incident and normal are facing opposite directions, reverse orientation of normal. 
	if(cos_theta_i < 0){
		normal *= -1;
		cos_theta_i = dot(incident, normal);
		n = n1 / n2;
	}
	
	//compute cos_theta_t
	float sin2_theta_i = max(0.0, 1.0 - cos_theta_i * cos_theta_i);
	float sin2_theta_t = sin2_theta_i / (n * n);
	if(sin2_theta_t >= 1.0){	//handle total internal reflection
		return 1.0;
	}
	float cos_theta_t = sqrt(max(0.0, 1.0 - sin2_theta_t));
	
	//compute ans
	float r_parl = (n * cos_theta_i - cos_theta_t) / (n * cos_theta_i + cos_theta_t);
    float r_perp = (cos_theta_i - n * cos_theta_t) / (cos_theta_i + n * cos_theta_t);
    return (r_parl * r_parl + r_perp * r_perp) / 2.0;
}

//returns true if can transmit
bool refract(vec3 incident, vec3 normal, float n1, float n2, inout vec3 ans) {
	float cos_theta_i = dot(normal, incident);
	float n = n2 / n1;
	
	//potentially flip orientation for snells law
	if(cos_theta_i < 0){
		n = n1 / n2;
		cos_theta_i = -cos_theta_i;
		normal *= -1;
	}
	
	//compute cos_theta_t
	float sin2_theta_i = max(0.0, 1.0 - cos_theta_i * cos_theta_i);
	float sin2_theta_t = sin2_theta_i / (n * n);
	
	//handle total internal reflection
	if(sin2_theta_t >= 1.0){
		return false;
	}
	float cos_theta_t = sqrt(max(0.0, 1.0 - sin2_theta_t));
	
	ans = -incident / n + (dot(incident, normal) / n - cos_theta_t) * normal;
	return true;
}

const int max_iter = 128;
const float water_ior = 2;
const vec3 water_color = vec3(10, 100, 160) * (1.0 / 255.0);
const float water_alpha = 0.75;

void main() {	
	vec3 frag_dir = normalize(in_frag_dir);
	ivec2 frag_coord = ivec2(gl_FragCoord.xy);
	
	vec4 pos_info = texelFetch(pos_tex, frag_coord, 0);
	if(pos_info.w == 0){
		discard;
	}
	
	vec3 pos = pos_info.xyz;
	bool did_hit = false;
	float render_target_density = target_density * 0.5f;
	
	for(int i = 0; i < max_iter; i++){
		float diff = render_target_density - computeDensity(pos);
		if(diff < 0){
			did_hit = true;
			break;
		}
		//pos += frag_dir * min(8, diff / 4.0);
		pos += frag_dir * max(0.1, diff / target_density);
	}
	
	if(did_hit) {
		vec3 normal = normalize(computeDensityGradient(pos));
		out_color = vec4(normal, 1.0);
		out_pos = vec4(0);
		
		/*
		float f = fresnelDielectric(-frag_dir, normal, 1.0, water_ior);
		if(computeDensity(camera_pos) > render_target_density) {
			f = 0;
		}
		
		vec3 reflect_dir = reflect(frag_dir, normal);
		vec3 skybox_color = texture(skybox, reflect_dir).rgb;
		
		float alpha = f + water_alpha - f * water_alpha;
		vec3 color = (skybox_color * f + water_color * water_alpha - water_color * f * water_alpha) / alpha;
		
		out_color = vec4(color, alpha);
		*/
	}
} 

