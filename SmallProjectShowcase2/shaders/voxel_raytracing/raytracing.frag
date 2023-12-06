#version 440 core
layout (location = 0) out vec4 tex_color;

uniform samplerCube skybox_tex;
uniform vec3 camera_pos;

uniform vec3 svo_offset;	//offset of minimum (x, y, z)
uniform int svo_size;	//x, y, z dimensions

in vec3 frag_dir;

struct Ray {
	vec3 origin;
	vec3 dir;
};

vec3 traceRay(Ray ray) {
	//translate ray into svo space
	ray.origin -= svo_offset;
	
	//check if ray will collide with svo at all
	bool collides = false;
	
	//see if ray origin is inside svo
	if(ray.origin.x > 0 && ray.origin.y > 0 && ray.origin.z > 0 && ray.origin.x < svo_size && ray.origin.y < svo_size && ray.origin.z < svo_size) {
		collides = true;
	}
	else {
		//check to see if ray will collide with any of the faces of the svo
		float min_bounds[3] = float[](0, 0, 0);
		float max_bounds[3] = float[](svo_offset.x + svo_size, svo_offset.y + svo_size, svo_offset.z + svo_size);
		float dir_component[3] = float[](ray.dir.x, ray.dir.y, ray.dir.z);
		float pos_component[3] = float[](ray.origin.x, ray.origin.y, ray.origin.z);
		for(int i = 0; i < 3; i++){
			if(abs(dir_component[i]) <= 0.00000001) {
				continue;
			}
			float tgt = min_bounds[i];
			if(dir_component[i] < 0){
				tgt = max_bounds[i];
			}
			float dist = tgt - pos_component[i];
			float ray_mul = dist / dir_component[i];
			if(ray_mul < 0){
				continue;
			}
			ray_mul += 0.001;
			vec3 test_pos = ray.origin + ray.dir * ray_mul;
			if(test_pos.x > 0 && test_pos.y > 0 && test_pos.z > 0 && test_pos.x < svo_size && test_pos.y < svo_size && test_pos.z < svo_size) {
				collides = true;
				ray.origin = test_pos;
				break;
			}
		}
	}
	
	vec3 result = texture(skybox_tex, ray.dir).rgb;
	if(!collides) {
		return result;
	}
	
	//traverse through svo and see what color we get
	result = ray.origin / float(svo_size);
	
	return result;
}

void main() {   
	Ray ray = Ray(camera_pos, normalize(frag_dir));
	vec3 color = traceRay(ray);
	
	vec4 result = vec4(color, 1);
	tex_color.rgba = result;
} 

