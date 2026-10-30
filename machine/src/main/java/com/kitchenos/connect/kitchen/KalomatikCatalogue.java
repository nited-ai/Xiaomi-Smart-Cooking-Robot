package com.kitchenos.connect.kitchen;
import com.kitchenos.common.*;
import org.json.JSONObject;
/** Machine adapter; phone and appliance use the same validated public recipe projection. */
public final class KalomatikCatalogue {
    public static final String ORIGIN=PublicRecipes.ORIGIN;
    private final PublicRecipes api=new PublicRecipes();
    public JSONObject search(String query,String cursor)throws Exception{return api.search(query,cursor);}
    public Recipe detail(String id)throws Exception{return api.detail(id);}
    public JSONObject rawDetail(String id)throws Exception{return api.rawDetail(id);}
    public static Recipe convert(JSONObject value)throws Exception{return PublicRecipes.convert(value);}
}
