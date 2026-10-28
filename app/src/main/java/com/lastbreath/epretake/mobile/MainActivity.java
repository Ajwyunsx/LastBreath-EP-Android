package com.lastbreath.epretake.mobile;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String GAME_ORIGIN = "https://game.local/";
    private final ExecutorService bundleExecutor = Executors.newSingleThreadExecutor();
    private WebView webView;
    private GameBundle gameBundle;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        webView = new WebView(this);
        webView.clearCache(true);
        webView.setBackgroundColor(Color.BLACK);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setLongClickable(false);
        webView.setHapticFeedbackEnabled(false);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);

        webView.addJavascriptInterface(new KeyboardBridge(), "AndroidKeyboard");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !request.getUrl().toString().startsWith(GAME_ORIGIN);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                GameBundle bundle = gameBundle;
                if (bundle != null && bundle.handles(url)) return bundle.open(url);
                return null;
            }
        });
        WebView.setWebContentsDebuggingEnabled(false);
        setContentView(webView);
        enterImmersiveMode();
        loadBundle(savedInstanceState);
    }

    private final class KeyboardBridge {
        @JavascriptInterface
        public void show() {
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                if (!webView.hasFocus()) webView.requestFocus();
                InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (keyboard != null) keyboard.showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT);
            });
        }

        @JavascriptInterface
        public void hide() {
            runOnUiThread(() -> hideKeyboard());
        }
    }

    private void hideKeyboard() {
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(webView.getWindowToken(), 0);
    }

    private void loadBundle(Bundle savedInstanceState) {
        bundleExecutor.execute(() -> {
            try {
                GameBundle bundle = new GameBundle(getAssets());
                runOnUiThread(() -> {
                    if (isDestroyed()) {
                        try {
                            bundle.close();
                        } catch (IOException ignored) {
                        }
                        return;
                    }
                    gameBundle = bundle;
                    if (savedInstanceState == null) {
                        webView.loadUrl(GAME_ORIGIN + "index.html");
                    } else {
                        webView.restoreState(savedInstanceState);
                    }
                });
            } catch (Exception error) {
                Log.e("LastBreath", "Unable to open game bundle", error);
                runOnUiThread(() -> webView.loadDataWithBaseURL(null,
                        "<html><body style='background:#000;color:#fff;font-family:monospace'>无法读取游戏资源</body></html>",
                        "text/html", "UTF-8", null));
            }
        });
    }

    private void enterImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enterImmersiveMode();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onPause() {
        hideKeyboard();
        webView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        enterImmersiveMode();
    }

    @Override
    protected void onDestroy() {
        webView.destroy();
        bundleExecutor.shutdownNow();
        if (gameBundle != null) {
            try {
                gameBundle.close();
            } catch (IOException ignored) {
            }
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }
}
