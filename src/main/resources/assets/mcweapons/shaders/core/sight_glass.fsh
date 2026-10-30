#version 330
#extension GL_ARB_separate_shader_objects : require

// The glass in a 1x sight's window (ScopeRenderer.processSight), over the screen once the hand is drawn, aimed or not: the scope lens's
// effects (scope_lens.fsh) inside the window's own outline, over the level and the parts of the gun seen through it. The middle, where
// the reticle is, stays at 1x and sharp; nothing outside the window is touched, nor any part of the gun in front of the glass.

uniform sampler2D InSampler; // the screen with the hand drawn
uniform sampler2D DepthSampler; // the hand's depth (reversed: nearer is larger)

layout(std140) uniform SightGlass {
    vec4 Corners[4]; // the window's corners on the screen, two to an entry (xy, zw), in order round it, in screen heights from the bottom left
    vec4 Plane;      // the glass's plane in the hand's view space: its normal, and that dotted with a point on it
    vec2 Center;     // the reticle on the screen, the same way: the middle of the glass
    vec2 HalfSize;   // half the window's width and height on the screen, for the glint
    float Count;     // corners in use, 3 to 8
    float Strength;  // 0 to 1: how strong the glass's effects are
    float Aspect;    // the screen's width over its height
    float TanHalf;   // tan of half the hand's vertical field of view
    float ProjA;     // the hand's projection, z row: clip z = ProjA * view z + ProjB, clip w = -view z
    float ProjB;
    float ZeroToOne; // 1 when the depth buffer holds clip z / w as is, 0 when it is mapped from -1..1
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

// The picture bends a little near the frame (towards the middle, the most two thirds of the way out), but not at the middle, which
// stays at 1x, nor at the frame, so it meets the world round the sight.
const float DISTORTION = 0.16;
// Chromatic aberration, as the scope lens's: the colours separate by up to this much of the way to the frame.
const float ABERRATION = 0.014;
// The frame darkens the glass this far in, as a share of the way from the middle to the nearest side (the scope's 0.14 of its radius).
const float EDGE = 0.14;

vec2 corner(int i) {
    vec4 pair = Corners[i / 2];
    return i % 2 == 0 ? pair.xy : pair.zw;
}

vec2 toUv(vec2 p) {
    return p / vec2(Aspect, 1.0);
}

// What is drawn at uv is nearer the eye than the glass there (a little, so its own frame at the window's edge counts).
bool nearerThanGlass(vec2 uv) {
    vec3 ray = vec3((uv * 2.0 - 1.0) * vec2(Aspect, 1.0) * TanHalf, -1.0);
    float z = -Plane.w / dot(Plane.xyz, ray);
    if (z >= 0.0) {
        return false;
    }
    float ndc = (ProjA * z + ProjB) / -z;
    float glass = ZeroToOne > 0.5 ? ndc : ndc * 0.5 + 0.5;
    return texture(DepthSampler, uv).r > glass * 1.0005;
}

// The window's outline holds the point at uv, which shows a part of the gun nearer the eye than the glass.
bool nearInWindow(vec2 uv, int count) {
    vec2 d = uv * vec2(Aspect, 1.0) - Center;
    for (int i = 0; i < 8; i++) {
        if (i >= count) {
            break;
        }
        vec2 a = corner(i) - Center;
        vec2 b = corner(i + 1 < count ? i + 1 : 0) - Center;
        vec2 n = vec2(b.y - a.y, a.x - b.x);
        if (dot(n, a) < 0.0) {
            n = -n;
        }
        if (dot(n, d) > dot(n, a)) {
            return false;
        }
    }
    return nearerThanGlass(uv);
}

// In front of the glass, or within NEAR_REACH pixels of a part of the gun in front of it inside the window: a shader pack draws its gun
// a pixel or so off the copy whose depth this reads, and the frame's edge would otherwise be darkened as glass.
const float NEAR_REACH = 2.0;

bool inFront(vec2 uv, int count) {
    if (nearerThanGlass(uv)) {
        return true;
    }
    vec2 px = NEAR_REACH / vec2(textureSize(DepthSampler, 0));
    return nearInWindow(uv + vec2(px.x, 0.0), count) || nearInWindow(uv - vec2(px.x, 0.0), count)
        || nearInWindow(uv + vec2(0.0, px.y), count) || nearInWindow(uv - vec2(0.0, px.y), count)
        || nearInWindow(uv + px * 0.7, count) || nearInWindow(uv - px * 0.7, count)
        || nearInWindow(uv + vec2(px.x, -px.y) * 0.7, count) || nearInWindow(uv - vec2(px.x, -px.y) * 0.7, count);
}

void main() {
    vec2 d = texCoord * vec2(Aspect, 1.0) - Center;
    int count = int(Count + 0.5);
    // Per side: how far out the pixel is, 1 on that side's line. The largest is 0 at the middle and 1 on the outline; a rounded
    // version of it bends the picture without creases from the corners.
    float outward = 0.0;
    float rounded = 0.0;
    float toSide = 1.0e9;
    float inner = 1.0e9;
    for (int i = 0; i < 8; i++) {
        if (i >= count) {
            break;
        }
        vec2 a = corner(i) - Center;
        vec2 b = corner(i + 1 < count ? i + 1 : 0) - Center;
        vec2 n = normalize(vec2(b.y - a.y, a.x - b.x));
        float h = dot(n, a);
        // Outward, whichever way round the corners go.
        if (h < 0.0) {
            n = -n;
            h = -h;
        }
        float along = dot(n, d);
        outward = max(outward, along / h);
        rounded += pow(max(along / h, 0.0), 6.0);
        toSide = min(toSide, h - along);
        inner = min(inner, h);
    }
    // Outside the window, or a part of the gun in front of the glass (a rear sight): left as drawn.
    if (outward > 1.0 || inFront(texCoord, count)) {
        discard;
    }
    float r = min(pow(rounded, 1.0 / 6.0), 1.0);

    // Refraction: each colour bent a little differently, more towards the frame.
    vec2 bent = d * (1.0 - DISTORTION * Strength * r * r * (1.0 - r * r));
    float fringe = ABERRATION * Strength * r * r;
    vec3 colour;
    colour.r = texture(InSampler, toUv(Center + bent * (1.0 + fringe))).r;
    colour.g = texture(InSampler, toUv(Center + bent)).g;
    colour.b = texture(InSampler, toUv(Center + bent * (1.0 - fringe))).b;

    // Glass takes a little light and tints it faintly cool.
    colour *= mix(vec3(1.0), vec3(0.93, 0.96, 1.0), Strength);

    // The frame darkens the glass at its edge, and a faint glint lies on it from the light above.
    colour *= mix(1.0, smoothstep(0.0, EDGE * inner, toSide), Strength);
    colour += vec3(0.05, 0.06, 0.07) * Strength * smoothstep(0.55, 0.0, length(d / HalfSize - vec2(-0.45, 0.55))) * smoothstep(1.0, 0.7, r);

    fragColor = vec4(colour, 1.0);
}
