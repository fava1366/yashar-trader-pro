package com.fava.marketpattern;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.JavascriptInterface;
import org.json.JSONObject;
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
  WebView web;
  ExecutorService pool = Executors.newFixedThreadPool(2);
  Handler main = new Handler(Looper.getMainLooper());

  public void onCreate(Bundle b) {
    super.onCreate(b);
    web = new WebView(this);
    WebSettings s = web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setAllowFileAccess(true);
    s.setMediaPlaybackRequiresUserGesture(false);
    web.addJavascriptInterface(new Bridge(), "MarketApi");
    setContentView(web);
    web.loadUrl("file:///android_asset/index.html");
  }

  class Bridge {
    @JavascriptInterface
    public String loadKey() {
      return getSharedPreferences("yashar_trader", MODE_PRIVATE).getString("api_key", "");
    }

    @JavascriptInterface
    public void saveKey(String key) {
      getSharedPreferences("yashar_trader", MODE_PRIVATE).edit()
          .putString("api_key", key == null ? "" : key.trim()).apply();
    }

    @JavascriptInterface
    public void getCandles(String symbol, String key, String cb) {
      pool.execute(() -> {
        String out = httpGet("https://api.twelvedata.com/time_series?symbol="
            + enc(symbol) + "&interval=1min&outputsize=500&order=ASC&timezone=UTC&apikey=" + enc(key));
        final String q = JSONObject.quote(out);
        main.post(() -> {
          if (web != null) web.evaluateJavascript(cb + "(" + q + ")", null);
        });
      });
    }

    @JavascriptInterface
    public void getQuote(String symbol, String key, String cb) {
      pool.execute(() -> {
        String out = httpGet("https://api.twelvedata.com/quote?symbol="
            + enc(symbol) + "&apikey=" + enc(key));
        final String q = JSONObject.quote(out);
        main.post(() -> {
          if (web != null) web.evaluateJavascript(cb + "(" + q + ")", null);
        });
      });
    }

    private String enc(String s) {
      try { return URLEncoder.encode(s == null ? "" : s, "UTF-8"); }
      catch (Exception e) { return ""; }
    }

    private String httpGet(String urlStr) {
      HttpURLConnection c = null;
      try {
        c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("Accept", "application/json");
        int code = c.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder b = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) b.append(line);
        r.close();
        return b.toString();
      } catch (Exception e) {
        try {
          JSONObject j = new JSONObject();
          j.put("status", "error");
          j.put("message", e.getClass().getSimpleName() + ": " + e.getMessage());
          return j.toString();
        } catch (Exception ignored) {
          return "{\"status\":\"error\",\"message\":\"unknown\"}";
        }
      } finally {
        if (c != null) c.disconnect();
      }
    }
  }

  public void onDestroy() {
    if (web != null) {
      web.removeJavascriptInterface("MarketApi");
      web.destroy();
      web = null;
    }
    pool.shutdownNow();
    super.onDestroy();
  }
}
