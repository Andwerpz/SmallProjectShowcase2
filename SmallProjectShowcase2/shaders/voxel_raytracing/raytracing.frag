#version 430 core
layout (location = 0) out vec4 tex_color;

uniform samplerCube skybox_tex;
uniform vec3 camera_pos;
uniform vec3 sun_dir;	//direction towards the sun

layout(std430, binding = 1) buffer svo_ssbo
{
    int svo_data[]; 
};

in vec3 frag_dir;

struct Ray {
	vec3 origin;
	vec3 dir;
};

struct HitInfo {
	bool did_hit;
	vec3 pos;
	vec3 normal;
	vec3 color;
};

int computeChildInd(Ray ray, vec3 parent_bl_offset, int parent_size) {
	float child_size = float(parent_size) / 2.0;
	int child_ind = 0;
	child_ind += (ray.origin.x - parent_bl_offset.x > child_size) ? (1 << 0) : 0;
	child_ind += (ray.origin.y - parent_bl_offset.y > child_size) ? (1 << 1) : 0;
	child_ind += (ray.origin.z - parent_bl_offset.z > child_size) ? (1 << 2) : 0;
	return child_ind;
}

bool pointInsideAABB(vec3 pt, vec3 AABB_bl, int AABB_size, float epsilon) {
	pt -= AABB_bl;
	bool ans = true;
	ans = ans && pt.x + epsilon > 0;
	ans = ans && pt.y + epsilon > 0;
	ans = ans && pt.z + epsilon > 0;
	ans = ans && pt.x - epsilon < AABB_size;
	ans = ans && pt.y - epsilon < AABB_size;
	ans = ans && pt.z - epsilon < AABB_size;
	return ans;
}

bool pointInsideAABB(vec3 pt, vec3 AABB_bl, int AABB_size) {
	return pointInsideAABB(pt, AABB_bl, AABB_size, 0);
}

//returns the length the ray has to travel to collide with AABB
//if a negative value is returned, there is no collision
float rayAABBBoundsDist(Ray ray, vec3 AABB_bl, int AABB_size) {
	//translate ray into AABB space
	ray.origin -= AABB_bl;

	bool inside = pointInsideAABB(ray.origin, vec3(0), AABB_size);
	float dir_component[3] = float[](ray.dir.x, ray.dir.y, ray.dir.z);
	float pos_component[3] = float[](ray.origin.x, ray.origin.y, ray.origin.z);
	for(int i = 0; i < 3; i++){
		if(abs(dir_component[i]) == 0) {
			continue;
		}
		float tgt = (inside ^^ (dir_component[i] < 0)) ? AABB_size : 0;
		float dist = tgt - pos_component[i];
		float ray_mul = dist / dir_component[i];
		if(ray_mul < 0){
			continue;
		}
		vec3 test_pos = ray.origin + ray.dir * ray_mul;
		if(pointInsideAABB(test_pos, vec3(0), AABB_size, 0.0001)) {
			return ray_mul;
		}
	}
	return -1;
}

//returns 0, 1, or 2 depending on if the ray hit the x, y, or z, bound of the AABB
//returns -1 if no hit occurs. 
int rayAABBBoundsNorm(Ray ray, vec3 AABB_bl, int AABB_size) {
	//translate ray into AABB space
	ray.origin -= AABB_bl;

	bool inside = pointInsideAABB(ray.origin, vec3(0), AABB_size);
	float dir_component[3] = float[](ray.dir.x, ray.dir.y, ray.dir.z);
	float pos_component[3] = float[](ray.origin.x, ray.origin.y, ray.origin.z);
	for(int i = 0; i < 3; i++){
		if(abs(dir_component[i]) == 0) {
			continue;
		}
		float tgt = (inside ^^ (dir_component[i] < 0)) ? AABB_size : 0;
		float dist = tgt - pos_component[i];
		float ray_mul = dist / dir_component[i];
		if(ray_mul < 0){
			continue;
		}
		vec3 test_pos = ray.origin + ray.dir * ray_mul;
		if(pointInsideAABB(test_pos, vec3(0), AABB_size, 0.0001)) {
			return i;
		}
	}
	return -1;
}

//returns the length the ray has to travel to collide with AABB
//if a negative value is returned, there is no collision
float rayAABBDist(Ray ray, vec3 AABB_bl, int AABB_size) {
	//translate ray into AABB space
	ray.origin -= AABB_bl;

	//see if ray origin is inside AABB
	if(pointInsideAABB(ray.origin, vec3(0), AABB_size)) {
		return 0;
	}
	
	return rayAABBBoundsDist(ray, vec3(0), AABB_size);
}

HitInfo createHitInfo() {
	return HitInfo(false, vec3(0), vec3(0), vec3(0));
}

