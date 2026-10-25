package dev.ryuya.oneuimods;

import android.Manifest;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.*;

/** Installation is sequential, user-confirmed, and rechecks the local APK before sharing it. */
abstract class InstallerActivity extends AppCompatActivity {
  protected ManagerState state;
  private final ArrayDeque<String> queue = new ArrayDeque<>();
  private String autoRepo = "", installRepo = "", permissionRepo = "";
  private long installCode;
  private boolean resumed, installing, awaitingResult;
  private final Runnable changed =
      () -> {
        refreshView();
        maybeAutoInstall();
      };

  protected void onCreate(Bundle saved) {
    super.onCreate(saved);
    state = ManagerState.get(this);
    if (saved != null) {
      autoRepo = saved.getString("auto", "");
      installRepo = saved.getString("install", "");
      permissionRepo = saved.getString("permission", "");
      installCode = saved.getLong("install_code");
      awaitingResult = saved.getBoolean("awaiting");
      installing = awaitingResult;
      if (!awaitingResult && !installRepo.isEmpty()) autoRepo = installRepo;
      var list = saved.getStringArrayList("queue");
      if (list != null) queue.addAll(list);
    }
  }

  protected abstract void refreshView();

  protected void onStart() {
    super.onStart();
    state.add(changed);
  }

  protected void onStop() {
    state.remove(changed);
    super.onStop();
  }

  protected void onResume() {
    super.onResume();
    resumed = true;
    InstalledApp.invalidate();
    refreshView();
    maybeAutoInstall();
  }

  protected void onPause() {
    resumed = false;
    super.onPause();
  }

  private void maybeAutoInstall() {
    if (!resumed || autoRepo.isEmpty() || installing) return;
    AppEntry app = state.find(autoRepo);
    if (app != null && ReadyStore.read(this, app) != null) {
      String repo = autoRepo;
      autoRepo = "";
      install(repo);
    } else if (!state.downloading) autoRepo = "";
  }

  protected void download(List<String> repos, boolean automatic) {
    if (repos.isEmpty() || state.downloading) return;
    if (!state.preferences.getBoolean("asked_notifications", false)) {
      state.preferences.edit().putBoolean("asked_notifications", true).apply();
      if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
          != android.content.pm.PackageManager.PERMISSION_GRANTED)
        requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 104);
    }
    if (automatic && repos.size() == 1) autoRepo = repos.get(0);
    startForegroundService(
        new Intent(this, DownloadService.class)
            .putStringArrayListExtra("repos", new ArrayList<>(repos)));
  }

  protected void installPrepared(List<String> repos) {
    queue.clear();
    repos.stream().filter(r -> !r.equals("OneUIModsManager")).forEach(queue::add);
    if (repos.contains("OneUIModsManager")) queue.add("OneUIModsManager");
    if (!queue.isEmpty()) install(queue.peek());
  }

  protected void install(String repo) {
    if (installing) return;
    AppEntry app = state.find(repo);
    if (app == null) return;
    ReadyStore.Ready ready = ReadyStore.read(this, app);
    if (ready == null) return;
    if (!getPackageManager().canRequestPackageInstalls()) {
      permissionRepo = repo;
      startActivityForResult(
          new Intent(
              Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())),
          101);
      return;
    }
    installing = true;
    installRepo = repo;
    installCode = ready.code();
    state.worker.execute(
        () -> {
          try {
            ApkVerifier.verify(this, app, ready.file(), ready.digest(), ready.code());
            state.main.post(
                () -> {
                  if (!resumed || isFinishing()) {
                    installing = false;
                    return;
                  }
                  try {
                    Uri uri =
                        FileProvider.getUriForFile(
                            this, BuildConfig.APPLICATION_ID + ".files", ready.file());
                    Intent intent =
                        new Intent(Intent.ACTION_INSTALL_PACKAGE)
                            .setDataAndType(uri, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            .putExtra(Intent.EXTRA_RETURN_RESULT, true);
                    awaitingResult = true;
                    startActivityForResult(intent, 102);
                  } catch (RuntimeException failure) {
                    installing = false;
                    queue.clear();
                    message(getString(R.string.install_failed));
                  }
                });
          } catch (Exception failure) {
            state.main.post(
                () -> {
                  installing = false;
                  queue.clear();
                  ReadyStore.clear(this, app);
                  message(
                      Ui.error(
                          this, failure instanceof ApkVerifier.Rejected r ? r.reason : "network"));
                  refreshView();
                });
          }
        });
  }

  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == 101) {
      String repo = permissionRepo;
      permissionRepo = "";
      if (getPackageManager().canRequestPackageInstalls()) install(repo);
      else queue.clear();
    }
    if (request == 102) {
      installing = false;
      awaitingResult = false;
      AppEntry app = state.find(installRepo);
      InstalledApp now = app == null ? null : InstalledApp.fresh(this, app);
      boolean success = now != null && now.code() >= installCode;
      if (success) {
        ReadyStore.clear(this, app);
        if (!queue.isEmpty() && queue.peek().equals(installRepo)) queue.remove();
      } else {
        queue.clear();
        Toast.makeText(this, R.string.install_canceled, Toast.LENGTH_SHORT).show();
      }
      installRepo = "";
      refreshView();
      if (success && !queue.isEmpty()) state.main.postDelayed(() -> install(queue.peek()), 250);
    }
  }

  protected void message(String body) {
    new MaterialAlertDialogBuilder(this)
        .setTitle(R.string.error_title)
        .setMessage(body)
        .setPositiveButton(R.string.close, null)
        .show();
  }

  protected void onSaveInstanceState(Bundle out) {
    out.putString("auto", autoRepo);
    out.putString("install", installRepo);
    out.putString("permission", permissionRepo);
    out.putLong("install_code", installCode);
    out.putBoolean("awaiting", awaitingResult);
    out.putStringArrayList("queue", new ArrayList<>(queue));
    super.onSaveInstanceState(out);
  }
}
