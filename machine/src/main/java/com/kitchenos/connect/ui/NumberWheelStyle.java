package com.kitchenos.connect.ui;

import android.graphics.LinearGradient;
import android.graphics.Shader;

/** Shared focus mask for the Home and dialog number wheels. */
final class NumberWheelStyle {
    static final int HEIGHT_DP=360, WINDOW_DP=336;
    static Shader fade(float height){
        return new LinearGradient(0,0,0,height,
                new int[]{0x00ffffff,0x1fffffff,0x75ffffff,0xffffffff,0xffffffff,0x75ffffff,0x1fffffff,0x00ffffff},
                new float[]{0,.07f,.28f,.38f,.62f,.72f,.93f,1},Shader.TileMode.CLAMP);
    }
    private NumberWheelStyle(){}
}
