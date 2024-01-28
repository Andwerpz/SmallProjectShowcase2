#version 430 core
#extension GL_NV_shader_atomic_float : enable

in vec2 frag_pos;
in vec2 frag_uv;
in vec4 frag_hue;
flat in int frag_sprite_id;

uniform sampler2D tex_spritesheet;

uniform int canvas_width;
uniform int canvas_height;

layout(rgba32f, binding = 1) uniform image2D tex_canvas;
layout(rgba32f, binding = 2) uniform image2D tex_target;

layout(std430, binding = 3) coherent buffer score_buffer { float score_data[]; };

float calcDiff(vec3 a, vec3 b) {
	vec3 diff = a - b;
	return abs(diff.x) + abs(diff.y) + abs(diff.z);
}

void main() {
	ivec2 ipos = ivec2(int(frag_pos.x), int(frag_pos.y));
	
	vec3 canvas = imageLoad(tex_canvas, ipos).rgb;
	vec3 target = imageLoad(tex_target, ipos).rgb;
	
	vec4 sprite_color = texture(tex_spritesheet, frag_uv);
	sprite_color *= frag_hue;
	
	//apply sprite to canvas color
	vec3 new_canvas = mix(canvas, sprite_color.rgb, sprite_color.a);
	
	//compute delta, and save to buffer
	float init_diff = calcDiff(canvas, target);
	float final_diff = calcDiff(new_canvas, target);
	
	float score = final_diff - init_diff;
	atomicAdd(score_data[frag_sprite_id], score);	
	
	discard;
} 

