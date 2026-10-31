package com.kitchenos.connect.control;

/** Value-only validation shared by the local transport and provisioning receiver. */
public final class FactoryAccessSession {
    public final String socket;
    final String token;
    public final long expiresAtSeconds;
    public FactoryAccessSession(String socket,String token,long expiresAtSeconds,int appUid,long nowSeconds) {
        if(appUid<10000||socket==null||!socket.matches("xcr\\.access\\."+appUid+"\\.[a-f0-9]{16}")
                ||token==null||!token.matches("[a-f0-9]{64}")||nowSeconds<=0
                ||expiresAtSeconds<=nowSeconds||expiresAtSeconds-nowSeconds>14400)
            throw new IllegalArgumentException("Invalid or expired factory access session");
        this.socket=socket;this.token=token;this.expiresAtSeconds=expiresAtSeconds;
    }
    public String mode(String operation) {
        if(!"ID".equals(operation)&&!"SERVE".equals(operation)&&!"PROBE".equals(operation))
            throw new IllegalArgumentException("Unsupported app access operation");
        return operation;
    }
    public boolean accepts(String line,String operation) {
        return ("XCR1 OK "+mode(operation)+" uid=0 expires="+expiresAtSeconds).equals(line);
    }
}
