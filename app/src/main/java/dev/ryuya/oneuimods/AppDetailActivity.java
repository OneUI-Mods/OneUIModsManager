package dev.ryuya.oneuimods;

import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.*;
import android.text.style.*;
import android.widget.*;
import androidx.core.widget.NestedScrollView;
import com.google.android.material.button.MaterialButton;
import java.util.*;

public final class AppDetailActivity extends InstallerActivity {
  private AppEntry app;
  private LinearLayout body;
  private NestedScrollView scroll;
  private int savedScroll;
  private String renderedKey = "";
  private com.google.android.material.textview.MaterialTextView downloadProgress;

  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    app = state.find(getIntent().getStringExtra("repo"));
    if (app == null) {
      finish();
      return;
    }
    LinearLayout root = Ui.root(this, app.title(), true);
    scroll = new NestedScrollView(this);
    body = Ui.vertical(this);
    body.setPadding(Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 24));
    scroll.addView(body);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    savedScroll = saved == null ? 0 : saved.getInt("scroll");
    refreshView();
  }

  protected void refreshView() {
    if (body == null) return;
    Release currentRelease = state.release(app);
    String key =
        AppStatus.label(this, state, app)
            + InstalledApp.read(this, app)
            + (currentRelease == null ? "" : currentRelease.tag())
            + state.downloading
            + state.downloadError;
    if (key.equals(renderedKey)) {
      if (downloadProgress != null)
        downloadProgress.setText(
            getString(R.string.download_progress, app.title(), state.downloadPercent));
      return;
    }
    renderedKey = key;
    downloadProgress = null;
    int y = Math.max(savedScroll, scroll.getScrollY());
    savedScroll = 0;
    body.removeAllViews();
    LinearLayout intro = Ui.card(this, body, true);
    intro.addView(Ui.text(this, app.title(), true));
    intro.addView(Ui.text(this, app.description(), false));
    intro.addView(Ui.text(this, AppStatus.label(this, state, app), true));
    InstalledApp installed = InstalledApp.read(this, app);
    Release release = state.release(app);
    ReadyStore.Ready ready = ReadyStore.read(this, app);
    if (installed != null)
      intro.addView(Ui.text(this, getString(R.string.installed_version, installed.name()), false));
    if (release != null) {
      intro.addView(
          Ui.text(this, getString(R.string.release_version, release.versionName()), false));
      intro.addView(
          Ui.text(
              this,
              getString(release.preview() ? R.string.preview_badge : R.string.stable_badge),
              false));
    }
    if (ready != null) {
      intro.addView(Ui.text(this, getString(R.string.verified), false));
      MaterialButton install = Ui.button(this, R.string.install, true);
      install.setOnClickListener(v -> install(app.repo()));
      intro.addView(install, new LinearLayout.LayoutParams(-1, -2));
    } else if (release != null && (installed == null || state.update(app))) {
      MaterialButton download =
          Ui.button(this, installed == null ? R.string.download : R.string.update, true);
      download.setEnabled(!state.downloading);
      download.setOnClickListener(v -> download(List.of(app.repo()), true));
      intro.addView(download, new LinearLayout.LayoutParams(-1, -2));
    }
    if (state.downloading && state.downloadRepo.equals(app.repo())) {
      downloadProgress =
          Ui.text(
              this,
              getString(R.string.download_progress, app.title(), state.downloadPercent),
              false);
      intro.addView(downloadProgress);
    }
    if (!state.downloadError.isEmpty())
      intro.addView(Ui.text(this, Ui.error(this, state.downloadError), false));
    if (installed != null) {
      MaterialButton open = Ui.button(this, R.string.open_app, false);
      open.setOnClickListener(
          v -> {
            Intent i = getPackageManager().getLaunchIntentForPackage(app.packageName());
            if (i != null) startActivity(i);
            else Toast.makeText(this, R.string.app_missing, Toast.LENGTH_SHORT).show();
          });
      intro.addView(open, new LinearLayout.LayoutParams(-1, -2));
    }
    LinearLayout notes = Ui.card(this, body, false);
    notes.addView(Ui.text(this, getString(R.string.whats_new), true));
    var text =
        Ui.text(
            this,
            renderNotes(release == null ? getString(R.string.no_notes) : release.notes()),
            false);
    text.setTextIsSelectable(true);
    notes.addView(text);
    notes.addView(Ui.text(this, getString(R.string.reload_note), false));
    MaterialButton github = Ui.button(this, R.string.release_page, false);
    github.setOnClickListener(
        v ->
            Ui.browse(
                this, release == null ? app.repositoryUrl() + "/releases" : release.pageUrl()));
    notes.addView(github, new LinearLayout.LayoutParams(-1, -2));
    if (installed != null) {
      MaterialButton info = Ui.button(this, R.string.app_info, false);
      info.setOnClickListener(
          v ->
              startActivity(
                  new Intent(
                      Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                      Uri.parse("package:" + app.packageName()))));
      body.addView(info, new LinearLayout.LayoutParams(-1, -2));
    }
    scroll.post(() -> scroll.scrollTo(0, y));
  }

  /**
   * Small, non-executable Markdown subset for headings, bullets and emphasis. Full notes stay
   * linked.
   */
  private CharSequence renderNotes(String input) {
    if (input == null || input.isBlank()) return getString(R.string.no_notes);
    SpannableStringBuilder out = new SpannableStringBuilder();
    for (String line : input.split("\\R")) {
      boolean heading = line.startsWith("#");
      String clean =
          line.replaceFirst("^#{1,6}\\s+", "")
              .replaceFirst("^[-*] ", "• ")
              .replace("**", "")
              .replace("`", "");
      int start = out.length();
      out.append(clean).append("\n");
      if (heading)
        out.setSpan(
            new StyleSpan(android.graphics.Typeface.BOLD),
            start,
            out.length(),
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    return out;
  }

  protected void onSaveInstanceState(Bundle out) {
    if (scroll != null) out.putInt("scroll", scroll.getScrollY());
    super.onSaveInstanceState(out);
  }
}
