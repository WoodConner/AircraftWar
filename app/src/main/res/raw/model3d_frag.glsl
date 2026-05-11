// 真3D实体飞机片元着色器
// 支持：Cel量化着色 + 描边Pass + 阴影Pass + Tint叠加
precision mediump float;

varying vec3  vColor;
varying float vDiffuse;

uniform vec4  uTint;       // 正常=(1,1,1,1)  阴影=(0,0,0,0.38)
uniform float uOutline;    // 1=描边Pass

void main() {
    // ── 描边Pass：纯深色
    if (uOutline > 0.5) {
        gl_FragColor = vec4(0.02, 0.02, 0.05, 0.88);
        return;
    }

    // ── 阴影Pass (uTint.a < 0.9 时为阴影)
    if (uTint.a < 0.9) {
        gl_FragColor = vec4(0.0, 0.0, 0.0, uTint.a);
        return;
    }

    // ── 主体Pass：环境光 + 漫反射Cel量化
    float ambient = 0.28;
    vec3  col     = vColor * (ambient + vDiffuse * (1.0 - ambient));

    // 轮廓提亮：暗面加少量侧光
    col += vColor * (1.0 - vDiffuse) * 0.08;

    gl_FragColor = vec4(clamp(col * uTint.rgb, 0.0, 1.0), 1.0);
}
