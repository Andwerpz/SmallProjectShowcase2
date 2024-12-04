#version 330 core
layout (location = 0) out vec4 out_height;
layout (location = 1) out vec4 out_normal;

in vec2 frag_uv;

uniform float time;
uniform int nr_sums;

uniform float theta[128];
uniform float speed[128];

uniform float u_amplitude;
uniform float u_period;

uniform float period_mult;
uniform float amplitude_mult;

uniform float domain_warp_coeff;

void main() {
	float x = frag_uv.x;
	float z = frag_uv.y;
	float y = 0;

	float dx = 0;
	float dz = 0;

	float period = u_period;
	float amplitude = u_amplitude;

	float pdx = 0;
	float pdz = 0;

	for(int i = 0; i < nr_sums; i++){
		y += amplitude * sin((x * sin(theta[i]) + z * cos(theta[i])) / period + time * speed[i]);
		pdx = (amplitude / period) * cos(time * speed[i] + (x * sin(theta[i]) + z * cos(theta[i])) / period) * sin(theta[i]);
		pdz = (amplitude / period) * cos(time * speed[i] + (x * sin(theta[i]) + z * cos(theta[i])) / period) * cos(theta[i]);

		dx += pdx;
		dz += pdz;

		x -= pdx * domain_warp_coeff;
		z -= pdz * domain_warp_coeff;

		period *= period_mult;
		amplitude *= amplitude_mult;
	}

	out_height.rgba = vec4(vec3(x, y, z), 1);
	out_normal.rgba = vec4(vec3(dx, dz, 0), 1);
}


