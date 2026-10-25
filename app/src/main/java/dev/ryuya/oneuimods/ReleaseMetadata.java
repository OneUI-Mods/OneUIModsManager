package dev.ryuya.oneuimods;

import android.content.Context;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.json.JSONObject;

/** Optional, APK-derived release information. Never requires a catalog edit for a new version. */
final class ReleaseMetadata {
  static Release apply(
      AppEntry app,
      Release release,
      String pkg,
      String apk,
      long code,
      String version,
      String sha) {
    if (!app.packageName().equals(pkg)
        || !release.apk().name().equals(apk)
        || code <= 0
        || version.isBlank()
        || version.length() > 200
        || !sha.matches("[a-fA-F0-9]{64}"))
      throw new IllegalArgumentException("Metadata does not match the release APK");
    String apiDigest = release.apk().digest();
    if (apiDigest.startsWith("sha256:") && !apiDigest.substring(7).equalsIgnoreCase(sha))
      throw new IllegalArgumentException("APK digest mismatch");
    return new Release(
        release.tag(),
        release.name(),
        release.notes(),
        release.pageUrl(),
        release.published(),
        release.preview(),
        new Release.Asset(apk, release.apk().url(), "sha256:" + sha, release.apk().size()),
        release.checksums(),
        release.metadata(),
        code,
        version);
  }

  static List<Release> load(Context context, AppEntry app, List<Release> releases, boolean online) {
    // Fetch at most the stable and preview candidates, rather than downloading every old release.
    Set<Release> candidates = new HashSet<>();
    candidates.add(ReleasePolicy.choose(releases, false));
    candidates.add(ReleasePolicy.choose(releases, true));
    List<Release> result = new ArrayList<>();
    for (Release release : releases) {
      Release enriched = release;
      if (candidates.contains(release) && release.metadata() != null) {
        try {
          enriched = read(context, app, release, online);
        } catch (Exception unavailable) {
          // Old releases and unavailable/invalid metadata retain the tag-based comparison.
        }
      }
      result.add(enriched);
    }
    return List.copyOf(result);
  }

  private static Release read(Context context, AppEntry app, Release release, boolean online)
      throws Exception {
    Release.Asset asset = release.metadata();
    if (!ReleasePolicy.downloadUrl(app.repo(), asset.url())
        || asset.size() < 1
        || asset.size() > 16384) throw new IllegalArgumentException("Invalid metadata asset");
    String key = hash((asset.url() + "\n" + asset.digest()).getBytes(StandardCharsets.UTF_8));
    File cache = new File(context.getFilesDir(), "metadata-" + key + ".json");
    if (cache.isFile() && (!online || asset.digest().startsWith("sha256:"))) {
      try {
        return parse(app, release, Io.read(cache));
      } catch (Exception invalidCache) {
        if (!online) throw invalidCache;
      }
    }
    if (!online) throw new IllegalStateException("No cached release metadata");
    String raw = Network.text(asset.url(), null, 16384).text();
    Release parsed = parse(app, release, raw);
    Io.write(cache, raw);
    return parsed;
  }

  private static Release parse(AppEntry app, Release release, String raw) throws Exception {
    byte[] bytes = raw.getBytes(StandardCharsets.UTF_8);
    if (bytes.length > 16384) throw new IllegalArgumentException("Metadata too large");
    String digest = release.metadata().digest();
    if (digest.startsWith("sha256:") && !hash(bytes).equalsIgnoreCase(digest.substring(7)))
      throw new IllegalArgumentException("Metadata digest mismatch");
    JSONObject value = new JSONObject(raw);
    if (value.getInt("schema") != 1) throw new IllegalArgumentException("Unknown metadata schema");
    String code = value.get("versionCode").toString();
    if (!code.matches("[1-9][0-9]{0,18}"))
      throw new IllegalArgumentException("Invalid APK build number");
    return apply(
        app,
        release,
        value.getString("packageName"),
        value.getString("apk"),
        Long.parseLong(code),
        value.getString("versionName"),
        value.getString("sha256"));
  }

  private static String hash(byte[] data) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
  }

  private ReleaseMetadata() {}
}
