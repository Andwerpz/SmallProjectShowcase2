#version 330 core
layout (location = 0) out vec4 color;

uniform vec3 camera_pos;
uniform vec3 cloud_pos;
uniform vec3 cloud_scale;

//x : main shape 
//y : detail shape
//z : subtract shape
uniform sampler3D tex_worley_noise;

uniform vec3 sun_dir;	//make sure that this is normalized
uniform vec3 sun_color;

in vec3 frag_dir;

uniform float density_threshold;
uniform float density_multiplier;
uniform float density_offset;

uniform float scale_main_1;
uniform float scale_main_2;
uniform float scale_detail;
uniform float scale_subtract;

uniform float light_absorption_towards_sun;
uniform float darkness_threshold;

uniform float light_absorption_through_clouds;

uniform float forward_scattering;
uniform float backward_scattering;
uniform float base_brightness;
uniform float phase_factor;

float container_edge_fade_dst = 1;

int nr_samples_main = 30;
int nr_samples_luminance = 10;

vec3 cloud_bounds_min = vec3(cloud_pos.x - cloud_scale.x / 2, cloud_pos.y - cloud_scale.y / 2, cloud_pos.z - cloud_scale.z / 2);
vec3 cloud_bounds_max = vec3(cloud_pos.x + cloud_scale.x / 2, cloud_pos.y + cloud_scale.y / 2, cloud_pos.z + cloud_scale.z / 2);

//returns distance to box, and length intersection. 
//if dstInsideBox = 0, then the ray misses the box. 
vec2 rayBoxDist(vec3 ray_origin, vec3 ray_dir) {
	vec3 pos = cloud_pos;
	vec3 scale = cloud_scale;
	
	vec3 t0 = (cloud_bounds_min - ray_origin) / ray_dir;
	vec3 t1 = (cloud_bounds_max - ray_origin) / ray_dir;
	vec3 tmin = min(t0, t1);
	vec3 tmax = max(t0, t1);
	
	float dstA = max(max(tmin.x, tmin.y), tmin.z);
	float dstB = min(min(tmax.x, tmax.y), tmax.z);
	
	float dstToBox = max(0, dstA);
	float dstInsideBox = max(0, dstB - dstToBox);
	return vec2(dstToBox, dstInsideBox);
}

float remap(float v, float minOld, float maxOld, float minNew, float maxNew) {
    return minNew + (v-minOld) * (maxNew - minNew) / (maxOld-minOld);
}

float sampleCloudDensity(vec3 pos) {
	float ans = 0;
	
	vec3 scaled_pos = vec3(pos.x / 0.8, pos.y / 0.7, pos.z / 1);
	
	//main cloud shape
	ans += texture(tex_worley_noise, scaled_pos / scale_main_1).r;
	ans += texture(tex_worley_noise, scaled_pos / scale_main_2).r;
	
	//detail cloud shape
	ans += texture(tex_worley_noise, scaled_pos / scale_detail).g * 0.2;
	
	//subtract from edges
	// Subtract detail noise from base shape (weighted by inverse density so that edges get eroded more than centre)
	float detail_subtract = texture(tex_worley_noise, scaled_pos / scale_subtract).b;
    float one_minus_shape = 1 - ans;
    float detailErodeWeight = one_minus_shape * one_minus_shape * one_minus_shape;
    ans = ans - (1 - detail_subtract) * detailErodeWeight;
	
	//apply density threshold
	ans = max(0, ans - density_threshold);
	
	if(ans == 0){
		return 0;
	}
	
	// Calculate falloff along sides of the cloud container
    float dst_from_edge_x = min(pos.x - cloud_bounds_min.x, cloud_bounds_max.x - pos.x);
    float dst_from_edge_z = min(pos.z - cloud_bounds_min.z, cloud_bounds_max.z - pos.z);
    float dst_from_edge_y = min(pos.y - cloud_bounds_min.y, cloud_bounds_max.y - pos.y);
    float edge_weight = min(1, min(dst_from_edge_y, min(dst_from_edge_z, dst_from_edge_x)) / container_edge_fade_dst);
    ans *= edge_weight;
    
    //calculate gradient falloff from top and bottom of cloud container
    float g_min = .2;
    float g_max = .7;
    float height_percent = (pos.y - cloud_bounds_min.y) / cloud_scale.y;
    float height_gradient = clamp(remap(height_percent, 0.0, g_min, 0, 1), 0, 1) * clamp(remap(height_percent, 1, g_max, 0, 1), 0, 1);
    ans *= height_gradient;
	
	//apply density multiplier
	ans *= density_multiplier;
	
	//apply density offset
	ans += density_offset;
	
	return max(0, ans);
}

float sampleLuminance(vec3 pos) {
	vec3 dir_to_light = sun_dir;
	float dist_inside_box = rayBoxDist(pos, dir_to_light).y;
	
	float step_size = dist_inside_box / nr_samples_luminance;
	float total_density = 0;
	
	for(int i = 0; i < nr_samples_luminance; i++){
		total_density += sampleCloudDensity(pos) * step_size;
		pos += dir_to_light * step_size;
	}
	
	float transmittance = exp(-total_density * light_absorption_towards_sun);
	return darkness_threshold + transmittance * (1 - darkness_threshold);
}

// Henyey-Greenstein
float hg(float a, float g) {
    float g2 = g*g;
    return (1-g2) / (4*3.1415*pow(1+g2-2*g*(a), 1.5));
}

float phase(float a) {
    float blend = 0.5;
    float hgBlend = hg(a, forward_scattering) * (1 - blend) + hg(a, -backward_scattering) * blend;
    return base_brightness + hgBlend * phase_factor;
}

float random (vec2 st) {
    return fract(sin(dot(st.xy,vec2(12.9898,78.233)))*43758.5453123);
}

void main() {	
	vec3 ray_dir = normalize(frag_dir);
	vec2 ray_box = rayBoxDist(camera_pos, ray_dir);
	float ray_box_dist = ray_box.x;
	float ray_box_intersect_dist = ray_box.y;
	if(ray_box_intersect_dist == 0){
		discard;
	}
	
	// Phase function makes clouds brighter around sun
    float cos_angle = dot(ray_dir, sun_dir);
    float phase_val = phase(cos_angle);
	
	//float step_size = ray_box_intersect_dist / nr_samples_main;
	float step_size = 0.2;
	float dist_travelled = random(frag_dir.xy) * step_size;	//initialize with random offset
	
	float transmittance = 1;
	float light_energy = 0;
	
	while(dist_travelled < ray_box_intersect_dist){
		vec3 pos = camera_pos + ray_dir * (ray_box_dist + dist_travelled);
		float density = sampleCloudDensity(pos);
		
		if(density > 0){
			float luminance = sampleLuminance(pos);
			light_energy += density * step_size * transmittance * luminance * phase_val;
			transmittance *= exp(-density * step_size * light_absorption_through_clouds);
		}
		
		//color won't change much if transmittance is very small
		if(transmittance < 0.01){
			break;
		}
		
		dist_travelled += step_size;
	}
	
	vec3 cloud_color = light_energy * sun_color;
	color = vec4(cloud_color, 1.0 - transmittance);
} 

