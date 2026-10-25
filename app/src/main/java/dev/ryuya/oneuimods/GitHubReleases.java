package dev.ryuya.oneuimods;

import java.util.*;
import org.json.*;

final class GitHubReleases {
  static List<Release> parse(AppEntry app, String raw) throws JSONException {
    JSONArray all = new JSONArray(raw);
    List<Release> result = new ArrayList<>();
    for (int i = 0; i < all.length(); i++) {
      JSONObject item = all.getJSONObject(i);
      if (item.optBoolean("draft")) continue;
      JSONArray assets = item.getJSONArray("assets");
      List<Release.Asset> apks = new ArrayList<>();
      Release.Asset sums = null;
      for (int n = 0; n < assets.length(); n++) {
        JSONObject a = assets.getJSONObject(n);
        String name = a.getString("name"), url = a.getString("browser_download_url");
        if (!ReleasePolicy.downloadUrl(app.repo(), url)) continue;
        Release.Asset asset =
            new Release.Asset(name, url, a.optString("digest", ""), a.getLong("size"));
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".apk") && !lower.contains("debug") && !lower.contains("unsigned"))
          apks.add(asset);
        if (lower.startsWith("sha256sums")) sums = asset;
      }
      if (apks.size() != 1) continue;
      String tag = item.getString("tag_name");
      AppEntry.KnownVersion known = app.knownVersions().get(tag);
      String page = app.repositoryUrl() + "/releases/tag/" + android.net.Uri.encode(tag);
      result.add(
          new Release(
              tag,
              item.optString("name", tag),
              item.optString("body"),
              page,
              item.optString("published_at"),
              item.optBoolean("prerelease"),
              apks.get(0),
              sums,
              known == null ? 0 : known.code(),
              known == null ? tag.replaceFirst("^[vV]", "") : known.name()));
    }
    return List.copyOf(result);
  }

  private GitHubReleases() {}
}
