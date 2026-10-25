package dev.ryuya.oneuimods;

import java.util.*;
import org.json.*;

record AppEntry(
    String repo,
    String packageName,
    String title,
    String description,
    String category,
    Set<String> certificates) {

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
    return new AppEntry(
        repo,
        pkg,
        value.getString("title"),
        value.optString("description"),
        value.optString("category", "device"),
        Set.copyOf(certs));
  }

  String repositoryUrl() {
    return "https://github.com/OneUI-Mods/" + repo;
  }
}
