#version 430 core
#extension GL_NV_shader_atomic_float : enable

layout(location = 0) out vec4 color;

in vec2 frag_pos;
in vec2 frag_uv;
in vec4 frag_hue;

uniform sampler2D tex_spritesheet;

layout(rgba32f, binding = 1) uniform image2D tex_canvas;
layout(rgba32f, binding = 2) uniform image2D tex_target;

void main() {
	ivec2 ipos = ivec2(int(frag_pos.x), int(frag_pos.y));
	
	vec3 canvas = imageLoad(tex_canvas, ipos).rgb;
	vec3 target = imageLoad(tex_target, ipos).rgb;
	
	vec4 sprite_color = texture(tex_spritesheet, frag_uv);
	sprite_color *= frag_hue;
	
	//apply sprite to canvas color
	vec3 new_canvas = mix(canvas, sprite_color.rgb, sprite_color.a);
	color = vec4(new_canvas, 1.0);
	
	//color = vec4(target, 1.0);
} 

