package com.example.deepseekapp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 极简 WebView 壳：加载电脑上跑的 chat-demo。
 *
 * 首次启动要求填写服务地址（存 SharedPreferences）；WebView 建不起来或页面加载失败时
 * 不崩溃，而是显示具体原因并提供「用系统浏览器打开」。
 *
 * 注意：WebViewClient / WebChromeClient 用「命名静态嵌套类」而不是匿名内部类。
 * build-tools 34.0.0 自带的 d8 是 dev 版，处理 android.jar 类的匿名子类时会抛 NPE，
 * 命名类可以绕开；这同时避免了匿名类持有外部引用的问题。
 */
public class MainActivity extends Activity {

    /**
     * 服务地址在 res/values/strings.xml 的 server_url 里改，
     * 换成自己部署的域名即可，无需改 Java 代码。
     */
    private static final String PREFS = "app";

    private String serverUrl() {
        return getString(R.string.server_url);
    }

    private static final int REQUEST_FILE = 1001;

    private WebView webView;
    private ValueCallback<Uri[]> pendingFileCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyThemeBackground();
        if (savedInstanceState == null) {
            showWebView(serverUrl());
        } else if (showWebView(serverUrl())) {
            if (webView != null) {
                webView.restoreState(savedInstanceState);
            }
        }
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private static boolean isNightMode(Activity activity) {
        return (activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    /** 深色模式用与页面一致的底色（chat-demo 的 --dsw-alias-bg-base），避免底部露出白条 */
    private static int pageBackground(Activity activity) {
        return isNightMode(activity) ? Color.rgb(21, 21, 23) : Color.WHITE;
    }

    private void applyThemeBackground() {
        // 不做边到边：内容留在系统栏之间，系统栏颜色随后按页面底色同步（syncThemeFromPage）
        applyColors(pageBackground(this));
    }

    /**
     * 页面里的主题可能和系统不一致（chat-demo 允许单独选深色/浅色），
     * 所以页面加载完后直接取它的实际背景色，再同步给系统栏。
     */
    void syncThemeFromPage() {
        final WebView view = webView;
        if (view == null) {
            return;
        }
        view.evaluateJavascript(
                "(function(){var s=getComputedStyle(document.documentElement).backgroundColor;"
                        + "if(!s||s==='rgba(0, 0, 0, 0)'){s=getComputedStyle(document.body).backgroundColor;}"
                        + "return s;})()",
                value -> {
                    int color = parseCssColor(value);
                    if (color != 0) {
                        applyColors(color);
                    }
                });
    }

    /** 解析 "rgb(21, 21, 23)" / "rgba(...)"；失败返回 0 */
    private static int parseCssColor(String jsValue) {
        if (jsValue == null) {
            return 0;
        }
        String v = jsValue.replace("\"", "").trim();
        int open = v.indexOf('(');
        int close = v.indexOf(')');
        if (open < 0 || close < 0) {
            return 0;
        }
        String[] parts = v.substring(open + 1, close).split(",");
        if (parts.length < 3) {
            return 0;
        }
        try {
            int r = Integer.parseInt(parts[0].trim());
            int g = Integer.parseInt(parts[1].trim());
            int b = Integer.parseInt(parts[2].trim());
            return Color.rgb(r, g, b);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 统一设置窗口、WebView 与系统栏的底色，并按亮度切换系统栏图标颜色 */
    private void applyColors(int bg) {
        getWindow().setBackgroundDrawable(new ColorDrawable(bg));
        if (webView != null) {
            webView.setBackgroundColor(bg);
        }
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);

        double luminance = (0.299 * Color.red(bg) + 0.587 * Color.green(bg) + 0.114 * Color.blue(bg)) / 255.0;
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (luminance > 0.6) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }
    /** 返回 false 表示 WebView 建不起来，已切换到失败说明页 */
    @SuppressLint("SetJavaScriptEnabled")
    private boolean showWebView(String url) {
        try {
            // 允许用 chrome://inspect / adb forward 调试页面（这台机器 adb 不能注入触摸）
            WebView.setWebContentsDebuggingEnabled(true);
            webView = new WebView(this);
        } catch (Throwable t) {
            showFailure(url, t.getClass().getName() + "\n" + t.getMessage());
            return false;
        }

        try {
            setContentView(webView, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            webView.setBackgroundColor(pageBackground(this));

            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            // 按设备宽度渲染，交给页面自己的移动端断点；开这两个会让 WebView 用桌面宽视口
            settings.setLoadWithOverviewMode(false);
            settings.setUseWideViewPort(false);
            settings.setSupportZoom(false);
            settings.setMediaPlaybackRequiresUserGesture(false);

            webView.setWebViewClient(new Client(this, url));
            webView.setWebChromeClient(new Chrome(this));
            webView.loadUrl(url);
            return true;
        } catch (Throwable t) {
            showFailure(url, t.getClass().getName() + "\n" + t.getMessage());
            return false;
        }
    }

    private boolean handleSpecialUrl(String url) {
        // 已固定服务地址，不再处理切换地址的深链接
        return false;
    }

    /** 失败说明页：显示具体原因 + 兜底入口 */
    private void showFailure(String url, String detail) {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.WHITE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 96, 48, 48);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("打不开页面");
        title.setTextSize(20);
        title.setTextColor(Color.parseColor("#1A1A1A"));
        root.addView(title);

        TextView body = new TextView(this);
        body.setText("目标地址：\n" + url + "\n\n原因：\n" + detail
                + "\n\n如果是「WebView 不可用」，请用下面的按钮在系统浏览器里打开。");
        body.setTextSize(13);
        body.setTextColor(Color.parseColor("#666666"));
        body.setPadding(0, 16, 0, 24);
        root.addView(body);

        Button browserButton = new Button(this);
        browserButton.setText("用系统浏览器打开");
        root.addView(browserButton);
        browserButton.setOnClickListener(v -> openInBrowser(url));

        Button retryButton = new Button(this);
        retryButton.setText("重试");
        root.addView(retryButton);
        retryButton.setOnClickListener(v -> showWebView(serverUrl()));

        setContentView(scroll);
    }

    /** 边到边显示时，把系统栏高度作为内边距让给页面，窗口底色在空隙处透出 */
    @SuppressWarnings("unused")
    private void applyInsetsPadding(final WebView view) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return;
        }
        view.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(0, bars.top, 0, bars.bottom);
            return insets;
        });
    }

    private void openInBrowser(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "没有找到可用的浏览器：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_FILE) {
            if (pendingFileCallback != null) {
                Uri[] uris = null;
                if (resultCode == RESULT_OK && data != null) {
                    if (data.getClipData() != null) {
                        int count = data.getClipData().getItemCount();
                        uris = new Uri[count];
                        for (int i = 0; i < count; i++) {
                            uris[i] = data.getClipData().getItemAt(i).getUri();
                        }
                    } else if (data.getData() != null) {
                        uris = new Uri[]{data.getData()};
                    }
                }
                pendingFileCallback.onReceiveValue(uris);
                pendingFileCallback = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /** 返回键先问页面：有浮层就关，页面返回 exit 才真正退出 */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView != null) {
            askPageThenMaybeExit();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /** 手势返回走的是这里（没有注册 OnBackInvokedCallback 时系统会回调 onBackPressed） */
    @Override
    public void onBackPressed() {
        if (webView != null) {
            askPageThenMaybeExit();
            return;
        }
        super.onBackPressed();
    }

    private void askPageThenMaybeExit() {
        webView.evaluateJavascript(
                "(function(){try{return window.__dsHandleBack?window.__dsHandleBack():'exit'}"
                        + "catch(e){return 'exit'}})()",
                value -> {
                    boolean exit = value == null || value.contains("exit");
                    if (exit) {
                        if (webView != null && webView.canGoBack()) {
                            webView.goBack();
                        } else {
                            moveTaskToBack(true);
                        }
                    }
                });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) {
            webView.saveState(outState);
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }

    /** 页面跳转与错误处理；静态嵌套类，避免 d8 对匿名子类的 bug */
    private static final class Client extends WebViewClient {
        private final MainActivity host;
        private final String startUrl;

        Client(MainActivity host, String startUrl) {
            this.host = host;
            this.startUrl = startUrl;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return host.handleSpecialUrl(request == null ? null : String.valueOf(request.getUrl()));
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String target) {
            return host.handleSpecialUrl(target);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request == null || request.isForMainFrame()) {
                host.showFailure(startUrl, error == null ? "页面加载失败" : String.valueOf(error.getDescription()));
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            host.syncThemeFromPage();
        }
    }

    /** 附件选择：图片走系统「照片选择器」（网格直选），其他类型走文件选择器 */
    private static final class Chrome extends WebChromeClient {
        private final MainActivity host;

        Chrome(MainActivity host) {
            this.host = host;
        }

        /** 页面是否只要求图片类型 */
        private static boolean acceptsImages(String[] types) {
            if (types == null) {
                return false;
            }
            for (String type : types) {
                if (type != null && type.toLowerCase().startsWith("image/")) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                         FileChooserParams params) {
            if (host.pendingFileCallback != null) {
                host.pendingFileCallback.onReceiveValue(null);
            }
            host.pendingFileCallback = callback;
            try {
                Intent intent;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        && acceptsImages(params.getAcceptTypes())) {
                    // Android 13+ 系统照片选择器：自带网格、支持多选、无需存储权限
                    intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
                    intent.setType("image/*");
                    intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, 10);
                } else {
                    intent = params.createIntent();
                }
                host.startActivityForResult(intent, REQUEST_FILE);
                return true;
            } catch (Exception e) {
                host.pendingFileCallback = null;
                return false;
            }
        }
    }
}