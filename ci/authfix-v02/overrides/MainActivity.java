package com.betawithgamma.microstructure;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;

import java.io.File;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private EditText dhanClient,dhanToken,dhanIns,fyersApp,fyersSecret,fyersRedirect,fyersAuthCode,fyersToken,fyersSymbols;
    private CheckBox cbDhanQ,cbDhan20,cbDhan200,cbFyers;
    private TextView status,authStatus;
    private final Handler h=new Handler(Looper.getMainLooper());
    private final ExecutorService net=Executors.newSingleThreadExecutor();
    private String exportPath="";

    @Override public void onCreate(Bundle b){super.onCreate(b);build();loadIntoUi();requestNotifications();h.post(poll);}
    @Override protected void onDestroy(){h.removeCallbacks(poll);net.shutdownNow();super.onDestroy();}

    private void build(){
        ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(16),dp(16),dp(24));scroll.addView(root);setContentView(scroll);
        root.addView(t("NIFTY Microstructure Evidence — Android V0.2",22,true));
        root.addView(t("AUTH FIRST → DATA PREFLIGHT → RAW CAPTURE. A WebSocket opening is not treated as proof of data entitlement.",14,false));

        root.addView(t("DHAN — existing 24 h access token",18,true));
        dhanClient=e("Dhan Client ID",false);dhanToken=e("Dhan Access Token",true);dhanIns=e("Dhan instruments — auto-filled after preflight",false);
        root.addView(dhanClient);root.addView(dhanToken);
        Button dhanTest=button("TEST DHAN AUTH + FETCH NIFTY ATM",v->testDhan());root.addView(dhanTest);
        root.addView(dhanIns);
        cbDhanQ=cb("Standard FULL feed (5-level + quote)");cbDhan20=cb("20-level depth");cbDhan200=cb("200-level depth — first instrument only");
        root.addView(cbDhanQ);root.addView(cbDhan20);root.addView(cbDhan200);
        root.addView(t("Dhan preflight calls /v2/profile, expiry list, and option chain. If the data plan/token fails, capture is not started.",12,false));

        root.addView(t("FYERS — browser login + auth-code exchange",18,true));
        fyersApp=e("FYERS App ID, e.g. XXXXX-100",false);fyersSecret=e("FYERS App Secret (runtime encrypted)",true);fyersRedirect=e("Registered Redirect URI",false);fyersAuthCode=e("Paste auth_code OR complete redirected URL",true);fyersToken=e("FYERS Access Token — auto-filled",true);fyersSymbols=e("TBT symbols (max 5), e.g. NSE:NIFTY26AUGFUT",false);
        root.addView(fyersApp);root.addView(fyersSecret);root.addView(fyersRedirect);
        root.addView(button("1. OPEN FYERS LOGIN IN BROWSER",v->openFyersLogin()));
        root.addView(fyersAuthCode);
        root.addView(button("2. EXCHANGE AUTH CODE + TEST DATA",v->exchangeFyers()));
        root.addView(fyersToken);
        root.addView(button("TEST EXISTING FYERS TOKEN",v->testFyersExisting()));
        root.addView(fyersSymbols);cbFyers=cb("50-level FYERS TBT raw capture");root.addView(cbFyers);
        root.addView(t("FYERS TBT now follows SDK 3.1.15 mechanics: resolve socket URL first, Authorization=APP_ID:access_token, channel \"1\", plain ping.",12,false));

        authStatus=t("AUTH STATUS: not tested",13,true);authStatus.setTypeface(Typeface.MONOSPACE);authStatus.setTextIsSelectable(true);root.addView(authStatus);
        root.addView(button("SAVE CONFIG + START RAW CAPTURE",v->start()));
        root.addView(button("STOP + SEAL MANIFEST",v->stop()));
        root.addView(button("EXPORT LAST SESSION ZIP",v->export()));
        status=t("Status: idle",13,false);status.setTypeface(Typeface.MONOSPACE);status.setTextIsSelectable(true);root.addView(status);
        root.addView(t("Secrets remain encrypted with Android Keystore and are excluded from evidence manifests. FYERS secret is only needed for auth-code exchange; it is never sent to market-data endpoints.",12,false));
    }

    private void testDhan(){
        String cid=dhanClient.getText().toString().trim(),tok=dhanToken.getText().toString().trim();setAuth("DHAN: testing profile + data entitlement...");
        net.execute(()->{try{BrokerAuthClient.DhanProbe p=new BrokerAuthClient().probeDhanAndResolveNifty(cid,tok);runOnUiThread(()->{dhanIns.setText(p.instruments());save();setAuth(p.summary());});}catch(Exception e){runOnUiThread(()->setAuth("DHAN BLOCKED: "+safe(e.getMessage())));}});
    }

    private void openFyersLogin(){
        try{save();String url=new BrokerAuthClient().buildFyersLoginUrl(fyersApp.getText().toString(),fyersRedirect.getText().toString(),"microstructure_android");startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));setAuth("FYERS: browser opened. Complete login, then paste auth_code or redirected URL into step 2.");}catch(Exception e){setAuth("FYERS LOGIN BLOCKED: "+safe(e.getMessage()));}
    }

    private void exchangeFyers(){
        String app=fyersApp.getText().toString().trim(),secret=fyersSecret.getText().toString().trim(),code=fyersAuthCode.getText().toString().trim();setAuth("FYERS: exchanging one-time auth_code...");
        net.execute(()->{try{BrokerAuthClient c=new BrokerAuthClient();String token=c.exchangeFyersAuthCode(app,secret,code);BrokerAuthClient.FyersProbe p=c.probeFyers(app,token);runOnUiThread(()->{fyersToken.setText(token);fyersAuthCode.setText("");save();setAuth(p.summary()+" | token stored encrypted");});}catch(Exception e){runOnUiThread(()->setAuth("FYERS BLOCKED: "+safe(e.getMessage())));}});
    }

    private void testFyersExisting(){
        String app=fyersApp.getText().toString().trim(),tok=fyersToken.getText().toString().trim();setAuth("FYERS: testing profile + NIFTY quote...");
        net.execute(()->{try{BrokerAuthClient.FyersProbe p=new BrokerAuthClient().probeFyers(app,tok);runOnUiThread(()->{save();setAuth(p.summary());});}catch(Exception e){runOnUiThread(()->setAuth("FYERS BLOCKED: "+safe(e.getMessage())));}});
    }

    private void start(){try{save();ProviderConfig c=loadConfig(this);c.validate();Intent i=new Intent(this,CaptureService.class).setAction(CaptureService.ACTION_START);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}catch(Exception e){toast(e.getMessage());}}
    private void stop(){startService(new Intent(this,CaptureService.class).setAction(CaptureService.ACTION_STOP));}

    private void save(){
        SecurePrefs s=new SecurePrefs(this);s.put("dhan_client",dhanClient.getText().toString());s.put("dhan_token",dhanToken.getText().toString());s.put("dhan_ins",dhanIns.getText().toString());
        s.put("fyers_app",fyersApp.getText().toString());s.put("fyers_secret",fyersSecret.getText().toString());s.put("fyers_redirect",fyersRedirect.getText().toString());s.put("fyers_token",fyersToken.getText().toString());s.put("fyers_symbols",fyersSymbols.getText().toString());
        getSharedPreferences("cfg_flags",MODE_PRIVATE).edit().putBoolean("dq",cbDhanQ.isChecked()).putBoolean("d20",cbDhan20.isChecked()).putBoolean("d200",cbDhan200.isChecked()).putBoolean("ft",cbFyers.isChecked()).apply();
    }

    public static ProviderConfig loadConfig(Context c){SecurePrefs s=new SecurePrefs(c);var f=c.getSharedPreferences("cfg_flags",MODE_PRIVATE);return new ProviderConfig(s.get("dhan_client"),s.get("dhan_token"),s.get("dhan_ins"),s.get("fyers_app"),s.get("fyers_secret"),s.get("fyers_redirect"),s.get("fyers_token"),s.get("fyers_symbols"),f.getBoolean("dq",true),f.getBoolean("d20",true),f.getBoolean("d200",false),f.getBoolean("ft",false));}

    private void loadIntoUi(){ProviderConfig c=loadConfig(this);dhanClient.setText(c.dhanClientId);dhanToken.setText(c.dhanToken);StringBuilder di=new StringBuilder();for(var x:c.dhanInstruments){if(di.length()>0)di.append(',');di.append(x.segment).append(':').append(x.securityId);}dhanIns.setText(di);fyersApp.setText(c.fyersAppId);fyersSecret.setText(c.fyersSecret);fyersRedirect.setText(c.fyersRedirectUri);fyersToken.setText(c.fyersToken);fyersSymbols.setText(String.join(",",c.fyersSymbols));cbDhanQ.setChecked(c.dhanQuote);cbDhan20.setChecked(c.dhan20);cbDhan200.setChecked(c.dhan200);cbFyers.setChecked(c.fyersTbt);}

    private final Runnable poll=new Runnable(){public void run(){var r=getSharedPreferences("runtime",MODE_PRIVATE);boolean run=r.getBoolean("running",false);String sid=r.getString("session","");String st=r.getString("status","IDLE");exportPath=r.getString("path","");status.setText("running="+run+"\nsession="+sid+"\n"+st);h.postDelayed(this,1000);}};
    private void export(){if(exportPath==null||exportPath.isEmpty()||!new File(exportPath).isDirectory()){toast("No captured session available");return;}Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip").putExtra(Intent.EXTRA_TITLE,"microstructure_"+new File(exportPath).getName()+".zip");startActivityForResult(i,44);}
    @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if(req==44&&res==RESULT_OK&&data!=null){Uri u=data.getData();try(OutputStream o=getContentResolver().openOutputStream(u)){EvidenceExporter.zip(new File(exportPath),o);toast("Evidence ZIP exported");}catch(Exception e){toast("Export failed: "+e.getMessage());}}}
    private void requestNotifications(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},99);}
    private Button button(String s,View.OnClickListener l){Button b=new Button(this);b.setText(s);b.setOnClickListener(l);return b;}
    private EditText e(String hint,boolean secret){EditText x=new EditText(this);x.setHint(hint);x.setSingleLine(false);x.setMinLines(1);if(secret)x.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);return x;}
    private CheckBox cb(String s){CheckBox c=new CheckBox(this);c.setText(s);return c;}
    private TextView t(String s,int sp,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setPadding(0,dp(8),0,dp(6));if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private int dp(int n){return(int)(n*getResources().getDisplayMetrics().density+.5f);}
    private void toast(String s){Toast.makeText(this,s==null?"error":s,Toast.LENGTH_LONG).show();}
    private void setAuth(String s){authStatus.setText("AUTH STATUS:\n"+s);}
    private static String safe(String s){if(s==null)return "error";s=s.replace('\n',' ');return s.substring(0,Math.min(220,s.length()));}
}
