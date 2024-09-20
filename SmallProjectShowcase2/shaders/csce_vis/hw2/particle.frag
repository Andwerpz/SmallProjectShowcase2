#version 430 core
layout (location = 0) out vec4 out_color;

//3D particle information
in vec3 frag_pos;
in vec3 frag_vel;
in vec3 frag_hue;
in float frag_lifespan;

in vec2 frag_uv;
in float frag_depth;

uniform sampler2D geometry_pos_tex;
uniform int do_gdt;

void main() {
	if(frag_lifespan < 0) {
		discard;
	}
	
	float geom_NDC_depth = texture(geometry_pos_tex, frag_uv).a;
	if(do_gdt == 1 && geom_NDC_depth < frag_depth && geom_NDC_depth != 0){	//!= 0 as that's where it isn't rendered
		discard;
	}
	
    out_color.rgba = vec4(frag_hue, 1);    
    gl_FragDepth = frag_depth;
} 

