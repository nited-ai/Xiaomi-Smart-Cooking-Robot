package com.kitchenos.connect.ui;

import java.util.*;
import java.util.function.Function;

/** Home curation is independent of the carousel, so a ranked feed can replace the fallback later. */
public final class FeaturedRecipes {
    private FeaturedRecipes() {}

    public static <T> List<T> select(List<T> favorites,List<T> suggestions,int minimum,Function<T,String> identity) {
        LinkedHashMap<String,T> selected=new LinkedHashMap<>();
        for(T recipe:favorites)selected.putIfAbsent(identity.apply(recipe),recipe);
        for(T recipe:suggestions){
            if(selected.size()>=minimum)break;
            selected.putIfAbsent(identity.apply(recipe),recipe);
        }
        return new ArrayList<>(selected.values());
    }
}
