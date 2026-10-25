package dev.ryuya.oneuimods;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.*;

/** User-initiated foreground downloads. Installation always uses Android's confirmation UI. */
public final class DownloadService extends Service {
  private static final String CHANNEL = "downloads";
  private static final int NOTICE = 4;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final AtomicBoolean canceled = new AtomicBoolean();
  private ManagerState state;
  private boolean running;

  public void onCreate() {
    super.onCreate();
    state = ManagerState.get(this);
    getSystemService(NotificationManager.class)
        .createNotificationChannel(
            new NotificationChannel(
                CHANNEL, getString(R.string.download_channel), NotificationManager.IMPORTANCE_LOW));
  }

  public int onStartCommand(Intent intent, int flags, int id) {
    if (intent == null) {
      stopSelf();
      return START_NOT_STICKY;
    }
    if ("cancel".equals(intent.getAction())) {
      canceled.set(true);
      return START_NOT_STICKY;
    }
    if (running) return START_NOT_STICKY;
    running = true;
    canceled.set(false);
    state.downloading = true;
    state.downloadError = "";
    startForeground(
        NOTICE,
        notification(getString(R.string.preparing), 0),
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
    ArrayList<String> requested = intent.getStringArrayListExtra("repos");
    worker.execute(
        () -> {
          try {
            if (requested != null)
              for (String repo : new LinkedHashSet<>(requested)) {
                if (canceled.get()) break;
                AppEntry app = state.find(repo);
                if (app == null) continue;
                Release release = state.release(app);
                if (release == null || ReadyStore.read(this, app) != null) continue;
                state.downloadRepo = repo;
                state.downloadPercent = 0;
                state.notifyChanged();
                try {
                  download(app, release);
                } catch (ApkVerifier.Rejected rejected) {
                  state.downloadError = rejected.reason;
                  break;
                } catch (Exception failed) {
                  state.downloadError = canceled.get() ? "canceled" : "network";
                  break;
                }
              }
          } finally {
            state.downloading = false;
            state.downloadRepo = "";
            state.notifyChanged();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
          }
        });
    return START_NOT_STICKY;
  }

  private void download(AppEntry app, Release release) throws Exception {
    String expected = release.apk().digest();
    if (expected.startsWith("sha256:")) expected = expected.substring(7);
    if (!expected.matches("[a-fA-F0-9]{64}")) {
      if (release.checksums() == null) throw new ApkVerifier.Rejected("missing_hash");
      String sums = Network.text(release.checksums().url(), null, 128000).text();
      Pattern line =
          Pattern.compile(
              "(?m)^([a-fA-F0-9]{64})[ \t]+\\*?" + Pattern.quote(release.apk().name()) + "[ \t]*$");
      Matcher match = line.matcher(sums);
      if (!match.find()) throw new ApkVerifier.Rejected("missing_hash");
      expected = match.group(1);
    }
    if (!ReleasePolicy.downloadUrl(app.repo(), release.apk().url()))
      throw new ApkVerifier.Rejected("origin");
    long expectedSize = release.apk().size();
    if (expectedSize < 1024 || expectedSize > 256L * 1024 * 1024)
      throw new ApkVerifier.Rejected("size");
    File partial = new File(ReadyStore.folder(this), app.repo() + ".part.apk");
    HttpURLConnection connection =
        Network.open(release.apk().url(), "application/octet-stream", null);
    try {
      try (InputStream in = connection.getInputStream();
          OutputStream out = Files.newOutputStream(partial.toPath())) {
        byte[] bytes = new byte[65536];
        long total = 0, last = 0;
        int count;
        while ((count = in.read(bytes)) != -1) {
          if (canceled.get()) throw new InterruptedIOException("Canceled");
          total += count;
          if (total > expectedSize) throw new ApkVerifier.Rejected("size");
          out.write(bytes, 0, count);
          long now = SystemClock.elapsedRealtime();
          if (now - last > 250) {
            last = now;
            state.downloadPercent = (int) (total * 100 / expectedSize);
            state.notifyChanged();
            getSystemService(NotificationManager.class)
                .notify(NOTICE, notification(app.title(), state.downloadPercent));
          }
        }
        if (total != expectedSize) throw new ApkVerifier.Rejected("size");
      }
      ApkVerifier.Verified verified =
          ApkVerifier.verify(this, app, partial, expected, release.versionCode());
      File target = ReadyStore.file(this, app);
      Files.move(
          partial.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      ReadyStore.save(this, app, verified);
      state.notifyChanged();
    } finally {
      connection.disconnect();
      partial.delete();
    }
  }

  private Notification notification(String title, int progress) {
    PendingIntent open =
        PendingIntent.getActivity(
            this,
            0,
            new Intent(this, MainActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    PendingIntent cancel =
        PendingIntent.getService(
            this,
            1,
            new Intent(this, DownloadService.class).setAction("cancel"),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    return new Notification.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_download)
        .setContentTitle(title)
        .setContentText(getString(R.string.downloading))
        .setContentIntent(open)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setProgress(100, progress, false)
        .addAction(
            new Notification.Action.Builder(null, getString(R.string.cancel), cancel).build())
        .build();
  }

  public void onDestroy() {
    canceled.set(true);
    worker.shutdown();
    super.onDestroy();
  }

  public IBinder onBind(Intent intent) {
    return null;
  }
}
