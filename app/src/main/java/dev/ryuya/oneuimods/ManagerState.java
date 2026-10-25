package dev.ryuya.oneuimods;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Application-scoped state; no Activity references survive a detached UI. */
final class ManagerState {
  private static ManagerState instance;
  final Context context;
  final SharedPreferences preferences;
  final Handler main = new Handler(Looper.getMainLooper());
  final ExecutorService worker = Executors.newSingleThreadExecutor();
  final Map<String, List<Release>> feeds = new ConcurrentHashMap<>();
  final Map<String, String> errors = new ConcurrentHashMap<>();
  final Set<Runnable> listeners = new HashSet<>();
  volatile List<AppEntry> apps = List.of();
  volatile boolean checking;
  volatile int completed;
  volatile String downloadRepo = "";
  volatile int downloadPercent;
  volatile boolean downloading;
  volatile String downloadError = "";
  volatile long retryAt;

  private ManagerState(Context c) {
    context = c.getApplicationContext();
    preferences = context.getSharedPreferences("manager", 0);
    try {
      apps = Catalog.load(context);
    } catch (Exception e) {
      throw new IllegalStateException("Bundled catalog is invalid", e);
    }
    for (AppEntry app : apps) loadCached(app);
  }

  static synchronized ManagerState get(Context c) {
    if (instance == null) instance = new ManagerState(c);
    return instance;
  }

  void add(Runnable listener) {
    listeners.add(listener);
  }

  void remove(Runnable listener) {
    listeners.remove(listener);
  }

  void notifyChanged() {
    main.post(
        () -> {
          for (Runnable listener : List.copyOf(listeners)) listener.run();
        });
  }

  boolean previews() {
    return preferences.getBoolean("previews", true);
  }

  Release release(AppEntry app) {
    return ReleasePolicy.choose(feeds.getOrDefault(app.repo(), List.of()), previews());
  }

  AppEntry find(String repo) {
    for (AppEntry app : apps) if (app.repo().equals(repo)) return app;
    return null;
  }

  boolean update(AppEntry app) {
    InstalledApp installed = InstalledApp.read(context, app);
    return installed != null
        && ReleasePolicy.update(installed.code(), installed.name(), release(app));
  }

  void refresh(boolean force) {
    if (checking) return;
    if (!force && System.currentTimeMillis() - preferences.getLong("last_check", 0) < 6 * 3600000L)
      return;
    checking = true;
    completed = 0;
    retryAt = 0;
    notifyChanged();
    worker.execute(
        () -> {
          boolean all = true;
          try {
            apps = Catalog.refresh(context);
          } catch (Exception ignored) {
            /* Keep the verified bundled or last valid catalog offline. */
          }
          for (AppEntry app : apps) {
            try {
              File cache = new File(context.getFilesDir(), "release-" + app.repo() + ".json");
              String key = "etag_" + app.repo();
              String etag = cache.isFile() ? preferences.getString(key, "") : "";
              Network.Response response =
                  Network.text(
                      "https://api.github.com/repos/OneUI-Mods/"
                          + app.repo()
                          + "/releases?per_page=100",
                      etag,
                      4 * 1024 * 1024);
              String raw = response.unchanged() ? Io.read(cache) : response.text();
              List<Release> parsed = GitHubReleases.parse(app, raw);
              if (!response.unchanged()) Io.write(cache, raw);
              feeds.put(app.repo(), parsed);
              errors.remove(app.repo());
              preferences
                  .edit()
                  .putString(key, response.etag() == null ? "" : response.etag())
                  .putLong("checked_" + app.repo(), System.currentTimeMillis())
                  .apply();
            } catch (Exception error) {
              all = false;
              errors.put(
                  app.repo(), error instanceof Network.Failure f ? "HTTP " + f.status : "offline");
              if (error instanceof Network.Failure f && f.retryAt > System.currentTimeMillis()) {
                retryAt = f.retryAt;
                break;
              }
            }
            completed++;
            notifyChanged();
          }
          if (all) preferences.edit().putLong("last_check", System.currentTimeMillis()).apply();
          checking = false;
          notifyChanged();
        });
  }

  private void loadCached(AppEntry app) {
    try {
      feeds.put(
          app.repo(),
          GitHubReleases.parse(
              app, Io.read(new File(context.getFilesDir(), "release-" + app.repo() + ".json"))));
    } catch (Exception ignored) {
    }
  }
}
