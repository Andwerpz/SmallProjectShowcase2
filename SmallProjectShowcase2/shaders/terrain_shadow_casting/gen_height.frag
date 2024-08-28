#version 330 core
layout (location = 0) out vec4 out_height;

in vec2 frag_uv;

uniform sampler2D tex_rgb_height;

void main() {
	vec3 rgb = texture2D(tex_rgb_height, frag_uv).rgb;
	float height = (rgb.r * 255.0 * 256.0 * 256.0 + rgb.g * 255.0 * 256.0 + rgb.b * 255.0) * 0.1 - 10000.0;
	height *= 4;
	out_height = vec4(vec3(height), 1);
}


