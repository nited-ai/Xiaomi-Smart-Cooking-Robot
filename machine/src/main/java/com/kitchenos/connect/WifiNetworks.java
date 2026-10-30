package com.kitchenos.connect;

import android.net.wifi.WifiConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Personal-network configuration. Never logs or persists credentials outside Android Wi-Fi. */
public final class WifiNetworks {
    public enum Security { OPEN, WPA2, WPA3, OWE, ENTERPRISE, WEP }
    public static Security security(String capabilities){String c=capabilities==null?"":capabilities.toUpperCase(Locale.ROOT);if(c.contains("EAP"))return Security.ENTERPRISE;if(c.contains("WEP"))return Security.WEP;if(c.contains("PSK"))return Security.WPA2;if(c.contains("SAE"))return Security.WPA3;if(c.contains("OWE"))return Security.OWE;return Security.OPEN;}
    public static WifiConfiguration configuration(String ssid,String password,Security security){
        if(ssid==null||ssid.isEmpty()||ssid.getBytes(StandardCharsets.UTF_8).length>32)throw new IllegalArgumentException("Netzwerkname muss 1–32 Bytes lang sein.");
        if(security==Security.ENTERPRISE||security==Security.WEP)throw new IllegalArgumentException("Dieses Netzwerk benötigt zusätzliche Anmeldeoptionen. Verwende dafür die Android-WLAN-Einstellungen.");
        String secret=password==null?"":password;WifiConfiguration config=new WifiConfiguration();config.SSID=quote(ssid);
        if(security==Security.OPEN)config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        else if(security==Security.OWE){config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.OWE);config.allowedProtocols.set(WifiConfiguration.Protocol.RSN);}
        else{boolean hex=security==Security.WPA2&&secret.matches("[a-fA-F0-9]{64}");int bytes=secret.getBytes(StandardCharsets.UTF_8).length;if(!hex&&(bytes<(security==Security.WPA3?1:8)||bytes>63))throw new IllegalArgumentException(security==Security.WPA3?"WPA3-Passwort muss 1–63 Bytes lang sein.":"WPA2-Passwort muss 8–63 Bytes lang sein.");config.allowedKeyManagement.set(security==Security.WPA3?WifiConfiguration.KeyMgmt.SAE:WifiConfiguration.KeyMgmt.WPA_PSK);config.preSharedKey=hex?secret:quote(secret);}
        return config;
    }
    public static String quote(String value){return "\""+value.replace("\\","\\\\").replace("\"","\\\"")+"\"";}
    private WifiNetworks(){}
}
