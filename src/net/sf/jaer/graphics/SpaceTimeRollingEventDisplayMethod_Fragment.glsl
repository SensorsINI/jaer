#version 130
// changes here must be saved to jar file by project build to be able to load this shader as resource

in vec3 frag_v;
out vec4 frag_color;

void main() {
	frag_color = 1 * vec4(0.1, 0.3, 0.7, 1.0);
}

