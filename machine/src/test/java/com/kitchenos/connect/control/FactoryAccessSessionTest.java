package com.kitchenos.connect.control;

import org.junit.Test;
import static org.junit.Assert.*;

public class FactoryAccessSessionTest {
    private static final String TOKEN="a".repeat(64);
    private FactoryAccessSession session(String socket,String token,long expiry,long now){return new FactoryAccessSession(socket,token,expiry,10001,now);}
    private void rejected(Runnable action){try{action.run();fail("Expected rejection");}catch(IllegalArgumentException expected){}}
    @Test public void acceptsBoundedAppOwnedSocketAndToken(){
        FactoryAccessSession s=session("xcr.access.10001.0123456789abcdef",TOKEN,5000,1000);
        assertEquals(5000,s.expiresAtSeconds);assertEquals("ID",s.mode("ID"));
        assertEquals("SERVE",s.mode("SERVE"));assertEquals("PROBE",s.mode("PROBE"));
    }
    @Test public void rejectsOtherUidMalformedNamesAndToken(){
        rejected(()->session("xcr.access.10002.0123456789abcdef",TOKEN,5000,1000));
        rejected(()->session("/tmp/socket",TOKEN,5000,1000));
        rejected(()->session("xcr.access.10001.0123456789abcdef",TOKEN+"\n",5000,1000));
        rejected(()->session("xcr.access.10001.0123456789abcdef","A".repeat(64),5000,1000));
    }
    @Test public void rejectsExpiredOrUnboundedLeaseAndAdminOperation(){
        rejected(()->session("xcr.access.10001.0123456789abcdef",TOKEN,1000,1000));
        rejected(()->session("xcr.access.10001.0123456789abcdef",TOKEN,16000,1000));
        rejected(()->session("xcr.access.10001.0123456789abcdef",TOKEN,Long.MAX_VALUE,1000));
        rejected(()->session("xcr.access.10001.0123456789abcdef",TOKEN,5000,1000).mode("SHUTDOWN"));
    }
    @Test public void requiresExactRootIdentityAndLeaseHandshake(){
        FactoryAccessSession s=session("xcr.access.10001.0123456789abcdef",TOKEN,5000,1000);
        assertTrue(s.accepts("XCR1 OK ID uid=0 expires=5000","ID"));
        assertFalse(s.accepts("XCR1 OK ID uid=2000 expires=5000","ID"));
        assertFalse(s.accepts("XCR1 OK ID uid=0 expires=5001","ID"));
        assertFalse(s.accepts("XCR1 OK SERVE uid=0 expires=5000","ID"));
        assertFalse(s.accepts("XCR1 ERROR UNAUTHORIZED","ID"));
    }
}
