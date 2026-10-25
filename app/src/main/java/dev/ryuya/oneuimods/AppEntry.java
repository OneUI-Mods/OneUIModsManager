package dev.ryuya.oneuimods;

import java.util.*;
import org.json.*;

record AppEntry(
    String repo,
    String packageName,
    String title,
    String description,
    String category,
    Set<String> certificates,
    Map<String, KnownVersion> knownVersions) {
  record KnownVersion(long code, String name) {}

  static AppEntry parse(JSONObject value) throws JSONException {
    String repo = value.getString("repo"), pkg = value.getString("packageName");
    if (!repo.matches("[A-Za-z0-9_.-]{1,100}")
        || !pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"))
      throw new JSONException("Invalid app identity");
    Set<String> certs = new HashSet<>();
    JSONArray pins = value.getJSONArray("certificates");
    for (int i = 0; i < pins.length(); i++) {
      String pin = pins.getString(i).toLowerCase(Locale.ROOT);
      if (!pin.matches("[a-f0-9]{64}")) throw new JSONException("Invalid certificate");
      certs.add(pin);
    }
    if (certs.isEmpty()) throw new JSONException("Missing certificate");
    Map<String, KnownVersion> versions = new HashMap<>();
    JSONObject known = value.optJSONObject("knownVersions");
    if (known != null)
      for (var keys = known.keys(); keys.hasNext(); ) {
        String tag = keys.next();
        JSONObject v = known.getJSONObject(tag);
        versions.put(tag, new KnownVersion(v.getLong("code"), v.getString("name")));
      }
    return new AppEntry(
        repo,
        pkg,
        value.getString("title"),
        value.optString("description"),
        value.optString("category", "device"),
        Set.copyOf(certs),
        Map.copyOf(versions));
  }

  String repositoryUrl() {
    return "https://github.com/OneUI-Mods/" + repo;
  }
}
