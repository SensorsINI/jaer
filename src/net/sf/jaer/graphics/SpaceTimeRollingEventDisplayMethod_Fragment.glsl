#version 130
// changes here must be saved to jar file by project build to be able to load this shader as resource

in vec3 frag_v;
in float frag_t0;
in float frag_t1;
out vec4 frag_color;

void main() {
    float z=frag_v.z;
    float dt=(frag_t1-frag_t0);
    float f=z/dt;
    f=f*f*f;
    float f1=1-f;
    frag_color = 1 * vec4(f1, 0.3, 0, 1.0);
}

