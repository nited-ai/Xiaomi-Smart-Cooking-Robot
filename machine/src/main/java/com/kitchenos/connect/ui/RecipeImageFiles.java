package com.kitchenos.connect.ui;

import android.content.Context;
import java.io.File;

/** Keep downloaded recipe pictures in app storage, migrating the old disposable cache. */
final class RecipeImageFiles {
    static File directory(Context context,String name){File dir=new File(context.getFilesDir(),name);dir.mkdirs();return dir;}
    static File saved(Context context,String directory,String name){
        File file=new File(directory(context,directory),name);
        if(!file.exists()){File legacy=new File(new File(context.getCacheDir(),directory),name);if(legacy.isFile()&&!legacy.renameTo(file))return legacy;}
        return file;
    }
    private RecipeImageFiles(){}
}
