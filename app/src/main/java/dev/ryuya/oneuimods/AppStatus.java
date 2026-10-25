package dev.ryuya.oneuimods;

import android.content.Context;

final class AppStatus {
  static String label(Context c, ManagerState state, AppEntry app) {
    if (ReadyStore.read(c, app) != null) return c.getString(R.string.ready);
    if (state.downloading && state.downloadRepo.equals(app.repo()))
      return c.getString(R.string.downloading);
    InstalledApp installed = InstalledApp.read(c, app);
    Release release = state.release(app);
    if (installed == null) return c.getString(R.string.not_installed);
    if (release == null)
      return c.getString(
          state.errors.containsKey(app.repo()) ? R.string.cached : R.string.no_release);
    if (ReleasePolicy.update(installed.code(), installed.name(), release))
      return c.getString(R.string.update_available);
    if (release.versionCode() > 0)
      return c.getString(
          installed.code() > release.versionCode() ? R.string.ahead : R.string.latest);
    var compared = VersionOrder.compare(installed.name(), release.versionName());
    return c.getString(
        compared.isEmpty()
            ? R.string.unknown_version
            : compared.getAsInt() > 0 ? R.string.ahead : R.string.latest);
  }

  private AppStatus() {}
}
