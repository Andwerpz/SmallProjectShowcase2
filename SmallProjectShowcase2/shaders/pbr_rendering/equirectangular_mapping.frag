#version 330 core
layout (location = 0) out vec4 FragColor;

in vec3 localPos;

uniform sampler2D equirectangularMap;

const vec2 invAtan = vec2(0.1591, 0.3183);

vec3 SampleSphericalMap(vec3 v) {
    vec2 uv = vec2(atan(v.z, v.x), asin(v.y));
    uv *= invAtan;
    uv += 0.5;
    return texture(equirectangularMap, uv).rgb;
}

void main() {		
    vec3 color = SampleSphericalMap(normalize(localPos));	//make sure to normalize localPos;
    FragColor = vec4(color, 1.0);
}