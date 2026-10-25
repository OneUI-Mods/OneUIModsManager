package dev.ryuya.oneuimods;

import android.content.Context;
import java.io.*;
import org.json.*;

final class ReadyStore {
  record Ready(File file, long code, String version, String digest) {}

  static File folder(Context c) {
    File folder = new File(c.getFilesDir(), "updates");
    if (!folder.isDirectory() && !folder.mkdirs())
      throw new IllegalStateException("Cannot create update folder");
    return folder;
  }

  static File file(Context c, AppEntry app) {
    return new File(folder(c), app.repo() + ".apk");
  }

  static Ready read(Context c, AppEntry app) {
    try {
      JSONObject data =
          new JSONObject(ManagerState.get(c).preferences.getString("ready_" + app.repo(), "{}"));
      File file = file(c, app);
      if (!file.isFile()) return null;
      InstalledApp installed = InstalledApp.read(c, app);
      long code = data.getLong("code");
      if (installed != null && installed.code() >= code) {
        clear(c, app);
        return null;
      }
      return new Ready(file, code, data.getString("version"), data.getString("sha256"));
    } catch (JSONException missing) {
      return null;
    }
  }

  static void save(Context c, AppEntry app, ApkVerifier.Verified verified) throws JSONException {
    JSONObject data =
        new JSONObject()
            .put("code", verified.code())
            .put("version", verified.version())
            .put("sha256", verified.sha256());
    ManagerState.get(c)
        .preferences
        .edit()
        .putString("ready_" + app.repo(), data.toString())
        .apply();
  }

  static void clear(Context c, AppEntry app) {
    ManagerState.get(c).preferences.edit().remove("ready_" + app.repo()).apply();
    file(c, app).delete();
  }

  private ReadyStore() {}
}
