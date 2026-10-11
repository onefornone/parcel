package com.parcel.store;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
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
import java.net.HttpURLConnection;
import java.net.URL;

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

    @PluginMethod
    public void download(PluginCall call) {
        final String url = call.getString("url");
        final String name = call.getString("name", "app.apk").replaceAll("[^A-Za-z0-9._-]", "_");
        final String id = call.getString("id", "");
        final long hint = call.getLong("size", 0L);
        cancelled = false;
        new Thread(() -> {
            try {
                File dir = new File(getContext().getCacheDir(), "apks");
                dir.mkdirs();
                File[] old = dir.listFiles();
                if (old != null) for (File o : old) o.delete();
                File out = new File(dir, name);
                HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
                h.setInstanceFollowRedirects(true);
                h.setConnectTimeout(15000);
                h.setReadTimeout(30000);
                if (h.getResponseCode() >= 400) { call.reject("HTTP " + h.getResponseCode()); return; }
                long total = h.getContentLengthLong() > 0 ? h.getContentLengthLong() : hint;
                long got = 0, last = 0;
                try (InputStream in = h.getInputStream(); OutputStream os = new FileOutputStream(out)) {
                    byte[] buf = new byte[32768];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        if (cancelled) { os.close(); out.delete(); call.reject("cancelled"); return; }
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
                }
                long hdr = h.getContentLengthLong();
                if (hdr > 0 && got != hdr) { out.delete(); call.reject("Incomplete download. Please retry."); return; }
                if (getContext().getPackageManager().getPackageArchiveInfo(out.getAbsolutePath(), 0) == null) {
                    out.delete(); call.reject("The downloaded file is not a valid APK."); return;
                }
                JSObject r = new JSObject();
                r.put("path", out.getAbsolutePath());
                call.resolve(r);
            } catch (Exception e) {
                call.reject(String.valueOf(e.getMessage()));
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
