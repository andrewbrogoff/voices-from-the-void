#version 330

// The Voices' colour drain (warp spec, "Perception effects"): desaturation only, luminance kept
// (Rec. 709 weights), by the intensity the client writes into the 1 x 1 Intensity texture.

uniform sampler2D InSampler;
uniform sampler2D IntensitySampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
    vec2 IntensitySize;
};

out vec4 fragColor;

void main() {
    vec4 colour = texture(InSampler, texCoord);
    float amount = texture(IntensitySampler, vec2(0.5, 0.5)).r;
    float luma = dot(colour.rgb, vec3(0.2126, 0.7152, 0.0722));
    fragColor = vec4(mix(colour.rgb, vec3(luma), amount), 1.0);
}
