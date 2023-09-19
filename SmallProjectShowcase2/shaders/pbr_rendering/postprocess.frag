#version 330 core
layout (location = 0) out vec4 FragColor;

in vec2 frag_uv;

uniform float gamma;
uniform float exposure;
uniform sampler2D tex_color;

void main()  {
	vec3 final_color = texture(tex_color, frag_uv).rgb;

    final_color =  vec3(1.0) - exp(-final_color * exposure); //hdr tonemapping    
	final_color = pow(final_color, vec3(1.0 / gamma)); //gamma correction
	
	FragColor = vec4(final_color, 1);
}


