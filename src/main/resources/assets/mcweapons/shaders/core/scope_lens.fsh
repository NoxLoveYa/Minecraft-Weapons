#version 330
#extension GL_ARB_separate_shader_objects : require

// The lens of a scope (ScopeRenderer): refracts the level seen through it and etches its reticle in. The output covers the lens's
// square; the eyepiece shows its inscribed circle.

uniform sampler2D InSampler;      // the level through the scope
uniform sampler2D ReticleSampler; // the scope's etched reticle: black lines across the whole lens
uniform sampler2D LitSampler;     // the lit reticle's shape, white with its halo

layout(std140) uniform ScopeLens {
    vec4 LitColor;   // rgb: the lit reticle's colour; a: its half-size as a share of the lens's radius
    vec2 EyeOffset;  // the eye off the scope's axis, in lens radii (x right, y up)
    float Pupil;     // the exit pupil's radius on the lens: 1 at the right eye relief, smaller with the eye too far or too near
    float Unused;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

// Barrel distortion: the picture bulges a little towards the rim, like a simple magnifier's.
const float DISTORTION = 0.06;
// Chromatic aberration: the colours separate by up to this much of the radius at the rim.
const float ABERRATION = 0.012;

vec2 toUv(vec2 p) {
    return p * 0.5 + 0.5;
}

void main() {
    vec2 p = texCoord * 2.0 - 1.0;
    float r = length(p);
    if (r > 1.0) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    // Refraction: each colour bent a little differently, more towards the rim.
    vec2 bent = p * (1.0 - DISTORTION * (1.0 - r * r));
    float fringe = ABERRATION * r * r;
    vec3 colour;
    colour.r = texture(InSampler, toUv(bent * (1.0 + fringe))).r;
    colour.g = texture(InSampler, toUv(bent)).g;
    colour.b = texture(InSampler, toUv(bent * (1.0 - fringe))).b;

    // Glass takes a little light and tints it faintly cool.
    colour *= vec3(0.93, 0.96, 1.0);

    // The scope's reticle sits in the eyepiece's focal plane: fixed on the lens, drawn over the picture.
    vec4 etched = texture(ReticleSampler, toUv(p));
    colour = mix(colour, etched.rgb, etched.a);
    if (LitColor.a > 0.0) {
        vec2 litUv = toUv(p / LitColor.a);
        if (all(greaterThanEqual(litUv, vec2(0.0))) && all(lessThanEqual(litUv, vec2(1.0)))) {
            vec4 lit = texture(LitSampler, litUv);
            colour = mix(colour, LitColor.rgb * lit.rgb, lit.a);
        }
    }

    // The exit pupil: only light through a disc the eye is in comes through. Off the axis its edge sweeps in from the far side, a
    // crescent of shadow; too far back or too close, the disc shrinks to a tunnel.
    float pupil = length(p + EyeOffset * 1.4);
    colour *= smoothstep(Pupil, Pupil - 0.18, pupil);

    // The tube's edge darkens the rim, and a faint glint lies on the glass from the light above.
    colour *= smoothstep(1.0, 0.86, r);
    colour += vec3(0.05, 0.06, 0.07) * smoothstep(0.55, 0.0, length(p - vec2(-0.45, 0.55))) * smoothstep(1.0, 0.7, r);

    fragColor = vec4(colour, 1.0);
}