HitInfo raySVO(Ray ray) {
	HitInfo result = createHitInfo();

	//read svo size and offset from buffer
	int svo_size_pow = svo_data[0];
	int svo_size = (1 << svo_size_pow);
	vec3 svo_offset = vec3(svo_data[1], svo_data[2], svo_data[3]);
	
	//translate ray into svo space
	ray.origin -= svo_offset;
	
	//check if ray will collide with svo at all
	float ray_AABB_dist = rayAABBDist(ray, vec3(0), svo_size);
	int last_norm = rayAABBBoundsNorm(ray, vec3(0), svo_size);
	ray.origin += ray.dir * (ray_AABB_dist + 0.001);
	if(!pointInsideAABB(ray.origin, vec3(0), svo_size) || ray_AABB_dist < 0) {
		return result;
	}
	
	//traverse through svo and see what color we get
	int stack_ind = 0;
	int ind_stack[64];	//what's the buffer index offset of the parent
	vec3 pos_stack[64];	//what's the bl offset of the parent
	int child_ind_stack[64];	//in this parent, what's the index of the child the ray is in?
	
	pos_stack[0] = vec3(0);
	ind_stack[0] = 1 + 3;	//size pow and offset
	child_ind_stack[0] = computeChildInd(ray, vec3(0), svo_size);
	int cur_size = svo_size;
	int iter_cnt = 0;
	
	float dir_component[3] = float[](ray.dir.x, ray.dir.y, ray.dir.z);
	
	while(stack_ind >= 0){
		iter_cnt ++;
		if(iter_cnt == 3 * svo_size){
			result.did_hit = true;
			result.color = vec3(0, 1, 0);
			break;
		}
	
		vec3 pos_offset = pos_stack[stack_ind];
		int ind_offset = ind_stack[stack_ind];
		int child_ind = child_ind_stack[stack_ind];
		
		if(cur_size == 1){
			//we're at a leaf node, find the color and exit
			int color_bits = svo_data[ind_offset + 0];
			int r = (color_bits >> 24) & 0xff;
			int g = (color_bits >> 16) & 0xff;
			int b = (color_bits >> 8) & 0xff;
			
			result.did_hit = true;
			result.pos = ray.origin;
			result.color = vec3(r, g, b) / 255.0;
			
			result.normal = last_norm == 0? vec3(1, 0, 0) : (last_norm == 1? vec3(0, 1, 0) : vec3(0, 0, 1));
			result.normal *= dir_component[last_norm] > 0? -1 : 1;
			break;
		}
		
		//check if child exists
		int child_ind_offset = (svo_data[ind_offset + child_ind] / 32) + ind_offset;
		if(child_ind_offset != ind_offset){
			//child exists, push stuff to stack. 
			vec3 child_pos_offset = pos_offset;
			child_pos_offset.x += (cur_size / 2) * ((child_ind >> 0) & 1);
			child_pos_offset.y += (cur_size / 2) * ((child_ind >> 1) & 1);
			child_pos_offset.z += (cur_size / 2) * ((child_ind >> 2) & 1);
			
			stack_ind ++;
			cur_size /= 2;
			pos_stack[stack_ind] = child_pos_offset;
			ind_stack[stack_ind] = child_ind_offset;
			child_ind_stack[stack_ind] = computeChildInd(ray, child_pos_offset, cur_size);
			continue;
		}
		
		//figure out what's the next boundary we cross. 
		int which_bound = -1;
		float min_dist = 1000000000;
		float pos_component[3] = float[](ray.origin.x, ray.origin.y, ray.origin.z);
		float bl_offset_component[3] = float[](pos_offset.x, pos_offset.y, pos_offset.z);
		bl_offset_component[0] += (cur_size / 2) * ((child_ind >> 0) & 1);
		bl_offset_component[1] += (cur_size / 2) * ((child_ind >> 1) & 1);
		bl_offset_component[2] += (cur_size / 2) * ((child_ind >> 2) & 1);
		for(int i = 0; i < 3; i++){
			if(dir_component[i] == 0){
				continue;
			}
			float tgt = bl_offset_component[i] + (dir_component[i] > 0? cur_size / 2 : 0);
			float dist = tgt - pos_component[i];
			float ray_mul = dist / dir_component[i];
			if(ray_mul < min_dist) {
				min_dist = ray_mul;
				which_bound = i;
			}
		}
		
		//this shouldn't happen
		if(which_bound == -1){
			result.did_hit = true;
			result.color = vec3(0, 0, 1);
			break;
		}
		
		//ok, now that we've found the bound, let's update the ray, and see what child indexes we can update
		ray.origin += ray.dir * min_dist;
		last_norm = which_bound;
		
		bool decrease = dir_component[which_bound] < 0;
		while(stack_ind >= 0) {
			int cur_child_ind = child_ind_stack[stack_ind];
			if(((cur_child_ind >> which_bound) & 1) == 0 ^^ decrease) {
				//we stay in our current parent
				cur_child_ind = cur_child_ind ^ (1 << which_bound);
				child_ind_stack[stack_ind] = cur_child_ind;
				break;
			}
			else {
				//we exit our current parent
				stack_ind --;
				cur_size *= 2;
				continue;
			}
		}
	}
	
	//result.color.r += float(iter_cnt) * 4 / (svo_size);
	
	return result;
}

vec3 traceRay(Ray ray) {
	HitInfo hit = raySVO(ray);
	if(hit.did_hit) {
		float diffuse = dot(hit.normal, sun_dir);
		float ambient = 1;
		
		float exposure = 2;
		float gamma = 0.5;
		
		vec3 final_color = hit.color * (diffuse + ambient);

    	final_color =  vec3(1.0) - exp(-final_color * exposure); //hdr tonemapping    
		final_color = pow(final_color, vec3(1.0 / gamma)); //gamma correction
	
		return final_color;
	}
	else {
		return texture(skybox_tex, ray.dir).rgb;
	}	
}

void main() {   
	Ray ray = Ray(camera_pos, normalize(frag_dir));
	vec3 color = traceRay(ray);
	
	vec4 result = vec4(color, 1);
	tex_color.rgba = result;
} 

