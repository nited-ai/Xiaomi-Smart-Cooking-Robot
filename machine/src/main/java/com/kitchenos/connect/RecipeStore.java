package com.kitchenos.connect;

import android.content.Context;
import android.util.AtomicFile;
import com.kitchenos.common.Recipe;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class RecipeStore {
    private static final Object LOCK=new Object();
    private final File directory;
    public RecipeStore(Context c){this(c,"");}
    public RecipeStore(Context c,String uid){String folder="recipes";if(!uid.isEmpty())try{folder="private-recipes-"+com.kitchenos.common.Pairing.fingerprint(uid.getBytes(StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalStateException(e);}directory=new File(c.getFilesDir(),folder);if(!directory.isDirectory()&&!directory.mkdirs())throw new IllegalStateException("Rezeptspeicher nicht verfügbar");}
    public boolean save(Recipe recipe)throws Exception { synchronized(LOCK){
        File target=new File(directory,recipe.id+".json");
        if(target.exists()) {
            Recipe previous=read(target);
            if(!previous.json().toString().equals(recipe.json().toString()))throw new IOException("Rezept-ID bereits mit anderem Inhalt vorhanden");
            return false;
        }
        File[] stored=directory.listFiles((d,n)->n.endsWith(".json"));
        if(stored!=null&&stored.length>=500)throw new IOException("Rezeptspeicher voll (500 Rezepte)");
        AtomicFile file=new AtomicFile(target);FileOutputStream out=null;
        try{out=file.startWrite();out.write(recipe.json().toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);}
        catch(Exception e){if(out!=null)file.failWrite(out);throw e;}
        if(!read(target).json().toString().equals(recipe.json().toString()))throw new IOException("Speichern nicht bestätigt");
        return true;
    }}
    private Recipe read(File file)throws Exception {
        if(file.length()>Recipe.MAX_BYTES)throw new IOException("Rezept zu groß");
        return Recipe.parse(new String(java.nio.file.Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));
    }
    public java.util.List<Recipe> all(){ synchronized(LOCK){
        List<Recipe> result=new ArrayList<>();File[] files=directory.listFiles((d,n)->n.endsWith(".json"));if(files==null)return result;
        Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
        for(File f:files)try{result.add(read(f));}catch(Exception ignored){}return result;
    }}
    public void delete(Recipe r)throws IOException{synchronized(LOCK){if(!new File(directory,r.id+".json").delete())throw new IOException("Rezept konnte nicht gelöscht werden");}}
}
