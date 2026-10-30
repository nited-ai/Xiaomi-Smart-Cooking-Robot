package com.kitchenos.connect.ui;

/** What a function does and what must be prepared before choosing its settings. */
public final class ModeHelp {
    public static org.json.JSONArray tools(String id){
        String[] tools;
        switch(id){
            case "lidless":tools=new String[]{"bowl","spatula"};break;
            case "whip":tools=new String[]{"stirring-attachment"};break;
            case "steam":tools=new String[]{"steamer","measuring-cup"};break;
            case "eggs":tools=new String[]{"bowl"};break;
            case "fry":case "dough":case "chop":case "mix":case "puree":case "grind":case "ice":case "turbo":case "caramel":tools=new String[]{"mixing-blade","spatula"};break;
            case "stir":tools=new String[]{"mixing-blade","blade-cover","spatula"};break;
            case "thicken":tools=new String[]{"mixing-blade","spatula"};break;
            case "warm":tools=new String[]{"mixing-blade","spatula"};break;
            case "clean":tools=new String[]{"bowl","lid"};break;
            default:tools=new String[]{"bowl","lid"};
        }
        return new org.json.JSONArray(java.util.Arrays.asList(tools));
    }
    public static String[] forMode(String id){return FunctionGuidance.get(id);}
    private ModeHelp(){}
}
