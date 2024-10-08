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
	for(int i = 0; i <= bevel_size; i++){
		bool found = false;
		if(texture(obstacle_map, frag_uv + vec2(texel_size.x * i, 0)).r != 1){
			normal += vec3(1, 0, 0);
			found = true;
		}
		if(texture(obstacle_map, frag_uv + vec2(-texel_size.x * i, 0)).r != 1) {
			normal += vec3(-1, 0, 0);
			found = true;
		}
		if(texture(obstacle_map, frag_uv + vec2(0, texel_size.y * i)).r != 1) {
			normal += vec3(0, 1, 0);
			found = true;
		}
		if(texture(obstacle_map, frag_uv + vec2(0, -texel_size.y * i)).r != 1) {
			normal += vec3(0, -1, 0);
			found = true;
		}
		if(found){
			break;
		}
	}
	
	normal = normalize(normal);
	normal = (normal * 0.5) + vec3(0.5);
	
	out_obstacle_normal = vec4(normal, 1);
}


