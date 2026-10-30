package com.kitchenos.connect.ui;

import android.app.*;
import android.widget.Toast;

/** Carousel membership lives in the function's options, away from its photo and controls. */
public final class FunctionVisibilityMenu {
 public static AlertDialog show(Activity activity,String id,String title,Runnable changed){
  boolean visible=new FunctionOrder(activity).isVisible(id);
  AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(title)
   .setItems(new String[]{visible?"Aus dem Karussell ausblenden":"Im Karussell anzeigen"},(d,which)->{
    new FunctionOrder(activity).setVisible(id,!visible);changed.run();
    Toast.makeText(activity,visible?"Im Karussell ausgeblendet":"Wird im Karussell angezeigt",Toast.LENGTH_SHORT).show();
   }).setNegativeButton("Schließen",null).create();
  dialog.show();RotaryDialogs.bind(activity,dialog);return dialog;
 }
 private FunctionVisibilityMenu(){}
}
