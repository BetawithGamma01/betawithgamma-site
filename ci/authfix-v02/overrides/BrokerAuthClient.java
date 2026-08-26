package com.betawithgamma.microstructure;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Broker authentication/data preflight. Never writes credentials to evidence artifacts. */
public final class BrokerAuthClient {
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS).build();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public static final class DhanProbe {
        public final String clientId, tokenValidity, dataPlan, dataValidity, expiry;
        public final double spot;
        public final int atm;
        public final String ceSecurityId, peSecurityId;
        DhanProbe(String clientId,String tokenValidity,String dataPlan,String dataValidity,String expiry,double spot,int atm,String ce,String pe){
            this.clientId=clientId;this.tokenValidity=tokenValidity;this.dataPlan=dataPlan;this.dataValidity=dataValidity;
            this.expiry=expiry;this.spot=spot;this.atm=atm;this.ceSecurityId=ce;this.peSecurityId=pe;
        }
        public String instruments(){ return "NSE_FNO:"+ceSecurityId+",NSE_FNO:"+peSecurityId; }
        public String summary(){ return "DHAN AUTH OK | client="+clientId+" | token="+tokenValidity+" | dataPlan="+dataPlan+" | dataValidity="+dataValidity+" | NIFTY="+spot+" | expiry="+expiry+" | ATM="+atm+" | CE="+ceSecurityId+" PE="+peSecurityId; }
    }

    public static final class FyersProbe {
        public final String displayName;
        public final double niftyLtp;
        FyersProbe(String displayName,double niftyLtp){this.displayName=displayName;this.niftyLtp=niftyLtp;}
        public String summary(){return "FYERS AUTH OK | profile="+displayName+" | NIFTY LTP="+niftyLtp;}
    }

    public DhanProbe probeDhanAndResolveNifty(String configuredClientId,String token) throws Exception {
        require(configuredClientId,"Dhan client ID"); require(token,"Dhan access token");
        JSONObject profile = getJson("https://api.dhan.co/v2/profile", new String[][]{{"access-token",token}});
        String returnedClient = profile.optString("dhanClientId","").trim();
        if (returnedClient.isEmpty()) throw apiError("Dhan profile", profile);
        if (!returnedClient.equals(configuredClientId.trim())) throw new IllegalStateException("Dhan client ID mismatch: token belongs to "+returnedClient);
        String dataPlan=profile.optString("dataPlan","UNKNOWN");

        JSONObject underlying=new JSONObject(); underlying.put("UnderlyingScrip",13); underlying.put("UnderlyingSeg","IDX_I");
        String[][] headers={{"access-token",token},{"client-id",configuredClientId.trim()}};
        JSONObject exp=postJson("https://api.dhan.co/v2/optionchain/expirylist",underlying,headers);
        if (!"success".equalsIgnoreCase(exp.optString("status"))) throw apiError("Dhan expiry list",exp);
        JSONArray a=exp.optJSONArray("data"); if(a==null||a.length()==0) throw new IllegalStateException("Dhan expiry list empty");
        LocalDate today=LocalDate.now(); LocalDate nearest=null;
        for(int i=0;i<a.length();i++){ LocalDate d=LocalDate.parse(a.getString(i)); if(!d.isBefore(today)&&(nearest==null||d.isBefore(nearest))) nearest=d; }
        if(nearest==null) throw new IllegalStateException("Dhan has no active NIFTY expiry");
        underlying.put("Expiry",nearest.toString());
        JSONObject chain=postJson("https://api.dhan.co/v2/optionchain",underlying,headers);
        if (!"success".equalsIgnoreCase(chain.optString("status"))) throw apiError("Dhan option chain",chain);
        JSONObject data=chain.optJSONObject("data"); if(data==null) throw new IllegalStateException("Dhan option chain data missing");
        double spot=data.optDouble("last_price",Double.NaN); if(!Double.isFinite(spot)||spot<=0) throw new IllegalStateException("Dhan NIFTY spot invalid");
        int atm=(int)(Math.floor(spot/50.0+0.5)*50.0);
        JSONObject oc=data.optJSONObject("oc"); if(oc==null) throw new IllegalStateException("Dhan option chain oc missing");
        JSONObject row=findStrike(oc,atm); if(row==null) throw new IllegalStateException("Dhan ATM strike "+atm+" missing");
        String ce=securityId(row.optJSONObject("ce")); String pe=securityId(row.optJSONObject("pe"));
        return new DhanProbe(returnedClient,profile.optString("tokenValidity","UNKNOWN"),dataPlan,profile.optString("dataValidity","UNKNOWN"),nearest.toString(),spot,atm,ce,pe);
    }

    public String buildFyersLoginUrl(String appId,String redirectUri,String state) {
        require(appId,"FYERS App ID"); require(redirectUri,"FYERS redirect URI");
        String s=(state==null||state.isBlank())?"microstructure_android":state.trim();
        return "https://api-t1.fyers.in/api/v3/generate-authcode?client_id="+enc(appId.trim())+"&redirect_uri="+enc(redirectUri.trim())+"&response_type=code&state="+enc(s);
    }

    public String exchangeFyersAuthCode(String appId,String secret,String authCodeOrUrl) throws Exception {
        require(appId,"FYERS App ID");require(secret,"FYERS secret");require(authCodeOrUrl,"FYERS auth_code / redirect URL");
        String code=extractAuthCode(authCodeOrUrl.trim());
        JSONObject body=new JSONObject(); body.put("grant_type","authorization_code"); body.put("appIdHash",sha256Hex(appId.trim()+":"+secret.trim())); body.put("code",code);
        JSONObject resp=postJson("https://api-t1.fyers.in/api/v3/validate-authcode",body,new String[0][0]);
        String token=resp.optString("access_token","").trim();
        if(token.isEmpty()) throw apiError("FYERS validate-authcode",resp);
        return token;
    }

    public FyersProbe probeFyers(String appId,String token) throws Exception {
        require(appId,"FYERS App ID");require(token,"FYERS access token");
        String auth=appId.trim()+":"+token.trim();
        String[][] h={{"Authorization",auth},{"version","3"}};
        JSONObject profile=getJson("https://api-t1.fyers.in/api/v3/profile",h);
        if(!"ok".equalsIgnoreCase(profile.optString("s"))) throw apiError("FYERS profile",profile);
        String name=profile.optString("data",profile.optString("name","profile-ok"));
        HttpUrl q=Objects.requireNonNull(HttpUrl.parse("https://api-t1.fyers.in/data/quotes")).newBuilder().addQueryParameter("symbols","NSE:NIFTY50-INDEX").build();
        JSONObject quotes=getJson(q.toString(),h);
        if(!"ok".equalsIgnoreCase(quotes.optString("s"))) throw apiError("FYERS quotes",quotes);
        double ltp=extractFyersLtp(quotes);
        return new FyersProbe(name.length()>80?name.substring(0,80):name,ltp);
    }

    private double extractFyersLtp(JSONObject quotes){
        JSONArray d=quotes.optJSONArray("d"); if(d==null||d.length()==0) return Double.NaN;
        JSONObject first=d.optJSONObject(0); if(first==null)return Double.NaN;
        JSONObject v=first.optJSONObject("v"); if(v==null)return Double.NaN;
        return v.optDouble("lp",Double.NaN);
    }
    private static JSONObject findStrike(JSONObject oc,int atm){ Iterator<String> it=oc.keys(); while(it.hasNext()){ String k=it.next(); try{ if((int)Math.round(Double.parseDouble(k))==atm)return oc.optJSONObject(k); }catch(Exception ignored){} } return null; }
    private static String securityId(JSONObject side){ if(side==null) throw new IllegalStateException("option side missing"); String s=String.valueOf(side.opt("security_id")).trim(); if(!s.matches("\\d+")) throw new IllegalStateException("invalid security_id"); return s; }

    private JSONObject getJson(String url,String[][] headers) throws Exception { Request.Builder b=new Request.Builder().url(url).get().header("Accept","application/json"); for(String[] h:headers)b.header(h[0],h[1]); return executeJson(b.build()); }
    private JSONObject postJson(String url,JSONObject body,String[][] headers) throws Exception { Request.Builder b=new Request.Builder().url(url).post(RequestBody.create(body.toString(),JSON)).header("Accept","application/json").header("Content-Type","application/json"); for(String[] h:headers)b.header(h[0],h[1]); return executeJson(b.build()); }
    private JSONObject executeJson(Request r) throws Exception { try(Response resp=client.newCall(r).execute()){ String s=resp.body()!=null?resp.body().string():""; JSONObject j; try{j=new JSONObject(s);}catch(Exception e){throw new IOException("HTTP "+resp.code()+" non-JSON response");} if(!resp.isSuccessful()) throw new IOException("HTTP "+resp.code()+" "+safeApi(j)); return j; } }
    private static IllegalStateException apiError(String label,JSONObject j){ return new IllegalStateException(label+" rejected: "+safeApi(j)); }
    private static String safeApi(JSONObject j){ String m=j.optString("message",j.optString("remarks",j.optString("status","API error"))); String c=String.valueOf(j.opt("code")); return (c==null||"null".equals(c)?"":c+" ")+m; }
    private static void require(String s,String n){ if(s==null||s.trim().isEmpty())throw new IllegalArgumentException(n+" required"); }
    private static String enc(String s){ return URLEncoder.encode(s,StandardCharsets.UTF_8); }
    private static String sha256Hex(String s) throws Exception { byte[] d=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)); StringBuilder b=new StringBuilder();for(byte x:d)b.append(String.format(Locale.ROOT,"%02x",x));return b.toString(); }
    static String extractAuthCode(String raw){
        if(!raw.contains("://")&&!raw.contains("?")) return raw;
        try{ URI u=URI.create(raw); String q=u.getRawQuery(); if(q==null) throw new IllegalArgumentException("Redirect URL has no query"); for(String p:q.split("&")){String[] kv=p.split("=",2);if(kv.length==2&&(kv[0].equals("auth_code")||kv[0].equals("code")))return URLDecoder.decode(kv[1],StandardCharsets.UTF_8);} }catch(Exception e){ if(e instanceof IllegalArgumentException) throw (IllegalArgumentException)e; }
        throw new IllegalArgumentException("auth_code not found in FYERS redirect URL");
    }
}
