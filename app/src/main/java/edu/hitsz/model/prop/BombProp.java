package edu.hitsz.model.prop;
import android.graphics.Bitmap;
/** 炸弹道具：清屏消灭所有敌机 */
public class BombProp extends AbstractProp {
    public BombProp(int x, int y, Bitmap img) { super(x, y, img, Type.BOMB); }
}
