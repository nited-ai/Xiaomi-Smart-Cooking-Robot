package com.kitchenos.connect.ui;

import android.view.View;
import org.json.*;
import java.util.*;
import java.util.function.Supplier;

/** In-process MCP tool endpoint. Never exported through the recipe pairing token. */
public final class ApplianceTools {
    private static final String PROTOCOL="2025-06-18";
    private final Supplier<String> account;
    private final LinkedHashMap<String,Entry> entries=new LinkedHashMap<>();
    private String revision,uid;private JSONObject context=new JSONObject();
    private static final class Entry {final View view;final Runnable action;Entry(View v,Runnable a){view=v;action=a;}}
    public ApplianceTools(Supplier<String> account){this.account=account;reset();}
    public void reset(){context=new JSONObject();entries.clear();revision=UUID.randomUUID().toString();uid=account.get();}
    public void context(JSONObject value){try{context=new JSONObject(value.toString());}catch(JSONException invalid){context=new JSONObject();}}
    public JSONObject contextSnapshot()throws JSONException{return new JSONObject(context.toString());}
    public void invalidate(){revision=UUID.randomUUID().toString();}
    public void action(String id,View view,Runnable action){entries.put(id,new Entry(view,action));}
    public JSONObject scene()throws JSONException{
        if(!uid.equals(account.get()))throw new JSONException("Das Konto hat sich geändert");
        JSONArray controls=new JSONArray();for(Map.Entry<String,Entry> row:entries.entrySet()){
            View view=row.getValue().view;if(!view.isShown())continue;
            JSONObject item=new JSONObject().put("id",row.getKey()).put("label",String.valueOf(view.getContentDescription())).put("enabled",view.isEnabled());
            if(view instanceof ParameterDial){ParameterDial dial=(ParameterDial)view;double scale=row.getKey().endsWith("speed")?10.0:1.0;item.put("value",dial.known()?dial.value()/scale:JSONObject.NULL).put("minimum",dial.minimum()/scale).put("maximum",dial.maximum()/scale).put("step",dial.tick()/scale).put("unit",row.getKey().endsWith("speed")?"Stufe":row.getKey().endsWith("temperature")?"°C":"Sekunden").put("adjustable",true);}
            controls.put(item);
        }
        return new JSONObject().put("revision",revision).put("controls",controls).put("context",new JSONObject(context.toString()));
    }
    public JSONObject rpc(JSONObject request){
        Object id=request.opt("id");try{
            if(!"2.0".equals(request.optString("jsonrpc")))throw new IllegalArgumentException("Ungültige Anfrage");
            JSONObject result;
            switch(request.getString("method")){
                case "initialize": result=new JSONObject().put("protocolVersion",PROTOCOL).put("serverInfo",new JSONObject().put("name","kalomatik-appliance").put("version","0.4")).put("capabilities",new JSONObject().put("tools",new JSONObject()));break;
                case "tools/list": result=new JSONObject().put("tools",new JSONArray().put(tool("kitchen_ui_read","Aktuelle bedienbare Oberfläche lesen",new JSONObject())).put(tool("kitchen_ui_activate","Sichtbares Element auswählen; ein Maschinenstart braucht weiterhin die Bestätigung vor Ort",new JSONObject().put("revision",new JSONObject().put("type","string")).put("id",new JSONObject().put("type","string")))).put(tool("kitchen_ui_set_value","Kreis einstellen; startet und verändert keinen laufenden Kochvorgang",new JSONObject().put("revision",new JSONObject().put("type","string")).put("id",new JSONObject().put("type","string")).put("value",new JSONObject().put("type","number")))));break;
                case "tools/call":
                    JSONObject params=request.getJSONObject("params"),args=params.optJSONObject("arguments");String name=params.getString("name");
                    if(!uid.equals(account.get()))throw new IllegalArgumentException("Das Konto hat sich geändert");
                    if(name.equals("kitchen_ui_read"))result=content(scene());
                    else if(name.equals("kitchen_ui_activate")){
                        if(args==null||!revision.equals(args.optString("revision")))throw new IllegalArgumentException("Die Ansicht hat sich geändert");
                        Entry entry=entries.get(args.getString("id"));if(entry==null||!entry.view.isShown()||!entry.view.isEnabled())throw new IllegalArgumentException("Dieses Element ist nicht verfügbar");
                        entry.action.run();result=content(new JSONObject().put("selected",true));
                    }else if(name.equals("kitchen_ui_set_value")){
                        if(args==null||!revision.equals(args.optString("revision")))throw new IllegalArgumentException("Die Ansicht hat sich geändert");
                        String controlId=args.getString("id");Entry entry=entries.get(controlId);if(entry==null||!(entry.view instanceof ParameterDial)||!entry.view.isShown()||!entry.view.isEnabled())throw new IllegalArgumentException("Dieser Kreis ist nicht verfügbar");
                        double requested=args.getDouble("value"),scaled=requested*(controlId.endsWith("speed")?10:1);if(!Double.isFinite(scaled)||scaled!=Math.rint(scaled)||!((ParameterDial)entry.view).accepts((int)scaled))throw new IllegalArgumentException("Dieser Wert ist nicht einstellbar");
                        ((ParameterDial)entry.view).value((int)scaled);result=content(new JSONObject().put("selected",true).put("value",requested));
                    }else throw new IllegalArgumentException("Unbekanntes Werkzeug");break;
                default:return error(id,-32601,"Unbekannte Methode");
            }
            return new JSONObject().put("jsonrpc","2.0").put("id",id==null?JSONObject.NULL:id).put("result",result);
        }catch(Exception error){return error(id,-32602,error.getMessage()==null?"Anfrage konnte nicht ausgeführt werden":error.getMessage());}
    }
    private JSONObject tool(String name,String description,JSONObject properties)throws JSONException{return new JSONObject().put("name",name).put("description",description).put("inputSchema",new JSONObject().put("type","object").put("properties",properties).put("required",new JSONArray(properties.keys().hasNext()?properties.has("value")?new String[]{"revision","id","value"}:new String[]{"revision","id"}:new String[]{})).put("additionalProperties",false));}
    private JSONObject content(JSONObject value)throws JSONException{return new JSONObject().put("content",new JSONArray().put(new JSONObject().put("type","text").put("text",value.toString()))).put("structuredContent",value);}
    private JSONObject error(Object id,int code,String message){try{return new JSONObject().put("jsonrpc","2.0").put("id",id==null?JSONObject.NULL:id).put("error",new JSONObject().put("code",code).put("message",message));}catch(JSONException impossible){throw new IllegalStateException(impossible);}}
}
