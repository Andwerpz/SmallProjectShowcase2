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

const int block_size = (1 << 20);

HitInfo raySVO(Ray ray, int block_index) {
	HitInfo result = createHitInfo();
	int block_offset = block_index * block_size;

	//read svo size and offset from buffer
	int svo_size_pow = svo_data[block_offset + 0];
	int svo_size = (1 << svo_size_pow);
	vec3 svo_offset = vec3(svo_data[block_offset + 1], svo_data[block_offset + 2], svo_data[block_offset + 3]);
	
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
			int color_bits = svo_data[block_offset + ind_offset + 0];
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
		int child_ind_offset = (svo_data[block_offset + ind_offset + child_ind] / 32) + ind_offset;
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

ivec3 calcChunkCoord(vec3 pos, int chunk_size) {
	return ivec3(floor(pos.x / chunk_size), floor(pos.y / chunk_size), floor(pos.z / chunk_size));
}

vec3 traceRay(Ray ray) {
	vec3 result = texture(skybox_tex, ray.dir).rgb;

	int chunk_size = svo_data[1000000];
	int view_dist = svo_data[1000001];
	int cube_size = 2 * view_dist + 1;
	
	ivec3 camera_chunk_coord = calcChunkCoord(camera_pos, chunk_size);
	ivec3 ray_chunk_coord = calcChunkCoord(ray.origin, chunk_size);
	ivec3 cube_ioffset = camera_chunk_coord - ivec3(view_dist);
	
	vec3 cube_AABB_offset = cube_ioffset * chunk_size;
	int cube_AABB_size = cube_size * chunk_size;
	
	//check if ray will intersect cube
	float ray_cube_dist = rayAABBDist(ray, cube_AABB_offset, cube_AABB_size);
	ray.origin += ray.dir * (ray_cube_dist + 0.0001);
	if(!pointInsideAABB(ray.origin, cube_AABB_offset, cube_AABB_size) || ray_cube_dist < 0) {
		return result;
	}
	
	//ray will intersect cube.
	float dir_component[3] = float[](ray.dir.x, ray.dir.y, ray.dir.z);
	
	ivec3 ray_cube_ioffset = ray_chunk_coord - cube_ioffset;
	
	while(true) {
		int chunk_hash = ray_cube_ioffset.x + ray_cube_ioffset.y * cube_size + ray_cube_ioffset.z * cube_size * cube_size;
		int chunk_index = svo_data[chunk_hash];
		
		HitInfo hit = raySVO(ray, chunk_index);
		
		if(hit.did_hit) {
			float diffuse = dot(hit.normal, sun_dir);
			float ambient = 1;
			
			float exposure = 2;
			float gamma = 0.5;
			
			vec3 final_color = hit.color * (diffuse + ambient);
	
	    	final_color =  vec3(1.0) - exp(-final_color * exposure); //hdr tonemapping    
			final_color = pow(final_color, vec3(1.0 / gamma)); //gamma correction
		
			result = final_color;
			break;
		}
		
		//look for the next chunk
		float pos_component[3] = float[](ray.origin.x, ray.origin.y, ray.origin.z);
		float bl_offset_component[3] = float[](0, 0, 0);
		bl_offset_component[0] = (cube_ioffset.x + ray_cube_ioffset.x) * chunk_size;
		bl_offset_component[1] = (cube_ioffset.y + ray_cube_ioffset.y) * chunk_size;
		bl_offset_component[2] = (cube_ioffset.z + ray_cube_ioffset.z) * chunk_size;
		float min_dist = 1000000000;
		int which_bound = -1;
		for(int i = 0; i < 3; i++){
			if(dir_component[i] == 0){
				continue;
			}
			float tgt = bl_offset_component[i] + (dir_component[i] > 0? chunk_size : 0);
			float dist = tgt - pos_component[i];
			float ray_mul = dist / dir_component[i];
			if(ray_mul < min_dist) {
				min_dist = ray_mul;
				which_bound = i;
			}
		}
		
		//this shouldn't happen
		if(which_bound == -1){
			result = vec3(0, 0, 1);
			break;
		}
		
		//ok, update the ray and the ray cube offset
		ray.origin += ray.dir * min_dist;
		if(which_bound == 0){ ray_cube_ioffset.x += (dir_component[0] < 0? -1 : 1); }
		if(which_bound == 1){ ray_cube_ioffset.y += (dir_component[1] < 0? -1 : 1); }
		if(which_bound == 2){ ray_cube_ioffset.z += (dir_component[2] < 0? -1 : 1); }
		
		//check if the ray is outside of the cube
		if( ray_cube_ioffset.x < 0 || ray_cube_ioffset.x >= cube_size || 
			ray_cube_ioffset.y < 0 || ray_cube_ioffset.y >= cube_size ||
			ray_cube_ioffset.z < 0 || ray_cube_ioffset.z >= cube_size) {
			//ray is outside of cube
			break;
		}
	}
	
	return result;
}

void main() {   
	Ray ray = Ray(camera_pos, normalize(frag_dir));
	vec3 color = traceRay(ray);
	
	vec4 result = vec4(color, 1);
	tex_color.rgba = result;
} 

