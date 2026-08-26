package com.betawithgamma.microstructure;

import okhttp3.*;
import okio.ByteString;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.*;

/** Raw FYERS TBT capture aligned to fyers-apiv3 3.1.15 observed connection mechanics. */
public final class FyersTbtConnector {
    private final EvidenceWriter writer;
    private final ProviderConfig cfg;
    private final OkHttpClient client = new OkHttpClient.Builder().retryOnConnectionFailure(true).build();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private WebSocket socket;
    private volatile boolean stopping;

    public FyersTbtConnector(EvidenceWriter writer, ProviderConfig cfg) { this.writer=writer; this.cfg=cfg; }

    public void start() {
        String auth=cfg.fyersAppId+":"+cfg.fyersToken;
        Request resolve=new Request.Builder().url("https://api-t1.fyers.in/indus/home/tbtws").header("Authorization",auth).get().build();
        client.newCall(resolve).enqueue(new Callback(){
            @Override public void onFailure(Call call,java.io.IOException e){ writer.state("fyers_tbt","URL_RESOLVE_FAILED:"+e.getClass().getSimpleName()+":FALLBACK"); connect("wss://rtsocket-api.fyers.in/versova",auth); }
            @Override public void onResponse(Call call,Response response){
                String url="wss://rtsocket-api.fyers.in/versova";
                try(Response r=response){ String body=r.body()!=null?r.body().string():""; if(r.isSuccessful()){ JSONObject j=new JSONObject(body); JSONObject d=j.optJSONObject("data"); String u=d==null?"":d.optString("socket_url",""); if(u.startsWith("wss://"))url=u; else writer.state("fyers_tbt","URL_RESOLVE_NO_SOCKET:FALLBACK"); } else writer.state("fyers_tbt","URL_RESOLVE_HTTP_"+r.code()+":FALLBACK"); }
                catch(Exception e){ writer.state("fyers_tbt","URL_RESOLVE_PARSE_FAILED:FALLBACK"); }
                connect(url,auth);
            }
        });
        scheduler.scheduleAtFixedRate(() -> { WebSocket s=socket; if(s!=null&&!stopping) send(s,"ping"); },10,10,TimeUnit.SECONDS);
    }

    private void connect(String url,String auth){
        if(stopping)return;
        writer.state("fyers_tbt","CONNECTING:"+(url.contains("versova")?"VERSOVA":"RESOLVED"));
        Request req=new Request.Builder().url(url).header("Authorization",auth).build();
        socket=client.newWebSocket(req,new WebSocketListener(){
            @Override public void onOpen(WebSocket ws,Response response){
                writer.state("fyers_tbt","CONNECTED");
                try{
                    JSONObject data=new JSONObject();data.put("subs",1);data.put("symbols",new JSONArray(cfg.fyersSymbols));data.put("mode","depth");data.put("channel","1");
                    JSONObject sub=new JSONObject();sub.put("type",1);sub.put("data",data);send(ws,sub.toString());
                    JSONObject swData=new JSONObject();swData.put("resumeChannels",new JSONArray().put("1"));swData.put("pauseChannels",new JSONArray());
                    JSONObject sw=new JSONObject();sw.put("type",2);sw.put("data",swData);send(ws,sw.toString());
                }catch(Exception e){writer.state("fyers_tbt","SUBSCRIBE_BUILD_FAILED:"+e.getClass().getSimpleName());}
            }
            @Override public void onMessage(WebSocket ws,String text){writer.text("fyers_tbt",FrameCodec.DIR_IN,text);}
            @Override public void onMessage(WebSocket ws,ByteString b){writer.write("fyers_tbt",FrameCodec.DIR_IN,FrameCodec.TYPE_BINARY,b.toByteArray());}
            @Override public void onClosing(WebSocket ws,int code,String reason){writer.state("fyers_tbt","CLOSING:"+code+":"+safe(reason));ws.close(code,null);}
            @Override public void onClosed(WebSocket ws,int code,String reason){writer.state("fyers_tbt","CLOSED:"+code+":"+safe(reason));}
            @Override public void onFailure(WebSocket ws,Throwable t,Response r){writer.state("fyers_tbt","FAILED:"+t.getClass().getSimpleName()+":"+safe(t.getMessage())+(r==null?"":":HTTP"+r.code()));}
        });
    }
    private void send(WebSocket ws,String text){writer.text("fyers_tbt",FrameCodec.DIR_OUT,text);if(!ws.send(text))writer.state("fyers_tbt","SEND_REJECTED");}
    public void stop(){stopping=true;if(socket!=null)socket.close(1000,"operator stop");scheduler.shutdownNow();client.dispatcher().executorService().shutdown();}
    private static String safe(String s){if(s==null)return "";s=s.replace('\n',' ');return s.substring(0,Math.min(120,s.length()));}
}
