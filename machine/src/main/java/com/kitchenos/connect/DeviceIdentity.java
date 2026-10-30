package com.kitchenos.connect;

import android.content.Context;
import android.security.keystore.*;
import com.kitchenos.common.Pairing;
import java.math.BigInteger;
import java.security.*;
import java.util.Date;
import javax.net.ssl.*;
import javax.security.auth.x500.X500Principal;

public final class DeviceIdentity {
    private static final String ALIAS="KitchenOSRecipeReceiverTLSec1";
    public final SSLContext tls;
    public final String pin,token;
    public DeviceIdentity(Context c)throws Exception {
        KeyStore keys=KeyStore.getInstance("AndroidKeyStore");keys.load(null);
        if(!keys.containsAlias(ALIAS)) {
            KeyPairGenerator generator=KeyPairGenerator.getInstance("EC","AndroidKeyStore");
            generator.initialize(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_VERIFY)
                // Conscrypt passes the pre-hashed TLS signature to AndroidKeyStore.
                .setAlgorithmParameterSpec(new java.security.spec.ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_NONE,KeyProperties.DIGEST_SHA256)
                .setCertificateSubject(new X500Principal("CN=KitchenOS"))
                .setCertificateSerialNumber(BigInteger.ONE).setCertificateNotBefore(new Date(0))
                .setCertificateNotAfter(new Date(4102444800000L)).build());generator.generateKeyPair();
        }
        pin=Pairing.fingerprint(keys.getCertificate(ALIAS).getEncoded());
        android.content.SharedPreferences p=c.getSharedPreferences("receiver",0);
        String saved=p.getString("token",null);
        if(saved==null){byte[] random=new byte[16];new SecureRandom().nextBytes(random);StringBuilder s=new StringBuilder();for(byte b:random)s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));saved=s.toString();if(!p.edit().putString("token",saved).commit())throw new java.io.IOException("Kopplung konnte nicht gespeichert werden");}
        token=saved;
        // Account device proofs also use an EC key. TLS must never auto-select that
        // unrelated certificate: it would fail the pairing pin (or raw TLS signing).
        final PrivateKey privateKey=(PrivateKey)keys.getKey(ALIAS,null);
        final java.security.cert.Certificate[] savedChain=keys.getCertificateChain(ALIAS);
        final java.security.cert.X509Certificate[] chain=java.util.Arrays.copyOf(savedChain,savedChain.length,java.security.cert.X509Certificate[].class);
        X509ExtendedKeyManager manager=new X509ExtendedKeyManager(){
            private boolean ec(String type){return type!=null&&type.startsWith("EC");}
            public String[] getClientAliases(String type,java.security.Principal[] issuers){return null;}
            public String chooseClientAlias(String[] types,java.security.Principal[] issuers,java.net.Socket socket){return null;}
            public String[] getServerAliases(String type,java.security.Principal[] issuers){return ec(type)?new String[]{ALIAS}:null;}
            public String chooseServerAlias(String type,java.security.Principal[] issuers,java.net.Socket socket){return ec(type)?ALIAS:null;}
            public String chooseEngineServerAlias(String type,java.security.Principal[] issuers,SSLEngine engine){return ec(type)?ALIAS:null;}
            public java.security.cert.X509Certificate[] getCertificateChain(String alias){return ALIAS.equals(alias)?chain.clone():null;}
            public PrivateKey getPrivateKey(String alias){return ALIAS.equals(alias)?privateKey:null;}
        };
        tls=SSLContext.getInstance("TLS");tls.init(new KeyManager[]{manager},null,new SecureRandom());
    }
}
