#version 130
// changes here must be saved to jar file by project build to be able to load this shader as resource
in float polarity; // the event polarity, either 0 or 1
in vec3 v; // the event x,y,t

// out vec3 frag_v;
out float frag_polarity;

uniform mat4 mv; // modelview
uniform mat4 proj; // projection

void main() {
	// transform vp to homogeneous coordinate
	vec4 vh = vec4(v, 1);
	gl_Position = proj * mv * vh; // must be this order
	gl_PointSize = 4.0;
	frag_polarity = polarity; // (v[0] + 1.0) / 2.0 * 1.0 * polarity;
	// frag_v = v;
}

