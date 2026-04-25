#version 430 core
layout (location = 0) out vec4 lColor;

const float PI = 3.14159265;
const float INV_PI = 1.0 / PI;

uniform mat4 pr_matrix;
uniform mat4 vw_matrix;

uniform int screenWidth;
uniform int screenHeight;
uniform float verticalFOV;

uniform vec3 cameraPos;
in vec3 fragDir;

// TODO 
// - implement spherical harmonics
//   - figure out how to test this. perhaps do some raytracing on sphere, then render the color there. 
// - for each gaussian, figure out what tiles it belongs to
// - figure out how to cull gaussians if they get too close. 

//suppose you have a function f(x) where x is a normalized vector. 
//then spherical harmonics in this context is just approximating f(x) as a sum of a bunch of basis functions, 
// kinda like a fourier transform. 
//to determine the color, we have r(x), g(x), b(x), where x is just the direction from the gaussian to the camera

struct Gaussian {
	vec3 center;
	float pad0;
	vec4 orient;	//quaternion
	vec3 scale;
	float alpha;
	
	// 1 degree 0 coeff
	// 3 degree 1 coeff
	// 5 degree 2 coeff
	// 7 degree 3 coeff
	float r_coeff[16];
	float g_coeff[16];
	float b_coeff[16];
};

layout (binding = 0) readonly buffer gaussianBuffer {
	Gaussian gaussianData[];
};
uniform int nrGaussians;

mat3 buildMatrixFromQuaternion(vec4 q) {
    float s = q.x;
    float i = q.y;
    float j = q.z;
    float k = q.w;

    //normalize 
    float invLen = inversesqrt(s * s + i * i + j * j + k * k);
    s *= invLen;
    i *= invLen;
    j *= invLen;
    k *= invLen;

    return mat3(
        1.0 - 2.0 * (j * j + k * k), 2.0 * (i * j - k * s), 2.0 * (i * k + j * s),
        2.0 * (i * j + k * s), 1.0 - 2.0 * (i * i + k * k), 2.0 * (j * k - i * s),
        2.0 * (i * k - j * s), 2.0 * (j * k + i * s), 1.0 - 2.0 * (i * i + j * j)
    );
}

float sample2DGaussianOpacity(mat2 covariance, vec2 v) {
	float ret = dot(v, inverse(covariance) * v);
	return exp((-1.0 / 2.0) * ret);
}

float sampleSH(float coeff[16], vec3 dir) {
	//degree 0
	
	//degree 1
	
	//degree 2
	
	//degree 3

	return 0;
}

void main() {  
	
	//calc projection focal length
	float f_y = float(screenHeight) / (2.0 * tan(verticalFOV / 2.0));
	float f_x = f_y * screenWidth / screenHeight;

	vec3 color = vec3(0);

	for(int i = 0; i < nrGaussians; i++) {
		Gaussian g = gaussianData[i];
		vec3 center = g.center;
		vec3 view_dir = normalize(cameraPos - center);
		
		//transform gaussian center into camera space
		center = vec3(vw_matrix * vec4(center, 1.0));
		if(center.z > -max(g.scale.x, max(g.scale.y, g.scale.z))) {	//gaussian too close to camera plane
			continue;
		}
		
		//project gaussian center onto screen
		vec4 pr_center = pr_matrix * vec4(center, 1.0);
		vec2 center_uv = (pr_center.xy / pr_center.w + 1.0) / 2.0;
		center_uv.x *= screenWidth;
		center_uv.y *= screenHeight;
		
		//project gaussian covariance onto screen
		mat3 R = buildMatrixFromQuaternion(g.orient);							// covariance rotation
		mat3 S = mat3(															// covariance scale
			g.scale.x, 0, 0,
			0, g.scale.y, 0,
			0, 0, g.scale.z
		);
		mat3 Sigma_3D = R * S * transpose(S) * transpose(R);					// 3D covariance (world space)
		
		mat3 W = mat3(vw_matrix);												// view matrix (no translation)		
		mat3x2 J = mat3x2(														// projection jacobian
			f_x / center.z, 0, 
			0, f_y / center.z,
			-f_x * center.x / (center.z * center.z), -f_y * center.y / (center.z * center.z)
		);
		mat2 Sigma_2D = J * (W * Sigma_3D * transpose(W)) * transpose(J);		// 2D covariance (screen pixel space)
		
		//see if pixel is outside of gaussian
		float tau = 9;
		vec2 screen_rad = vec2(
			sqrt(tau * Sigma_2D[0][0]),
			sqrt(tau * Sigma_2D[1][1])
		);
		if(abs(gl_FragCoord.x - center_uv.x) > screen_rad.x || abs(gl_FragCoord.y - center_uv.y) > screen_rad.y) {
			// color += vec3(1, 0, 0) * (1.0 / float(nrGaussians));
			continue;
		}
		
		//sample gaussian opacity
		float opacity = sample2DGaussianOpacity(Sigma_2D, gl_FragCoord.xy - center_uv);
		color += vec3(1) * opacity * g.alpha;
		
		if(length(center_uv - gl_FragCoord.xy) < 1) {
			color = vec3(1);
		}
		
		//sample gaussian color
		
		//gaussian contributes color back to camera
		
	}

	lColor.rgba = vec4(color, 1.0);
} 

