// 2.5D Sprite Fragment Shader — OpenGL ES 2.0 兼容版
// 卡通着色（3步Cel） + 描边Pass + 阴影Pass + 饱和度增强
precision mediump float;

uniform sampler2D uTex;
uniform vec4      uTint;       // 叠色：普通=(1,1,1,1), 阴影=(0,0,0,0.45)
uniform float     uCelSteps;   // 0=关闭卡通, 3=三段卡通
uniform float     uOutline;    // 1.0=描边Pass, 0.0=正常

varying vec2  vUV;
varying float vLight;

void main() {
    vec4 tex = texture2D(uTex, vUV);
    if (tex.a < 0.05) discard;

    // ── 描边Pass（输出纯黑，在主Pass前用稍大尺寸绘制以产生描边效果）
    if (uOutline > 0.5) {
        gl_FragColor = vec4(0.04, 0.04, 0.08, tex.a * uTint.a);
        return;
    }

    // ── 阴影Pass（深色半透明）
    if (uTint.a < 0.99) {
        gl_FragColor = vec4(0.0, 0.0, 0.0, tex.a * uTint.a);
        return;
    }

    // ── 主渲染Pass ─────────────────────────────────────
    vec3 c = tex.rgb;

    // 1. 卡通量化（Cel Shading）
    if (uCelSteps > 0.5) {
        float lum = dot(c, vec3(0.299, 0.587, 0.114));
        float cel = ceil(lum * uCelSteps) / uCelSteps;
        c = c * (cel / max(lum, 0.001));
    }

    // 2. 伪方向光（顶部亮、底部暗）
    c *= clamp(vLight, 0.55, 1.15);

    // 3. 饱和度提升（让2D PNG在3D场景中更鲜艳）
    float gray = dot(c, vec3(0.299, 0.587, 0.114));
    c = mix(vec3(gray), c, 1.35);

    // 4. Tint叠加
    c *= uTint.rgb;

    gl_FragColor = vec4(clamp(c, 0.0, 1.0), tex.a);
}
