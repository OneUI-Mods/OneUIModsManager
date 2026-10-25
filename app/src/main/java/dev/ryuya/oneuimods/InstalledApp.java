package dev.ryuya.oneuimods;

import android.content.Context;
import android.content.pm.PackageManager;

record InstalledApp(long code, String name, boolean enabled) {
  private static final java.util.Map<String, java.util.Optional<InstalledApp>> CACHE =
      new java.util.HashMap<>();

  static synchronized void invalidate() {
    CACHE.clear();
  }

  static synchronized InstalledApp read(Context c, AppEntry app) {
    return CACHE
        .computeIfAbsent(app.packageName(), key -> java.util.Optional.ofNullable(fresh(c, app)))
        .orElse(null);
  }

  static synchronized InstalledApp fresh(Context c, AppEntry app) {
    try {
      var info = c.getPackageManager().getPackageInfo(app.packageName(), 0);
      return new InstalledApp(
          info.getLongVersionCode(),
          info.versionName,
          info.applicationInfo == null || info.applicationInfo.enabled);
    } catch (PackageManager.NameNotFoundException missing) {
      return null;
    }
  }
}
