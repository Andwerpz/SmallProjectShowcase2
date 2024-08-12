#version 330 core
layout (location = 0) out vec4 out_normal;

in vec2 frag_uv;

uniform sampler2D tex_height;
uniform int tile_resolution;
uniform float pixel_scale;

void main() {
	vec2 dr = vec2(1.0 / tile_resolution);
	float p0 = texture2D(tex_height, dr * (gl_FragCoord.xy + vec2(0.0, 0.0))).r;
	float px = texture2D(tex_height, dr * (gl_FragCoord.xy + vec2(1.0, 0.0))).r;
	float py = texture2D(tex_height, dr * (gl_FragCoord.xy + vec2(0.0, 1.0))).r;
	vec3 dx = vec3(pixel_scale, 0.0, px - p0);
	vec3 dy = vec3(0.0, pixel_scale, py - p0);
	vec3 n = normalize(cross(dx, dy));
	out_normal = vec4(0.5 * n + 0.5, 1.0);
}


