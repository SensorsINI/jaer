#version 130
// changes here must be saved to jar file by project build to be able to load this shader as resource

in vec3 frag_v;
in float frag_t0;
in float frag_t1;
out vec4 frag_color;

void main() {
    float z=-frag_v.z; // 0 at most recent time, dt at most distant past
    float dt=(frag_t1-frag_t0);
    float f=z/dt; // fraction of total time in window, 0 at now, 1 at most distant past
    float f1=1-f; 
    float b=max(1-2*f,0);
    float r=max(2*(f-.5),0);
    float g=f;
    if(f>.5)
        g=1-g;
    frag_color =  (.75*f1+.25) * vec4(r, g, b, 1.0); 
}

