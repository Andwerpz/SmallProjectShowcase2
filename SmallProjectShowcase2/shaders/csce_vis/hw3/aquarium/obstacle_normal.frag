#version 430 core
layout (location = 0) out vec4 out_obstacle_normal;

in vec2 frag_uv;

uniform float window_width;
uniform float window_height;

uniform sampler2D obstacle_map;

const int bevel_size = 10;

void main() {
	vec2 texel_size = vec2(1.0 / window_width, 1.0 / window_height);
	
	if(texture(obstacle_map, frag_uv).r != 1){
		discard;
	}
	
	vec3 normal = vec3(0, 0, 1);
	for(int i = 1; i <= bevel_size; i++){
		bool found_left = false;
		bool found_right = false;
		bool found_up = false;
		bool found_down = false;
		
		for(int j = -i + 1; j < i; j++){
			if(texture(obstacle_map, frag_uv + vec2(texel_size.x * i, texel_size.y * j)).r != 1){
				found_right = true;
			}
			if(texture(obstacle_map, frag_uv + vec2(-texel_size.x * i, texel_size.y * j)).r != 1){
				found_left = true;
			}
			if(texture(obstacle_map, frag_uv + vec2(texel_size.x * j, texel_size.y * i)).r != 1){
				found_up = true;
			}
			if(texture(obstacle_map, frag_uv + vec2(texel_size.x * j, -texel_size.y * i)).r != 1){
				found_down = true;
			}
		}
		
		if(found_left) {
			normal += vec3(-1, 0, 0);
		}
		if(found_right) {
			normal += vec3(1, 0, 0);
		}	
		if(found_down) {
			normal += vec3(0, -1, 0);
		}
		if(found_up) {
			normal += vec3(0, 1, 0);
		}
		if(found_left || found_right || found_up || found_down){
			break;
		}
	}
	
	normal = normalize(normal);
	normal = (normal * 0.5) + vec3(0.5);
	
	out_obstacle_normal = vec4(normal, 1);
}


