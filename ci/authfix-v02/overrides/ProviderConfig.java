package com.betawithgamma.microstructure;

import java.util.*;

public final class ProviderConfig {
    public static final class DhanInstrument {
        public final String segment;
        public final String securityId;
        DhanInstrument(String segment, String securityId) { this.segment = segment; this.securityId = securityId; }
    }

    public final String dhanClientId;
    public final String dhanToken;
    public final List<DhanInstrument> dhanInstruments;
    public final String fyersAppId;
    public final String fyersSecret;
    public final String fyersRedirectUri;
    public final String fyersToken;
    public final List<String> fyersSymbols;
    public final boolean dhanQuote, dhan20, dhan200, fyersTbt;

    public ProviderConfig(String dhanClientId, String dhanToken, String dhanRaw,
                          String fyersAppId, String fyersSecret, String fyersRedirectUri, String fyersToken, String fyersRaw,
                          boolean dhanQuote, boolean dhan20, boolean dhan200, boolean fyersTbt) {
        this.dhanClientId = nz(dhanClientId).trim();
        this.dhanToken = nz(dhanToken).trim();
        this.dhanInstruments = parseDhan(nz(dhanRaw));
        this.fyersAppId = nz(fyersAppId).trim();
        this.fyersSecret = nz(fyersSecret).trim();
        this.fyersRedirectUri = nz(fyersRedirectUri).trim();
        this.fyersToken = nz(fyersToken).trim();
        this.fyersSymbols = parseSymbols(nz(fyersRaw));
        this.dhanQuote = dhanQuote; this.dhan20 = dhan20; this.dhan200 = dhan200; this.fyersTbt = fyersTbt;
    }
    private static String nz(String s){return s==null?"":s;}

    static List<DhanInstrument> parseDhan(String raw) {
        List<DhanInstrument> out = new ArrayList<>();
        for (String p : raw.split("[,;\\n]+")) {
            p = p.trim(); if (p.isEmpty()) continue;
            String[] bits = p.split(":", 2);
            if (bits.length != 2 || bits[0].isBlank() || bits[1].isBlank()) throw new IllegalArgumentException("Dhan instrument must be SEGMENT:SECURITY_ID");
            out.add(new DhanInstrument(bits[0].trim(), bits[1].trim()));
        }
        return out;
    }
    static List<String> parseSymbols(String raw) { List<String> out=new ArrayList<>(); for(String p:raw.split("[,;\\n]+")){p=p.trim();if(!p.isEmpty())out.add(p);} return out; }

    public void validate() {
        if (!(dhanQuote || dhan20 || dhan200 || fyersTbt)) throw new IllegalArgumentException("Enable at least one feed");
        if ((dhanQuote || dhan20 || dhan200) && (dhanClientId.isEmpty() || dhanToken.isEmpty())) throw new IllegalArgumentException("Dhan client ID/token required — run Dhan auth test first");
        if ((dhanQuote || dhan20 || dhan200) && dhanInstruments.isEmpty()) throw new IllegalArgumentException("Dhan instruments missing — use TEST DHAN + AUTO ATM first");
        if (dhan20 && dhanInstruments.size() > 50) throw new IllegalArgumentException("Dhan 20-depth max 50 instruments");
        if (fyersTbt && (fyersAppId.isEmpty() || fyersToken.isEmpty() || fyersSymbols.isEmpty())) throw new IllegalArgumentException("FYERS App ID/token/symbol required — complete FYERS auth test first");
        if (fyersTbt && fyersSymbols.size() > 5) throw new IllegalArgumentException("One FYERS TBT connection supports max 5 symbols in this evidence lane");
    }
}
