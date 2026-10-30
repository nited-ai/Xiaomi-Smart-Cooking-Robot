package com.kitchenos.connect.kitchen;

import android.content.Context;
import com.kitchenos.common.Recipe;
import com.kitchenos.connect.RecipeStore;
import java.util.*;

/** Device imports are shared on the appliance; account AI drafts remain in that account's area. */
public final class KitchenRecipes {
    public static List<Recipe> all(Context context){
        List<Recipe> result=new ArrayList<>();String uid=new CloudSession(context).account().optString("uid");
        if(!uid.isEmpty())result.addAll(new RecipeStore(context,uid).all());
        Set<String> ids=new HashSet<>();for(Recipe r:result)ids.add(r.id);
        for(Recipe r:new RecipeStore(context).all())if(ids.add(r.id))result.add(r);
        return result;
    }
    public static void savePrivate(Context context,Recipe recipe,String expectedUid)throws Exception{
        if(expectedUid.isEmpty()||!expectedUid.equals(new CloudSession(context).account().optString("uid")))throw new IllegalStateException("Dein Konto hat sich geändert");
        new RecipeStore(context,expectedUid).save(recipe);
    }
    public static void delete(Context context,Recipe recipe)throws Exception{
        String uid=new CloudSession(context).account().optString("uid");
        if(!uid.isEmpty()){RecipeStore personal=new RecipeStore(context,uid);for(Recipe r:personal.all())if(r.id.equals(recipe.id)){personal.delete(recipe);return;}}
        new RecipeStore(context).delete(recipe);
    }
}
