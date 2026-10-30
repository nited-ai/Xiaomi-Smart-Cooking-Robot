package com.kitchenos.common;

import android.net.Uri;

/** Camera-compatible HTTPS envelope. Credentials stay in the fragment, never an HTTP request. */
public final class SetupCode {
    public static String link(String value){Uri code=parse(value);return new Uri.Builder().scheme("https").authority("app.kalomatik.ai").path("/machine/").fragment(code.toString()).build().toString();}
    public static Uri parse(String value){
        if(value==null||value.length()>4096)throw new IllegalArgumentException("Ungültiger Einrichtungscode");
        Uri code=Uri.parse(value.trim());
        if("https".equals(code.getScheme())&&"app.kalomatik.ai".equals(code.getHost())&&"/machine/".equals(code.getPath())){
            if(code.getFragment()==null)throw new IllegalArgumentException();code=Uri.parse(code.getFragment());
        }
        if(!"kitchenos".equals(code.getScheme())||!("setup".equals(code.getHost())||"pair".equals(code.getHost())))throw new IllegalArgumentException();
        new Pairing(code.getQueryParameter("host"),code.getQueryParameter("token"),code.getQueryParameter("pin"));
        return code;
    }
    private SetupCode(){}
}
