package com.betawithgamma.microstructure;

import org.junit.Test;
import static org.junit.Assert.*;

public class FyersOAuthTest {
    private static final String R="https://127.0.0.1:8765/oauth/callback";

    @Test public void acceptsExactHttpsRedirectAndState(){
        assertEquals("abc+123",FyersOAuth.requireValidCallback(R,R+"?auth_code=abc%2B123&state=s1","s1"));
    }
    @Test public void acceptsTrailingSlashEquivalence(){
        assertEquals("c",FyersOAuth.requireValidCallback(R+"/",R+"?auth_code=c&state=x","x"));
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsWrongState(){
        FyersOAuth.requireValidCallback(R,R+"?auth_code=c&state=evil","expected");
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsWrongPort(){
        FyersOAuth.requireValidCallback(R,"https://127.0.0.1:9999/oauth/callback?auth_code=c&state=x","x");
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsWrongPath(){
        FyersOAuth.requireValidCallback(R,"https://127.0.0.1:8765/other?auth_code=c&state=x","x");
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsMissingCode(){
        FyersOAuth.requireValidCallback(R,R+"?state=x","x");
    }
    @Test public void targetDetectionIgnoresQueryOnly(){
        assertTrue(FyersOAuth.isRedirectTarget(R,R+"?auth_code=c&state=x"));
        assertFalse(FyersOAuth.isRedirectTarget(R,"https://example.com/oauth/callback?auth_code=c&state=x"));
    }
    @Test public void stateHasEntropy(){
        String a=FyersOAuth.newState(),b=FyersOAuth.newState();
        assertNotEquals(a,b);assertTrue(a.length()>=32);assertTrue(b.length()>=32);
    }
}
