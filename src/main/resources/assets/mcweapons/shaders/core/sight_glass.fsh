#version 330
#extension GL_ARB_separate_shader_objects : require

// The glass in an aimed 1x sight's window (ScopeRenderer.processSight), over the screen once the hand is drawn: the scope lens's effects
// (scope_lens.fsh) inside the window's own outline, over the level and the parts of the gun seen through it. The middle, where the
// reticle is, stays at 1x and sharp; nothing outside the window is touched.

uniform sampler2D InSampler; // the screen with the hand drawn
uniform sampler2D DepthSampler; // the hand's depth (reversed: nearer is larger)

layout(std140) uniform SightGlass {
    vec4 Corners[4]; // the window's corners on the screen, two to an entry (xy, zw), in order round it, in screen heights from the bottom left
    vec2 Center;     // the reticle on the screen, the same way: the middle of the glass
    vec2 HalfSize;   // half the window's width and height on the screen, for the glint
    float Count;     // corners in use, 3 to 8
    float Strength;  // 0 to 1 as the sight comes up to the eye: the glass's effects come in with it
    float Aspect;    // the screen's width over its height
    float GlassDepth; // the depth of the window's corner nearest the eye: the gun nearer than that is in front of the glass
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
    if (outward > 1.0 || texture(DepthSampler, texCoord).r > GlassDepth) {
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
