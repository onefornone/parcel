package com.parcel.store;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;
import androidx.core.content.FileProvider;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

/** Native bridge: installed-app detection, in-app APK download with progress, install, launch. */
@CapacitorPlugin(name = "Parcel")
public class ParcelPlugin extends Plugin {
    private volatile boolean cancelled = false;

    @PluginMethod
    public void getInstalled(PluginCall call) {
        JSArray list = call.getArray("packages");
        JSObject apps = new JSObject();
        PackageManager pm = getContext().getPackageManager();
        for (int i = 0; list != null && i < list.length(); i++) {
            try {
                String pkg = list.getString(i);
                PackageInfo pi = pm.getPackageInfo(pkg, 0);
                JSObject o = new JSObject();
                o.put("version", pi.versionName == null ? "" : pi.versionName);
                o.put("icon", iconB64(pm.getApplicationIcon(pkg)));
                apps.put(pkg, o);
            } catch (Exception ignored) { /* not installed */ }
        }
        JSObject r = new JSObject();
        r.put("apps", apps);
        call.resolve(r);
    }

    private String iconB64(Drawable d) {
        Bitmap b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, 96, 96);
        d.draw(c);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.PNG, 100, bo);
        return "data:image/png;base64," + Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
    }

    private static final String[] GH = {"140.82.112.3", "140.82.113.3", "140.82.114.3", "140.82.121.3"};
    private static final String[] GHAPI = {"140.82.112.6", "140.82.113.6", "140.82.114.6", "140.82.121.6"};

    /** HTTP client that also tries GitHub's alternate addresses if the default one is unreachable on this network. */
    private final OkHttpClient http = new OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .dns(new Dns() {
            @Override
            public List<InetAddress> lookup(String host) throws UnknownHostException {
                List<InetAddress> out = new ArrayList<>();
                try { out.addAll(Dns.SYSTEM.lookup(host)); } catch (Exception ignored) { }
                String[] extra = "github.com".equals(host) ? GH : "api.github.com".equals(host) ? GHAPI : null;
                if (extra != null) for (String ip : extra) {
                    try { out.add(InetAddress.getByAddress(host, InetAddress.getByName(ip).getAddress())); } catch (Exception ignored) { }
                }
                if (out.isEmpty()) throw new UnknownHostException(host);
                return out;
            }
        }).build();

    private Request req(String url, String accept) {
        Request.Builder b = new Request.Builder().url(url).header("User-Agent", "Parcel");
        if (accept != null) b.header("Accept", accept);
        return b.build();
    }

    /** Route 1: the release URL. Route 2: GitHub's official API asset endpoint (different servers). */
    private Response open(String url) throws IOException {
        IOException first = null;
        try {
            Response r = http.newCall(req(url, null)).execute();
            if (r.isSuccessful()) return r;
            r.close();
        } catch (IOException e) { first = e; }
        Matcher m = Pattern.compile("^https://github\\.com/([^/]+)/([^/]+)/releases/download/([^/]+)/(.+)$").matcher(url);
        if (m.matches()) {
            String base = "https://api.github.com/repos/" + m.group(1) + "/" + m.group(2) + "/releases/";
            try (Response j = http.newCall(req(base + "tags/" + m.group(3), "application/vnd.github+json")).execute()) {
                if (j.isSuccessful()) {
                    JSONArray as = new JSONObject(j.body().string()).getJSONArray("assets");
                    String want = URLDecoder.decode(m.group(4), "UTF-8");
                    for (int i = 0; i < as.length(); i++) {
                        JSONObject o = as.getJSONObject(i);
                        if (want.equals(o.getString("name"))) {
                            Response r = http.newCall(req(base + "assets/" + o.getLong("id"), "application/octet-stream")).execute();
                            if (r.isSuccessful()) return r;
                            r.close();
                        }
                    }
                }
            } catch (Exception e) { if (first == null) first = new IOException(e); }
        }
        throw first != null ? first : new IOException("The server refused the download");
    }

    private String friendly(Exception e) {
        String m = String.valueOf(e.getMessage());
        if (e instanceof IOException && (m.contains("connect") || m.contains("timeout") || m.contains("resolve") || m.contains("Unable")))
            return "Could not reach GitHub from this network (tried every route). Check your connection, or try a VPN or Private DNS.";
        return m;
    }

    @PluginMethod
    public void download(PluginCall call) {
        final String url = call.getString("url");
        final String id = call.getString("id", "");
        final boolean asFile = "file".equals(call.getString("kind", "apk"));
        final String name = call.getString("name", "file").replaceAll("[^A-Za-z0-9._-]", "_");
        final long hint = call.getLong("size", 0L);
        cancelled = false;
        new Thread(() -> {
            Context x = getContext();
            File out = null;
            Uri media = null;
            try (Response r = open(url)) {
                long hdr = r.body().contentLength();
                long total = hdr > 0 ? hdr : hint;
                OutputStream os;
                if (asFile && Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
                    cv.put(MediaStore.Downloads.RELATIVE_PATH, "Download/Parcel");
                    media = x.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    os = x.getContentResolver().openOutputStream(media);
                } else {
                    File dir = asFile ? new File(x.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Parcel") : new File(x.getCacheDir(), "apks");
                    dir.mkdirs();
                    if (!asFile) { File[] old = dir.listFiles(); if (old != null) for (File o : old) o.delete(); }
                    out = new File(dir, name);
                    os = new FileOutputStream(out);
                }
                long got = 0, last = 0;
                try (InputStream in = r.body().byteStream()) {
                    byte[] buf = new byte[32768];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        if (cancelled) throw new IOException("cancelled");
                        os.write(buf, 0, n);
                        got += n;
                        long now = System.currentTimeMillis();
                        if (now - last > 120) {
                            last = now;
                            JSObject p = new JSObject();
                            p.put("id", id); p.put("loaded", got); p.put("total", total);
                            notifyListeners("progress", p);
                        }
                    }
                } finally { try { os.close(); } catch (Exception ignored) { } }
                if (hdr > 0 && got != hdr) throw new IOException("Incomplete download. Please retry.");
                JSObject res = new JSObject();
                if (asFile) {
                    res.put("saved", (Build.VERSION.SDK_INT >= 29 ? "Download/Parcel/" : "Android/data/com.parcel.store/files/Download/Parcel/") + name);
                } else {
                    if (x.getPackageManager().getPackageArchiveInfo(out.getAbsolutePath(), 0) == null)
                        throw new IOException("The downloaded file is not a valid APK.");
                    res.put("path", out.getAbsolutePath());
                }
                call.resolve(res);
            } catch (Exception e) {
                if (out != null) out.delete();
                if (media != null) x.getContentResolver().delete(media, null, null);
                call.reject(friendly(e));
            }
        }).start();
    }

    @PluginMethod
    public void deviceInfo(PluginCall call) {
        JSArray abis = new JSArray();
        for (String a : Build.SUPPORTED_ABIS) abis.put(a);
        JSObject r = new JSObject();
        r.put("abis", abis);
        call.resolve(r);
    }

    @PluginMethod
    public void clean(PluginCall call) {
        File[] f = new File(getContext().getCacheDir(), "apks").listFiles();
        if (f != null) for (File x : f) x.delete();
        call.resolve();
    }

    @PluginMethod
    public void cancel(PluginCall call) { cancelled = true; call.resolve(); }

    @PluginMethod
    public void install(PluginCall call) {
        Context x = getContext();
        JSObject r = new JSObject();
        if (Build.VERSION.SDK_INT >= 26 && !x.getPackageManager().canRequestPackageInstalls()) {
            Intent s = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + x.getPackageName()));
            s.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            x.startActivity(s);
            r.put("needsPermission", true);
            call.resolve(r);
            return;
        }
        File f = new File(call.getString("path", ""));
        Uri u = FileProvider.getUriForFile(x, x.getPackageName() + ".fileprovider", f);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(u, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        x.startActivity(i);
        r.put("needsPermission", false);
        call.resolve(r);
    }

    @PluginMethod
    public void launch(PluginCall call) {
        Context x = getContext();
        Intent i = x.getPackageManager().getLaunchIntentForPackage(call.getString("package", ""));
        if (i == null) { call.reject("not installed"); return; }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        x.startActivity(i);
        call.resolve();
    }
}
