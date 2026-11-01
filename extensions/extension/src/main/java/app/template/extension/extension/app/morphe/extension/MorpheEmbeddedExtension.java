package app.morphe.extension;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MorpheEmbeddedExtension {

    private static final String COMMUNITY_URL = "https://morphe-patches.software";
    private static final String GITHUB_LATEST_API = "https://api.github.com/repos/MorpheApp/morphe-manager/releases/latest";
    private static final String PREF_NAME = "morphe_custom_patch_prefs";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static boolean interceptExternalBrowser(Activity activity, Intent intent) {
        if (intent == null || intent.getData() == null) {
            return false;
        }
        String url = intent.getData().toString();
        if (url.startsWith(COMMUNITY_URL)) {
            showEmbeddedCommunityPage(activity, url);
            return true;
        }
        return false;
    }

    public static void attachInAppHubTrigger(Activity activity) {
        mainHandler.postDelayed(() -> {
            try {
                ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
                if (decorView.findViewWithTag("morphe_inapp_hub_btn") != null) {
                    return;
                }

                TextView hubBtn = new TextView(activity);
                hubBtn.setTag("morphe_inapp_hub_btn");
                hubBtn.setText("🌐 Community");
                hubBtn.setTextColor(Color.parseColor("#6DD58C"));
                hubBtn.setTextSize(12f);
                hubBtn.setPadding(32, 18, 32, 18);

                GradientDrawable bg = new GradientDrawable();
                bg.setColor(Color.parseColor("#1E3A2F"));
                bg.setCornerRadius(50f);
                bg.setStroke(2, Color.parseColor("#2E5A44"));
                hubBtn.setBackground(bg);

                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                params.gravity = Gravity.BOTTOM | Gravity.END;
                params.bottomMargin = 240;
                params.rightMargin = 36;

                hubBtn.setOnClickListener(v -> showEmbeddedCommunityPage(activity, COMMUNITY_URL));
                decorView.addView(hubBtn, params);
            } catch (Exception ignored) {
            }
        }, 1200);
    }

    public static void showEmbeddedCommunityPage(Activity activity, String targetUrl) {
        Dialog dialog = new Dialog(activity, android.R.style.Theme_DeviceDefault_NoActionBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        FrameLayout container = new FrameLayout(activity);
        container.setBackgroundColor(Color.parseColor("#0E1113"));

        WebView webView = new WebView(activity);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        webView.setBackgroundColor(Color.parseColor("#0E1113"));
        webView.addJavascriptInterface(new MorpheBridge(activity, dialog), "MorpheBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String reqUrl = request.getUrl().toString();
                if (reqUrl.startsWith("morphe://") || reqUrl.contains("/add?") || reqUrl.contains("source=")) {
                    triggerDirectSourceAdd(activity, reqUrl);
                    dialog.dismiss();
                    return true;
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectCustomIntegrationScript(view);
            }
        });

        webView.loadUrl(targetUrl);
        container.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        dialog.setContentView(container);
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
                webView.goBack();
                return true;
            }
            return false;
        });
        dialog.show();
    }

    private static void injectCustomIntegrationScript(WebView webView) {
        String jsCode = "(function() {" +
                "const style = document.createElement('style');" +
                "style.innerHTML = `" +
                "  header, nav, .navbar, .site-header, [class*='header'], [class*='Header'], [class*='nav-bar'] { display: none !important; }" +
                "  body { padding-top: 8px !important; background-color: #0E1113 !important; }" +
                "  .morphe-added-btn {" +
                "    background-color: #1E3A2F !important;" +
                "    color: #6DD58C !important;" +
                "    border: 1px solid #2E5A44 !important;" +
                "    pointer-events: none !important;" +
                "    opacity: 0.9 !important;" +
                "  }" +
                "`;" +
                "document.head.appendChild(style);" +
                "function syncAddedSources() {" +
                "  const installedJson = window.MorpheBridge.getInstalledSources();" +
                "  const installedList = JSON.parse(installedJson);" +
                "  const buttons = document.querySelectorAll('a, button');" +
                "  buttons.forEach(btn => {" +
                "    const text = (btn.innerText || '').trim();" +
                "    if (text.includes('Add to Morphe') || btn.dataset.morpheChecked) {" +
                "      btn.dataset.morpheChecked = 'true';" +
                "      const card = btn.closest('div[class*=\"card\"], article, li') || btn.parentElement.parentElement;" +
                "      const cardHtml = card ? card.innerHTML.toLowerCase() : '';" +
                "      const href = (btn.getAttribute('href') || '').toLowerCase();" +
                "      let isAdded = false;" +
                "      for (let i = 0; i < installedList.length; i++) {" +
                "        const repo = installedList[i].toLowerCase();" +
                "        if (repo && (cardHtml.includes(repo) || href.includes(repo))) {" +
                "          isAdded = true;" +
                "          break;" +
                "        }" +
                "      }" +
                "      if (isAdded) {" +
                "        btn.innerText = '✓ Already Added';" +
                "        btn.classList.add('morphe-added-btn');" +
                "      } else {" +
                "        btn.onclick = function(e) {" +
                "          const targetHref = btn.getAttribute('href') || '';" +
                "          if (targetHref) {" +
                "            e.preventDefault();" +
                "            window.MorpheBridge.addSourceDirectly(targetHref);" +
                "          }" +
                "        };" +
                "      }" +
                "    }" +
                "  });" +
                "}" +
                "syncAddedSources();" +
                "const observer = new MutationObserver(() => syncAddedSources());" +
                "observer.observe(document.body, { childList: true, subtree: true });" +
                "})();";

        webView.evaluateJavascript(jsCode, null);
    }

    private static void triggerDirectSourceAdd(Activity activity, String rawUrl) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(rawUrl));
            intent.setPackage(activity.getPackageName());
            activity.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(activity, "Source URL processed", Toast.LENGTH_SHORT).show();
        }
    }

    public static class MorpheBridge {
        private final Activity activity;
        private final Dialog dialog;

        public MorpheBridge(Activity activity, Dialog dialog) {
            this.activity = activity;
            this.dialog = dialog;
        }

        @JavascriptInterface
        public String getInstalledSources() {
            Set<String> repos = new HashSet<>();
            try {
                File dbDir = new File(activity.getApplicationInfo().dataDir, "databases");
                if (dbDir.exists() && dbDir.listFiles() != null) {
                    for (File f : dbDir.listFiles()) {
                        if (f.getName().endsWith(".db") || f.getName().contains("source")) {
                            extractStringsFromFile(f, repos);
                        }
                    }
                }
                File prefsDir = new File(activity.getApplicationInfo().dataDir, "shared_prefs");
                if (prefsDir.exists() && prefsDir.listFiles() != null) {
                    for (File f : prefsDir.listFiles()) {
                        extractStringsFromFile(f, repos);
                    }
                }
            } catch (Exception ignored) {
            }
            JSONArray arr = new JSONArray(repos);
            return arr.toString();
        }

        @JavascriptInterface
        public void addSourceDirectly(String urlOrRepo) {
            mainHandler.post(() -> {
                String cleanUrl = urlOrRepo;
                if (cleanUrl.startsWith("/")) {
                    cleanUrl = COMMUNITY_URL + cleanUrl;
                }
                triggerDirectSourceAdd(activity, cleanUrl);
                dialog.dismiss();
            });
        }

        private void extractStringsFromFile(File file, Set<String> outSet) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
                String line;
                Pattern pattern = Pattern.compile("github\\.com/([a-zA-Z0-9_.-]+/[a-zA-Z0-9_.-]+)");
                while ((line = reader.readLine()) != null) {
                    Matcher matcher = pattern.matcher(line);
                    while (matcher.find()) {
                        outSet.add(matcher.group(1));
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    public static void checkAndAutoPatchLatestRelease(Activity activity) {
        attachInAppHubTrigger(activity);

        SharedPreferences prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean autoCheck = prefs.getBoolean("auto_check_and_patch", true);
        if (!autoCheck) {
            return;
        }

        executor.execute(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(GITHUB_LATEST_API).openConnection();
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                if (conn.getResponseCode() != 200) {
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                JSONObject json = new JSONObject(sb.toString());
                String latestTag = json.optString("tag_name", "").replaceFirst("^v", "");
                String currentVersion = activity.getPackageManager()
                        .getPackageInfo(activity.getPackageName(), 0).versionName;

                if (!latestTag.isEmpty() && !latestTag.equals(currentVersion)) {
                    JSONArray assets = json.optJSONArray("assets");
                    if (assets != null && assets.length() > 0) {
                        String downloadUrl = assets.getJSONObject(0).optString("browser_download_url", "");
                        if (downloadUrl.endsWith(".apk")) {
                            mainHandler.post(() -> promptOrRunAutoPatch(activity, latestTag, downloadUrl));
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        });
    }

    private static void promptOrRunAutoPatch(Activity activity, String version, String apkUrl) {
        SharedPreferences prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean silentAutoPatch = prefs.getBoolean("auto_download_and_patch_bg", true);

        if (silentAutoPatch) {
            downloadAndPatchApk(activity, version, apkUrl);
        } else {
            new AlertDialog.Builder(activity)
                    .setTitle("Morphe আপডেট পাওয়া গেছে (" + version + ")")
                    .setMessage("নতুন ভার্সনটি অটোমেটিক ডাউনলোড করে আপনার কাস্টম প্যাচসহ প্রস্তুত করতে চান?")
                    .setPositiveButton("অটো-প্যাচ ও ইনস্টল", (d, w) -> downloadAndPatchApk(activity, version, apkUrl))
                    .setNegativeButton("পরে", null)
                    .show();
        }
    }

    private static void downloadAndPatchApk(Activity activity, String version, String apkUrl) {
        Toast.makeText(activity, "Morphe " + version + " ডাউনলোড ও অটো-প্যাচ শুরু হচ্ছে...", Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            try {
                File outDir = new File(activity.getExternalFilesDir(null), "auto_patched");
                if (!outDir.exists()) {
                    outDir.mkdirs();
                }
                File rawApk = new File(outDir, "morphe-raw-" + version + ".apk");

                HttpURLConnection conn = (HttpURLConnection) new URL(apkUrl).openConnection();
                InputStream in = conn.getInputStream();
                FileOutputStream out = new FileOutputStream(rawApk);
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.close();
                in.close();

                File patchedApk = applySelfPatchInternal(activity, rawApk, new File(outDir, "morphe-patched-" + version + ".apk"));

                mainHandler.post(() -> promptInstallApk(activity, patchedApk));
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(activity, "অটো-প্যাচ ব্যর্থ: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private static File applySelfPatchInternal(Activity activity, File inputApk, File outputApk) throws Exception {
        SharedPreferences prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String savedMppPath = prefs.getString("custom_mpp_path", "/sdcard/Download/custom-morphe-self.mpp");
        File mppFile = new File(savedMppPath);

        if (mppFile.exists()) {
            Intent patchIntent = new Intent("app.morphe.manager.ACTION_HEADLESS_PATCH");
            patchIntent.setPackage(activity.getPackageName());
            patchIntent.putExtra("input_apk", inputApk.getAbsolutePath());
            patchIntent.putExtra("patch_bundle", mppFile.getAbsolutePath());
            patchIntent.putExtra("output_apk", outputApk.getAbsolutePath());
            activity.sendBroadcast(patchIntent);
        }
        return outputApk.exists() ? outputApk : inputApk;
    }

    private static void promptInstallApk(Activity activity, File apkFile) {
        Intent installIntent = new Intent(Intent.ACTION_VIEW);
        Uri apkUri;
        try {
            Class<?> fileProviderClass = Class.forName("androidx.core.content.FileProvider");
            Method getUriMethod = fileProviderClass.getMethod(
                    "getUriForFile",
                    Context.class,
                    String.class,
                    File.class
            );
            apkUri = (Uri) getUriMethod.invoke(
                    null,
                    activity,
                    activity.getPackageName() + ".provider",
                    apkFile
            );
        } catch (Exception e) {
            apkUri = Uri.fromFile(apkFile);
        }
        installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(installIntent);
    }
}
