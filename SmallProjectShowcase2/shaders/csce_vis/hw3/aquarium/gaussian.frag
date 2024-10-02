#version 430 core
layout (location = 0) out vec4 out_color;

in vec2 frag_uv;

uniform float window_width;
uniform float window_height;

uniform sampler2D color_map;

const float kernel[25] = float[25] (
	1, 4, 7, 4, 1,
	4, 20, 33, 20, 4,
	7, 33, 55, 33, 7,
	4, 20, 33, 20, 4,
	1, 4, 7, 4, 1
);

void main() {
	vec2 texel_size = vec2(1.0 / window_width, 1.0 / window_height);
	vec3 result = vec3(0);
	
	for(int i = 0; i < 5; i++){
		for(int j = 0; j < 5; j++){
			result += kernel[i * 5 + j] * texture(color_map, frag_uv + vec2(texel_size.x * (j - 2), texel_size.y * (i - 2))).rgb;
		}
	}
	
	result *= 1.0 / 273.0;
	out_color = vec4(result, 1);
}


