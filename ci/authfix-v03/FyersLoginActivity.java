package com.betawithgamma.microstructure;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Embedded FYERS login surface. It never reads page DOM or credentials.
 * It only intercepts navigation to the exact registered redirect target,
 * validates OAuth state, returns the one-time auth_code, and stops navigation
 * before WebView attempts to connect to 127.0.0.1.
 */
public final class FyersLoginActivity extends Activity {
    public static final String EXTRA_LOGIN_URL = "login_url";
    public static final String EXTRA_REDIRECT_URI = "redirect_uri";
    public static final String EXTRA_EXPECTED_STATE = "expected_state";
    public static final String EXTRA_AUTH_CODE = "auth_code";
    public static final String EXTRA_ERROR = "error";

    private WebView web;
    private TextView status;
    private String redirectUri;
    private String expectedState;
    private final AtomicBoolean terminal = new AtomicBoolean(false);

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);

        String loginUrl = getIntent().getStringExtra(EXTRA_LOGIN_URL);
        redirectUri = getIntent().getStringExtra(EXTRA_REDIRECT_URI);
        expectedState = getIntent().getStringExtra(EXTRA_EXPECTED_STATE);
        if (blank(loginUrl) || blank(redirectUri) || blank(expectedState)) {
            finishError("OAuth launch parameters missing");
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        status = new TextView(this);
        status.setText("FYERS official login — waiting for secure callback…");
        status.setPadding(dp(12), dp(8), dp(12), dp(8));
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));
        web = new WebView(this);
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSaveFormData(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return maybeCapture(request == null || request.getUrl() == null ? null : request.getUrl().toString());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return maybeCapture(url);
            }
            @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (!maybeCapture(url)) super.onPageStarted(view, url, favicon);
            }
        });
        web.loadUrl(loginUrl);
    }

    private boolean maybeCapture(String url) {
        if (terminal.get() || blank(url) || !FyersOAuth.isRedirectTarget(redirectUri, url)) return false;
        if (!terminal.compareAndSet(false, true)) return true;
        try {
            if (web != null) web.stopLoading();
            String code = FyersOAuth.requireValidCallback(redirectUri, url, expectedState);
            Intent out = new Intent().putExtra(EXTRA_AUTH_CODE, code);
            setResult(RESULT_OK, out);
            finish();
        } catch (Exception e) {
            finishError(e.getMessage());
        }
        return true;
    }

    private void finishError(String message) {
        if (!terminal.get()) terminal.set(true);
        Intent out = new Intent().putExtra(EXTRA_ERROR, message == null ? "OAuth callback rejected" : message);
        setResult(RESULT_CANCELED, out);
        finish();
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else { setResult(RESULT_CANCELED, new Intent().putExtra(EXTRA_ERROR, "FYERS login cancelled")); finish(); }
    }

    @Override protected void onDestroy() {
        if (web != null) {
            try { web.stopLoading(); web.loadUrl("about:blank"); web.clearHistory(); web.removeAllViews(); web.destroy(); } catch (Exception ignored) {}
            web = null;
        }
        super.onDestroy();
    }

    private static boolean blank(String s) { return s == null || s.trim().isEmpty(); }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
}
