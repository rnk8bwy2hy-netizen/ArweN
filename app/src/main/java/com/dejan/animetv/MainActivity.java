package com.dejan.animetv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {

    private FrameLayout rootContainer;
    private WebView webView;
    private FrameLayout fullscreenContainer;
    private View cursorView;
    private TextView helpOverlay;

    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private float cursorX = 0f;
    private float cursorY = 0f;

    private int cursorSizePx;
    private int cursorStepPx;
    private int scrollStepPx;
    private int edgePaddingPx;

    private boolean cursorMode = false;

    private static final String HOME_URL = "https://animecix.tv/";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        rootContainer = findViewById(R.id.rootContainer);
        webView = findViewById(R.id.animeWebView);
        fullscreenContainer = findViewById(R.id.fullscreenContainer);
        cursorView = findViewById(R.id.tvCursor);
        helpOverlay = findViewById(R.id.helpOverlay);

        cursorSizePx = dp(38);
        cursorStepPx = dp(62);
        scrollStepPx = dp(290);
        edgePaddingPx = dp(30);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setTextZoom(112);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setBackgroundColor(Color.BLACK);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.requestFocus();

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() == null) return false;
                String url = request.getUrl().toString();
                return handleUrl(view, url);
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(view, url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectTvRemoteScript();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        remoteInit();
                    }
                }, 550);
            }

            private boolean handleUrl(WebView view, String url) {
                if (url == null) return false;

                if (url.startsWith("http://") || url.startsWith("https://")) {
                    view.loadUrl(url);
                    return true;
                }

                // intent://, market:// vb. harici şemaları TV'de boş geçiyoruz.
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    if (callback != null) callback.onCustomViewHidden();
                    return;
                }

                customView = view;
                customViewCallback = callback;

                fullscreenContainer.addView(
                        view,
                        new FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                        )
                );

                fullscreenContainer.setVisibility(View.VISIBLE);
                webView.setVisibility(View.GONE);
                setCursorMode(false, false);
                enterImmersiveMode();
                showHelp("Video modu: OK oynat/duraklatır, Geri tam ekrandan çıkarır.", 2500);
            }

            @Override
            public void onHideCustomView() {
                hideCustomView();
            }
        });

        rootContainer.post(new Runnable() {
            @Override
            public void run() {
                centerCursor();
                setCursorMode(false, false);
                enterImmersiveMode();
                showHelp("TV NAV modu açık: Yön tuşları seçim, OK açar. 0 = imleç modu.", 3500);
            }
        });

        if (savedInstanceState == null) {
            webView.loadUrl(HOME_URL);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) webView.saveState(outState);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int repeat = event == null ? 0 : event.getRepeatCount();
        int multiplier = Math.min(5, 1 + (repeat / 3));
        int step = cursorStepPx * multiplier;

        if (customView != null) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_BACK:
                    hideCustomView();
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    nativeTap(customView, rootContainer.getWidth() / 2f, rootContainer.getHeight() / 2f);
                    return true;
                default:
                    return super.onKeyDown(keyCode, event);
            }
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (cursorMode) moveCursor(-step, 0);
                else remoteMove("left");
                return true;

            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (cursorMode) moveCursor(step, 0);
                else remoteMove("right");
                return true;

            case KeyEvent.KEYCODE_DPAD_UP:
                if (cursorMode) moveCursor(0, -step);
                else remoteMove("up");
                return true;

            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (cursorMode) moveCursor(0, step);
                else remoteMove("down");
                return true;

            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (cursorMode) clickAtCursor();
                else remoteClickSelected();
                return true;

            case KeyEvent.KEYCODE_0:
            case KeyEvent.KEYCODE_BUTTON_SELECT:
                setCursorMode(!cursorMode, true);
                return true;

            case KeyEvent.KEYCODE_1:
                setCursorMode(false, true);
                return true;

            case KeyEvent.KEYCODE_2:
                setCursorMode(true, true);
                return true;

            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (cursorMode) scrollWebView(-scrollStepPx);
                else remoteScroll("up");
                return true;

            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (cursorMode) scrollWebView(scrollStepPx);
                else remoteScroll("down");
                return true;

            case KeyEvent.KEYCODE_SEARCH:
            case KeyEvent.KEYCODE_PROG_RED:
                remoteSearch();
                showHelp("Arama kutusu seçilmeye çalışıldı. Yazı girmek için TV klavyesi açılabilir.", 2800);
                return true;

            case KeyEvent.KEYCODE_INFO:
            case KeyEvent.KEYCODE_HELP:
                showHelp("Yön=seçim | OK=aç | 0=imleç | 1=TV NAV | 2=imleç | Menü=ana sayfa | Geri=geri", 5000);
                return true;

            case KeyEvent.KEYCODE_MENU:
                if (webView != null) {
                    webView.loadUrl(HOME_URL);
                    showHelp("Ana sayfaya dönüldü.", 1800);
                }
                return true;

            case KeyEvent.KEYCODE_BACK:
                if (cursorMode) {
                    setCursorMode(false, true);
                    return true;
                }
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                    return true;
                }
                return super.onKeyDown(keyCode, event);

            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
        enterImmersiveMode();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private void setCursorMode(boolean enabled, boolean announce) {
        cursorMode = enabled;
        if (cursorView != null) {
            cursorView.setVisibility(enabled ? View.VISIBLE : View.GONE);
            if (enabled) cursorView.bringToFront();
        }
        if (announce) {
            if (enabled) showHelp("İmleç modu: yön tuşları imleci hareket ettirir, OK dokunur. 1 ile TV NAV'a dön.", 3200);
            else showHelp("TV NAV modu: yön tuşları karttan karta gider, OK seçer. 0 ile imleç açılır.", 3200);
        }
        if (!enabled) remoteInit();
    }

    private void remoteInit() {
        evaluate("if(window.AnimeTVRemote){window.AnimeTVRemote.init();}");
    }

    private void remoteMove(String direction) {
        evaluate("if(window.AnimeTVRemote){window.AnimeTVRemote.move('" + direction + "');}");
    }

    private void remoteScroll(String direction) {
        evaluate("if(window.AnimeTVRemote){window.AnimeTVRemote.scrollPage('" + direction + "');}");
    }

    private void remoteSearch() {
        evaluate("if(window.AnimeTVRemote){window.AnimeTVRemote.focusSearch();}");
    }

    private void remoteClickSelected() {
        if (webView == null) return;
        webView.evaluateJavascript(
                "(window.AnimeTVRemote&&window.AnimeTVRemote.clickAndGetCenter)?window.AnimeTVRemote.clickAndGetCenter():null",
                value -> {
                    try {
                        if (value != null && value.trim().startsWith("{")) {
                            JSONObject object = new JSONObject(value);
                            float x = (float) object.optDouble("x", rootContainer.getWidth() / 2f);
                            float y = (float) object.optDouble("y", rootContainer.getHeight() / 2f);
                            nativeTap(webView, x, y);
                        } else {
                            nativeTap(webView, rootContainer.getWidth() / 2f, rootContainer.getHeight() / 2f);
                        }
                    } catch (Exception e) {
                        nativeTap(webView, rootContainer.getWidth() / 2f, rootContainer.getHeight() / 2f);
                    }
                }
        );
    }

    private void moveCursor(int dx, int dy) {
        if (rootContainer == null || cursorView == null) return;

        int width = rootContainer.getWidth();
        int height = rootContainer.getHeight();
        if (width <= 0 || height <= 0) return;

        float minX = edgePaddingPx;
        float maxX = width - edgePaddingPx;
        float minY = edgePaddingPx;
        float maxY = height - edgePaddingPx;

        float newX = cursorX + dx;
        float newY = cursorY + dy;

        if (newY > maxY) {
            newY = maxY;
            scrollWebView(scrollStepPx);
        } else if (newY < minY) {
            newY = minY;
            scrollWebView(-scrollStepPx);
        }

        if (newX > maxX) newX = maxX;
        if (newX < minX) newX = minX;

        cursorX = newX;
        cursorY = newY;
        updateCursorPosition();
    }

    private void clickAtCursor() {
        View target = customView != null ? customView : webView;
        if (target == null) return;
        nativeTap(target, cursorX, cursorY);
        pulseCursor();
    }

    private void nativeTap(View target, float x, float y) {
        if (target == null) return;
        long now = SystemClock.uptimeMillis();

        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(now, now + 72, MotionEvent.ACTION_UP, x, y, 0);

        target.dispatchTouchEvent(down);
        target.dispatchTouchEvent(up);

        down.recycle();
        up.recycle();
    }

    private void scrollWebView(int dy) {
        if (customView != null) return;
        if (webView != null) webView.scrollBy(0, dy);
    }

    private void centerCursor() {
        if (rootContainer == null) return;
        int width = rootContainer.getWidth();
        int height = rootContainer.getHeight();
        if (width <= 0 || height <= 0) return;

        cursorX = width / 2f;
        cursorY = height / 2f;
        updateCursorPosition();
    }

    private void updateCursorPosition() {
        if (cursorView == null) return;
        cursorView.setX(cursorX - (cursorSizePx / 2f));
        cursorView.setY(cursorY - (cursorSizePx / 2f));
        cursorView.bringToFront();
    }

    private void pulseCursor() {
        if (cursorView == null) return;
        cursorView.animate()
                .scaleX(0.72f)
                .scaleY(0.72f)
                .setDuration(65)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        cursorView.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(65)
                                .start();
                    }
                })
                .start();
    }

    private void hideCustomView() {
        if (customView == null) return;

        fullscreenContainer.removeView(customView);
        fullscreenContainer.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);

        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
        }

        customView = null;
        customViewCallback = null;
        exitImmersiveMode();
        enterImmersiveMode();
        showHelp("TV NAV moduna dönüldü.", 1800);
    }

    private void injectTvRemoteScript() {
        if (webView == null) return;
        try {
            String js = readAssetText("tv_remote.js");
            webView.evaluateJavascript(js, null);
        } catch (Exception ignored) {
            // JS enjekte edilemezse imleç modu hâlâ çalışır.
        }
    }

    private String readAssetText(String fileName) throws Exception {
        InputStream inputStream = getAssets().open(fileName);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        inputStream.close();
        return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
    }

    private void evaluate(String js) {
        if (webView == null) return;
        webView.evaluateJavascript(js, null);
    }

    private void showHelp(String message, long durationMs) {
        if (helpOverlay == null) return;
        helpOverlay.setText(message);
        helpOverlay.setVisibility(View.VISIBLE);
        helpOverlay.bringToFront();
        handler.removeCallbacksAndMessages(helpOverlay);
        handler.postAtTime(new Runnable() {
            @Override
            public void run() {
                if (helpOverlay != null) helpOverlay.setVisibility(View.GONE);
            }
        }, helpOverlay, SystemClock.uptimeMillis() + durationMs);
    }

    private void enterImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private void exitImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
