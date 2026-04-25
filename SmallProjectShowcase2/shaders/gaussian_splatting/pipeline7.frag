#version 430 core
layout (location = 0) out vec4 color;

uniform int screenWidth;
uniform int screenHeight;

layout (binding = 6) readonly buffer renderBuffer {
	vec4 renderData[];
};

void main() {
	int px_x = int(gl_FragCoord.x);
	int px_y = int(gl_FragCoord.y);
	color = renderData[px_x + px_y * screenWidth];
}


