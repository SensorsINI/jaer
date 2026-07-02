#version 120

// ColorFilter ordinals: W=0, R=1, G=2, B=3
const int CF_W = 0;
const int CF_R = 1;
const int CF_G = 2;
const int CF_B = 3;

uniform sampler2D uMosaic;
uniform sampler2D uEvents;
uniform vec2 uChipSize;
uniform vec2 uTextureSize;
uniform vec4 uCfa;
uniform int uDisplayFrames;
uniform int uDisplayEvents;
uniform int uAwbEnabled;
uniform int uCcEnabled;
uniform int uMonochrome;
uniform float uAwbGR;
uniform float uAwbGB;
uniform mat3 uCcMatrix;
uniform vec3 uCcOffset;

varying vec2 vTexCoord;

int cfaPhase(int x, int y) {
    if (mod(float(y), 2.0) < 1.0) {
        return (mod(float(x), 2.0) < 1.0) ? 0 : 1;
    }
    return (mod(float(x), 2.0) < 1.0) ? 3 : 2;
}

int cfaAt(int x, int y) {
    int p = cfaPhase(x, y);
    if (p == 0) {
        return int(uCfa.x);
    }
    if (p == 1) {
        return int(uCfa.y);
    }
    if (p == 2) {
        return int(uCfa.z);
    }
    return int(uCfa.w);
}

float rawLum(int x, int y) {
    if (x < 0 || y < 0 || x >= int(uChipSize.x) || y >= int(uChipSize.y)) {
        return 0.0;
    }
    vec2 uv = (vec2(float(x), float(y)) + vec2(0.5)) / uTextureSize;
    return texture2D(uMosaic, uv).r;
}

float balancedLum(int x, int y) {
    float l = rawLum(x, y);
    if (uAwbEnabled != 0) {
        int cf = cfaAt(x, y);
        if (cf == CF_R) {
            l *= uAwbGR;
        } else if (cf == CF_B) {
            l *= uAwbGB;
        }
    }
    return l;
}

float interpChannel(int channel, ivec2 p) {
    if (cfaAt(p.x, p.y) == channel) {
        return balancedLum(p.x, p.y);
    }
    float sum = 0.0;
    int count = 0;
    for (int dy = -1; dy <= 1; dy++) {
        for (int dx = -1; dx <= 1; dx++) {
            if (dx == 0 && dy == 0) {
                continue;
            }
            int nx = p.x + dx;
            int ny = p.y + dy;
            if (nx >= 0 && nx < int(uChipSize.x) && ny >= 0 && ny < int(uChipSize.y)) {
                if (cfaAt(nx, ny) == channel) {
                    sum += balancedLum(nx, ny);
                    count++;
                }
            }
        }
    }
    if (count == 0) {
        return balancedLum(p.x, p.y);
    }
    return sum / float(count);
}

vec3 demosaicAt(ivec2 p) {
    if (uMonochrome != 0) {
        int qx = (p.x / 2) * 2;
        int qy = (p.y / 2) * 2;
        float g = (balancedLum(qx + 1, qy) + balancedLum(qx + 1, qy + 1) + balancedLum(qx, qy + 1)) / 3.0;
        return vec3(g);
    }
    float r = interpChannel(CF_R, p);
    float g = interpChannel(CF_G, p);
    float b = interpChannel(CF_B, p);
    if (uCcEnabled != 0) {
        vec3 rgb = vec3(r, g, b);
        return uCcMatrix * rgb + uCcOffset;
    }
    return vec3(r, g, b);
}

void main() {
    ivec2 p = ivec2(vTexCoord * uTextureSize);
    vec3 frameColor = vec3(0.0);
    if (uDisplayFrames != 0) {
        frameColor = demosaicAt(p);
    }

    if (uDisplayEvents != 0) {
        vec4 evt = texture2D(uEvents, vTexCoord);
        if (evt.a > 0.0) {
            gl_FragColor = vec4(evt.rgb, 1.0);
            return;
        }
    }
    gl_FragColor = vec4(frameColor, 1.0);
}
