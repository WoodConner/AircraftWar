// 真3D实体飞机顶点着色器
// 支持：Phong漫反射 + 3步Cel量化 + 翻滚(Roll) + 描边Pass
precision highp float;

attribute vec3 aPosition;   // 模型空间顶点 (x,y,z)
attribute vec3 aNormal;     // 顶点法线
attribute vec3 aColor;      // 顶点颜色 (RGB)

uniform mat4  uMVP;         // Model-View-Projection
uniform float uBankRad;     // 翻滚角（弧度），仅英雄飞机非零
uniform float uOutline;     // 1=描边Pass（沿法线膨胀输出纯黑）

varying vec3  vColor;
varying float vDiffuse;     // 量化后的漫射系数

void main() {
    // ── Roll旋转（绕Z轴 = 飞机前向轴旋转）
    float c = cos(uBankRad), s = sin(uBankRad);
    vec3 pos = vec3(
        aPosition.x * c - aPosition.y * s,
        aPosition.x * s + aPosition.y * c,
        aPosition.z
    );
    vec3 nor = normalize(vec3(
        aNormal.x * c - aNormal.y * s,
        aNormal.x * s + aNormal.y * c,
        aNormal.z
    ));

    // ── 描边：沿模型法线方向膨胀4%
    if (uOutline > 0.5) {
        pos += nor * 0.04;
    }

    gl_Position = uMVP * vec4(pos, 1.0);

    // ── 方向光（从左上方前方照射，模拟俯视场景阳光）
    vec3 L = normalize(vec3(0.35, 1.0, -0.45));
    float diff = max(0.0, dot(nor, L));

    // ── 3步Cel量化（0/0.33/0.67/1.0四个亮度区间）
    vDiffuse = floor(diff * 3.0 + 0.5) / 3.0;
    vColor   = aColor;
}
