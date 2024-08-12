#version 440 core
layout (location = 0) out vec4 out_shadow;

in vec2 frag_uv;

uniform sampler2D tex_height;
uniform sampler2D tex_normal;

uniform int tile_resolution;
uniform float pixel_scale;
uniform int nr_rays;
uniform int ray_no;
uniform vec3 sun_dir;

const float PI = 3.14159265;
const float INV_PI = 1.0 / PI;

uint rngState = uint(gl_FragCoord.x * tile_resolution) * tile_resolution * 13 + uint(gl_FragCoord.y * tile_resolution) * 1203 + ray_no * 1838411;
//https://www.shadertoy.com/view/XlGcRh
float randomValue() {
	rngState = rngState * 747796405 + 2891336453;
	uint result = ((rngState >> ((rngState >> 28u) + 4u)) ^ rngState) * 277803737u;
	result = (result >> 22) ^ result;
	return result / 4294967295.0;
}

vec2 randomPointInCircle() {
	float angle = randomValue() * 2 * 3.1415926;
	vec2 pointOnCircle = vec2(cos(angle), sin(angle));
	pointOnCircle *= sqrt(randomValue());
	return pointOnCircle;
}

//v is relative to normal, and normal is relative to world. Transform v such that is is relative to world. 
vec3 transformToWorld(vec3 a, vec3 normal) {
	//find axis that is not parallel to normal
	vec3 majorAxis;
	if(abs(normal.x) < 0.57735026919) {	//1 / sqrt(3)
		majorAxis = vec3(1, 0, 0);
	}	
	else if(abs(normal.y) < 0.57735026919) {
		majorAxis = vec3(0, 1, 0);
	}
	else {
		majorAxis = vec3(0, 0, 1);
	}	
	
	//use major axis to create coordinate system relative to world space
	vec3 u = normalize(cross(normal, majorAxis));
	vec3 v = cross(normal, u);
	vec3 w = normal;
	
	//transform from local to world coordinates
	return a.x * u + a.y * v + a.z * w;
}

vec3 lambertBRDF_Dir(vec3 normal) {
	float rand = randomValue();
	float r = sqrt(rand);
	float theta = randomValue() * 2.0 * PI;
	
	float x = r * cos(theta);
	float y = r * sin(theta);
	float z = sqrt(1.0 - x * x - y * y);
	
	return normalize(transformToWorld(vec3(x, y, z), normal));
}

float lambertBRDF() {
	return 1.0 / (2.0 * PI);
}

float lambertBRDF_PDF(vec3 normal, vec3 in_dir) {
	return dot(in_dir, normal) * INV_PI;
}

float isOccluded(float height, vec3 dir) {	
	dir = normalize(dir);
	dir.z *= pixel_scale;
	vec3 pos = vec3(gl_FragCoord.xy, height);
	for(int i = 0; i < 65536; i++){
		if(pos.x < 0 || pos.y < 0 || pos.x > tile_resolution || pos.y > tile_resolution) {
			break;
		}
		if(texture2D(tex_height, pos.xy / tile_resolution).r > pos.z + pixel_scale) {
			return 1;
		}
		float tx, ty;
		if(dir.x > 0){
			tx = (floor(pos.x + 1.001) - pos.x) / dir.x;
		}
		else {
			tx = (ceil(pos.x - 1.001) - pos.x) / dir.x;
		}
		if(dir.y > 0){
			ty = (floor(pos.y + 1.001) - pos.y) / dir.y;
		}
		else {
			ty = (ceil(pos.y - 1.001) - pos.y) / dir.y;
		}
		pos += dir * min(tx, ty);
	}
	return 0;
}

const float sun_dist = 149597870700.0;
const float sun_radius = 700064640.0;
const float sun_radius_mult = 10;

void main() {
	float terrain_height = texture2D(tex_height, gl_FragCoord.xy / tile_resolution).r;
	vec3 terrain_normal = texture2D(tex_normal, gl_FragCoord.xy / tile_resolution).rgb;
	terrain_normal = (terrain_normal * 2.0) - 1.0;
	
	vec3 res = vec3(0);
	
	//do shadow
	{	
		vec3 sun_p1 = normalize(cross(sun_dir, vec3(1, 0, 0)));
		vec3 sun_p2 = normalize(cross(sun_dir, sun_p1));
		vec3 c_sun_dir = sun_dir * sun_dist;
		vec2 circle_pt = randomPointInCircle() * sun_radius * sun_radius_mult;
		c_sun_dir += sun_p1 * circle_pt.x + sun_p2 * circle_pt.y;
		c_sun_dir = normalize(c_sun_dir);
		res.x = (1.0 - isOccluded(terrain_height, c_sun_dir)) / nr_rays;
	}
	
	//do ambient occlusion
	{
		vec3 ray_dir = lambertBRDF_Dir(terrain_normal);
		res.y = (1.0 - isOccluded(terrain_height, ray_dir)) / nr_rays;
	}
	
	out_shadow = vec4(res, 1);
}


