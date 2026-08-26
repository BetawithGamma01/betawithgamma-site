package com.betawithgamma.microstructure;

import org.junit.Test;
import static org.junit.Assert.*;

public class BrokerAuthClientTest {
    @Test public void extractsAuthCodeFromRedirect(){assertEquals("abc+123",BrokerAuthClient.extractAuthCode("https://example.com/cb?state=x&auth_code=abc%2B123"));}
    @Test public void acceptsRawAuthCode(){assertEquals("raw.jwt.code",BrokerAuthClient.extractAuthCode("raw.jwt.code"));}
    @Test(expected=IllegalArgumentException.class) public void rejectsRedirectWithoutCode(){BrokerAuthClient.extractAuthCode("https://example.com/cb?state=x");}
}
