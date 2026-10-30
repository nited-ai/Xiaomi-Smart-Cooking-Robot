package com.kitchenos.common;

import android.net.Uri;
import java.net.*;
import java.security.*;
import java.security.cert.X509Certificate;
import javax.net.ssl.*;

/** Explicit device certificate pin; never trusts a different LAN device. */
public final class Pairing {
    public final String host,token,pin;
    public Pairing(String host,String token,String pin) {
        if(host==null||!host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}") || !local(host))throw new IllegalArgumentException("Lokale IPv4-Adresse der Maschine erforderlich");
        if(token==null||pin==null||!token.matches("[a-f0-9]{32}") || !pin.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Kopplungscode unvollständig");
        this.host=host;this.token=token;this.pin=pin;
    }
    private static boolean local(String host) {
        try{InetAddress a=InetAddress.getByName(host);return a.isSiteLocalAddress()||a.isLoopbackAddress()||a.isLinkLocalAddress();}catch(Exception e){return false;}
    }
    public static Pairing parse(String value) {
        Uri u=Uri.parse(value.trim());
        if(!"kitchenos".equals(u.getScheme())||!"pair".equals(u.getHost()))throw new IllegalArgumentException("KitchenOS-Kopplungscode erforderlich");
        return new Pairing(u.getQueryParameter("host"),u.getQueryParameter("token"),u.getQueryParameter("pin"));
    }
    public String uri() {return new Uri.Builder().scheme("kitchenos").authority("pair").appendQueryParameter("host",host).appendQueryParameter("token",token).appendQueryParameter("pin",pin).build().toString();}
    public static String fingerprint(byte[] bytes) throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);
        char[] hex=new char[digest.length*2];String digits="0123456789abcdef";
        for(int i=0;i<digest.length;i++){int value=digest[i]&255;hex[2*i]=digits.charAt(value>>>4);hex[2*i+1]=digits.charAt(value&15);}
        return new String(hex);
    }
    public HttpsURLConnection connection(String path) throws Exception {
        return connection(path,null);
    }
    public HttpsURLConnection connection(String path,android.net.Network network) throws Exception {
        TrustManager[] managers={new X509TrustManager(){
            public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
            public void checkClientTrusted(X509Certificate[] c,String a)throws java.security.cert.CertificateException{throw new java.security.cert.CertificateException("Client cert not used");}
            public void checkServerTrusted(X509Certificate[] c,String a)throws java.security.cert.CertificateException{
                try{if(c.length==0||!MessageDigest.isEqual(pin.getBytes(java.nio.charset.StandardCharsets.US_ASCII),fingerprint(c[0].getEncoded()).getBytes(java.nio.charset.StandardCharsets.US_ASCII)))throw new Exception();}
                catch(Exception e){throw new java.security.cert.CertificateException("Zertifikat stimmt nicht mit der gekoppelten Maschine überein");}
            }
        }};
        SSLContext tls=SSLContext.getInstance("TLS");tls.init(null,managers,new SecureRandom());
        URL url=new URL("https://"+host+":8765"+path);
        HttpsURLConnection c=(HttpsURLConnection)(network==null?url.openConnection():network.openConnection(url));
        c.setSSLSocketFactory(tls.getSocketFactory());
        // Host identity is the out-of-band certificate fingerprint, not a public DNS name.
        c.setHostnameVerifier((name,session)->name.equals(host));
        c.setInstanceFollowRedirects(false);c.setConnectTimeout(8000);c.setReadTimeout(10000);
        c.setRequestProperty("Authorization","Bearer "+token);return c;
    }
}
