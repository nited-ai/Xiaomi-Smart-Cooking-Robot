package com.kitchenos.connect.ui;
import android.app.Activity;
import android.view.*;
/** Keep the existing machine footer reachable with one tap beneath auxiliary editors. */
public final class CookingWindows {
 public static void protectFooter(Activity activity,Window window){View root=activity.getWindow().getDecorView(),footer=root.findViewWithTag("assistant.footer");if(window==null||footer==null||!footer.isShown()||footer.getHeight()==0)return;int[] at=new int[2],origin=new int[2];footer.getLocationOnScreen(at);root.getLocationOnScreen(origin);int height=at[1]-origin[1];if(height<=0)return;window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);window.setGravity(Gravity.TOP);window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,height);}
 private CookingWindows(){}
}
