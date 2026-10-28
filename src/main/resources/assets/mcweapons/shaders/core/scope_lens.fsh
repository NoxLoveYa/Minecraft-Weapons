#version 330
#extension GL_ARB_separate_shader_objects : require

// The lens of an aimed scope (ScopeRenderer), over the whole screen before the hand is drawn: the level is already zoomed by the scope's
// magnification. Inside the lens it is refracted and the reticle etched in; outside, where the eye is not focused, it is a dim blur.

uniform sampler2D InSampler;      // the level, before the hand
uniform sampler2D ReticleSampler; // the scope's etched reticle: black lines across the whole lens
uniform sampler2D LitSampler;     // the lit reticle's shape, white with its halo

layout(std140) uniform ScopeLens {
    vec4 LitColor;   // rgb: the lit reticle's colour; a: its half-size as a share of the lens's radius
    vec2 Center;     // the lens's centre on the screen, 0 to 1 from the bottom left
    vec2 EyeOffset;  // the eye off the scope's axis, in lens radii (x right, y up)
    float Radius;    // the lens's radius on the screen, as a share of its height
    float Aspect;    // the screen's width over its height
    float Pupil;     // the exit pupil's radius: 1 with the eye where it is set to sit, smaller too far or too near
    float Strength;  // 0 to 1 as the scope comes to the eye: the blur and the lens's effects come in with it
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

// Barrel distortion: the picture bulges a little towards the rim, like a simple magnifier's.
const float DISTORTION = 0.08;
// Chromatic aberration: the colours separate by up to this much of the radius at the rim.
const float ABERRATION = 0.014;
// The eyepiece is placed from the last frame's pose, so the sharp disc reaches a little past it.
const float EDGE = 1.08;
// The blur outside, at full strength, as a share of the screen's height.
const float BLUR = 0.012;

// A point in lens radii from the centre, on the screen.
vec2 toScreen(vec2 d) {
    return Center + d * Radius / vec2(Aspect, 1.0);
}

void main() {
    vec2 d = (texCoord - Center) * vec2(Aspect, 1.0) / Radius;
    float r = length(d);

    if (r > EDGE) {
        // Two rings of taps round the pixel: a soft blur, dimmed, so the zoomed level outside reads as out of focus.
        vec2 reach = vec2(1.0 / Aspect, 1.0) * BLUR * Strength;
        vec3 sum = texture(InSampler, texCoord).rgb;
        for (int i = 0; i < 12; i++) {
            float a = float(i) * 0.5236;
            vec2 o = vec2(cos(a), sin(a));
            sum += texture(InSampler, texCoord + o * reach).rgb;
            sum += texture(InSampler, texCoord + o.yx * reach * 0.5).rgb;
        }
        fragColor = vec4(sum / 25.0 * mix(1.0, 0.55, Strength), 1.0);
        return;
    }

    // Refraction: each colour bent a little differently, more towards the rim.
    vec2 bent = d * (1.0 - DISTORTION * Strength * (1.0 - r * r));
    float fringe = ABERRATION * Strength * r * r;
    vec3 colour;
    colour.r = texture(InSampler, toScreen(bent * (1.0 + fringe))).r;
    colour.g = texture(InSampler, toScreen(bent)).g;
    colour.b = texture(InSampler, toScreen(bent * (1.0 - fringe))).b;

    // Glass takes a little light and tints it faintly cool.
    colour *= mix(vec3(1.0), vec3(0.93, 0.96, 1.0), Strength);

    // The reticle sits in the eyepiece's focal plane: fixed on the lens, drawn over the picture.
    if (r <= 1.0) {
        vec4 etched = texture(ReticleSampler, d * 0.5 + 0.5);
        colour = mix(colour, etched.rgb, etched.a * Strength);
        vec2 litUv = d / LitColor.a * 0.5 + 0.5;
        if (all(greaterThanEqual(litUv, vec2(0.0))) && all(lessThanEqual(litUv, vec2(1.0)))) {
            vec4 lit = texture(LitSampler, litUv);
            colour = mix(colour, LitColor.rgb * lit.rgb, lit.a * Strength);
        }
    }

    // The exit pupil: off the axis a crescent of shadow sweeps in from the far side; too far back or too close, it tunnels down.
    float pupil = length(d + EyeOffset * 1.4);
    colour *= mix(1.0, smoothstep(Pupil, Pupil - 0.18, pupil), Strength);

    // The tube's edge darkens the rim, and a faint glint lies on the glass from the light above.
    colour *= mix(1.0, smoothstep(1.0, 0.86, r), Strength);
    colour += vec3(0.05, 0.06, 0.07) * Strength * smoothstep(0.55, 0.0, length(d - vec2(-0.45, 0.55))) * smoothstep(1.0, 0.7, r);

    fragColor = vec4(colour, 1.0);
}
