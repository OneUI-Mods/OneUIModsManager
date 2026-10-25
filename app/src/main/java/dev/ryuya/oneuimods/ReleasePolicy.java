package dev.ryuya.oneuimods;

import java.net.URI;
import java.util.*;

final class ReleasePolicy {
  static boolean downloadUrl(String repo, String url) {
    try {
      URI u = URI.create(url);
      return "https".equals(u.getScheme())
          && "github.com".equals(u.getHost())
          && u.getUserInfo() == null
          && u.getPort() == -1
          && u.getPath().startsWith("/OneUI-Mods/" + repo + "/releases/download/");
    } catch (IllegalArgumentException bad) {
      return false;
    }
  }

  static Release choose(List<Release> releases, boolean includePreview) {
    Release best = null;
    for (Release item : releases) {
      if (item.preview() && !includePreview) continue;
      if (best == null) {
        best = item;
        continue;
      }
      int c;
      if (item.versionCode() > 0 && best.versionCode() > 0)
        c = Long.compare(item.versionCode(), best.versionCode());
      else
        c =
            VersionOrder.compare(item.versionName(), best.versionName())
                .orElse(item.published().compareTo(best.published()));
      if (c > 0) best = item;
    }
    return best;
  }

  static boolean update(long installedCode, String installedName, Release release) {
    if (release == null) return false;
    if (release.versionCode() > 0) return release.versionCode() > installedCode;
    return VersionOrder.compare(release.versionName(), installedName).orElse(0) > 0;
  }

  private ReleasePolicy() {}
}
