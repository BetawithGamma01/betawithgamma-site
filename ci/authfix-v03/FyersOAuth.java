package com.betawithgamma.microstructure;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;

/** Pure OAuth callback validation helpers. No Android dependencies; unit-testable. */
public final class FyersOAuth {
    private static final SecureRandom RNG = new SecureRandom();
    private FyersOAuth() {}

    public static String newState() {
        byte[] b = new byte[24];
        RNG.nextBytes(b);
        StringBuilder s = new StringBuilder(b.length * 2);
        for (byte x : b) s.append(String.format(Locale.ROOT, "%02x", x));
        return s.toString();
    }

    public static String requireValidCallback(String registeredRedirect, String callbackUrl, String expectedState) {
        if (registeredRedirect == null || registeredRedirect.trim().isEmpty())
            throw new IllegalArgumentException("Registered FYERS redirect URI required");
        if (callbackUrl == null || callbackUrl.trim().isEmpty())
            throw new IllegalArgumentException("FYERS callback URL missing");
        if (expectedState == null || expectedState.isEmpty())
            throw new IllegalArgumentException("OAuth state missing");

        URI reg = parse(registeredRedirect.trim(), "registered redirect");
        URI cb = parse(callbackUrl.trim(), "callback");
        if (!sameTarget(reg, cb)) throw new IllegalArgumentException("FYERS callback target mismatch");

        String code = null;
        String state = null;
        String q = cb.getRawQuery();
        if (q != null) {
            for (String p : q.split("&")) {
                String[] kv = p.split("=", 2);
                if (kv.length != 2) continue;
                String k = decode(kv[0]);
                String v = decode(kv[1]);
                if ("auth_code".equals(k) || "code".equals(k)) code = v;
                else if ("state".equals(k)) state = v;
            }
        }
        if (code == null || code.isBlank()) throw new IllegalArgumentException("FYERS callback has no auth_code");
        if (state == null || !constantTimeEquals(expectedState, state))
            throw new IllegalArgumentException("FYERS OAuth state mismatch");
        return code;
    }

    public static boolean isRedirectTarget(String registeredRedirect, String candidateUrl) {
        try { return sameTarget(parse(registeredRedirect, "registered redirect"), parse(candidateUrl, "candidate")); }
        catch (RuntimeException e) { return false; }
    }

    private static boolean sameTarget(URI a, URI b) {
        return eqIgnoreCase(a.getScheme(), b.getScheme())
                && eqIgnoreCase(a.getHost(), b.getHost())
                && effectivePort(a) == effectivePort(b)
                && normalizePath(a.getPath()).equals(normalizePath(b.getPath()));
    }

    private static int effectivePort(URI u) {
        if (u.getPort() >= 0) return u.getPort();
        if ("https".equalsIgnoreCase(u.getScheme())) return 443;
        if ("http".equalsIgnoreCase(u.getScheme())) return 80;
        return -1;
    }

    private static String normalizePath(String p) {
        if (p == null || p.isEmpty()) return "/";
        if (p.length() > 1 && p.endsWith("/")) return p.substring(0, p.length() - 1);
        return p;
    }

    private static boolean eqIgnoreCase(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    private static URI parse(String s, String label) {
        try {
            URI u = URI.create(s);
            if (u.getScheme() == null || u.getHost() == null) throw new IllegalArgumentException();
            return u;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid " + label + " URI");
        }
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        int diff = x.length ^ y.length;
        int n = Math.max(x.length, y.length);
        for (int i = 0; i < n; i++) {
            byte xb = i < x.length ? x[i] : 0;
            byte yb = i < y.length ? y[i] : 0;
            diff |= xb ^ yb;
        }
        return diff == 0;
    }
}
