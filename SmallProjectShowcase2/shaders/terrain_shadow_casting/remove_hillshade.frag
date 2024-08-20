#version 330 core
layout (location = 0) out vec4 out_color;

in vec2 frag_uv;

uniform sampler2D tex_color;
uniform sampler2D tex_hillshade;

void main() {
	float hillshade = texture(tex_hillshade, frag_uv).r;
	float color = texture(tex_color, frag_uv).r;
	
	hillshade = (hillshade - 0.5) * 2.0;
	hillshade = (hillshade - 0.5) * 2.0;
	color /= hillshade;
	out_color = vec4(vec3(hillshade), 1);
}


