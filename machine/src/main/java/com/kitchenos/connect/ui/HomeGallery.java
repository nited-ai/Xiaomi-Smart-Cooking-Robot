package com.kitchenos.connect.ui;

import java.util.*;
import org.json.JSONObject;

/** A pantry invitation is a gallery item, never a recipe or a cooking program. */
public final class HomeGallery {
    public static final class Item {
        public final JSONObject recipe;
        public final boolean pantry;
        private Item(JSONObject recipe){this.recipe=recipe;pantry=recipe==null;}
        public String key(){return pantry?"home.pantry":recipe.optString("foodIdentity",recipe.optString("id"));}
        public String title(){return pantry?"Erfasse deinen Vorrat":recipe.optString("title");}
    }
    public static List<Item> items(List<JSONObject> recipes,boolean pantry){
        ArrayList<Item> result=new ArrayList<>();for(JSONObject recipe:recipes)result.add(new Item(recipe));
        if(pantry)result.add(Math.min(1,result.size()),new Item(null));
        return result;
    }
    private HomeGallery(){}
}
