package dev.ryuya.oneuimods;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

final class Catalog {
  static List<AppEntry> read(String raw) throws JSONException {
    JSONObject root = new JSONObject(raw);
    if (root.getInt("schema") != 1) throw new JSONException("Unknown catalog schema");
    JSONArray items = root.getJSONArray("apps");
    if (items.length() < 1 || items.length() > 100) throw new JSONException("Invalid catalog size");
    List<AppEntry> result = new ArrayList<>();
    Set<String> repos = new HashSet<>(), packages = new HashSet<>();
    for (int i = 0; i < items.length(); i++) {
      AppEntry app = AppEntry.parse(items.getJSONObject(i));
      if (!repos.add(app.repo()) || !packages.add(app.packageName()))
        throw new JSONException("Duplicate catalog identity");
      result.add(app);
    }
    return List.copyOf(result);
  }

  static List<AppEntry> load(Context context) throws Exception {
    File cache = new File(context.getFilesDir(), "catalog.json");
    if (cache.isFile())
      try {
        return read(Io.read(cache));
      } catch (Exception ignored) {
      }
    try (InputStream in = context.getAssets().open("catalog.json")) {
      return read(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    }
  }

  static List<AppEntry> refresh(Context c) throws Exception {
    Network.Response response =
        Network.text(
            "https://raw.githubusercontent.com/OneUI-Mods/OneUIModsManager/main/catalog/apps.json",
            null,
            512000);
    List<AppEntry> result = read(response.text());
    Io.write(new File(c.getFilesDir(), "catalog.json"), response.text());
    return result;
  }

  private Catalog() {}
}
