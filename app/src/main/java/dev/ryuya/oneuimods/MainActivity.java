package dev.ryuya.oneuimods;

import android.app.LocaleManager;
import android.content.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.*;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.*;
import com.google.android.material.textview.MaterialTextView;
import java.text.DateFormat;
import java.util.*;

/** A searchable catalog, a focused update queue, and user-controlled release channels. */
public final class MainActivity extends InstallerActivity {
  private SettingsTabs tabs;
  private LinearLayout appList, updateList;
  private MaterialTextView summary, lastCheck, activityMessage, empty;
  private LinearProgressIndicator progress;
  private MaterialButton check, batch, installAll;
  private String search = "";
  private int filter;
  private boolean built;
  private String renderedKey = "";
  private final Map<String, android.graphics.drawable.Drawable> icons = new HashMap<>();

  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    if (saved != null) {
      search = saved.getString("search", "");
      filter = saved.getInt("filter");
    }
    LinearLayout root = Ui.root(this, getString(R.string.app_name), false);
    var toolbar = (com.google.android.material.appbar.MaterialToolbar) root.getChildAt(0);
    toolbar
        .getMenu()
        .add(R.string.language)
        .setOnMenuItemClickListener(
            item -> {
              language();
              return true;
            });
    tabs = new SettingsTabs(this, root, saved);
    buildApps(tabs.page(R.string.apps_tab));
    buildUpdates(tabs.page(R.string.updates_tab));
    buildSettings(tabs.page(R.string.settings_tab));
    built = true;
    tabs.restore();
    refreshView();
    if (state.preferences.getBoolean("auto_check", true)) state.refresh(false);
  }

  private void buildApps(LinearLayout page) {
    LinearLayout top = Ui.card(this, page, true);
    summary = Ui.text(this, "", true);
    top.addView(summary);
    lastCheck = Ui.text(this, "", false);
    top.addView(lastCheck);
    check = Ui.button(this, R.string.check_updates, true);
    check.setOnClickListener(v -> state.refresh(true));
    top.addView(check, new LinearLayout.LayoutParams(-1, -2));
    progress = new LinearProgressIndicator(this);
    progress.setIndeterminate(true);
    top.addView(progress, new LinearLayout.LayoutParams(-1, Ui.dp(this, 4)));
    activityMessage = Ui.text(this, "", false);
    top.addView(activityMessage);
    TextInputLayout field = new TextInputLayout(this);
    field.setHint(R.string.search_apps);
    field.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
    TextInputEditText input = new TextInputEditText(field.getContext());
    input.setSingleLine(true);
    input.setText(search);
    field.addView(input, new LinearLayout.LayoutParams(-1, -2));
    page.addView(field, new LinearLayout.LayoutParams(-1, -2));
    input.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          public void onTextChanged(CharSequence s, int start, int before, int count) {
            search = s.toString();
            renderLists();
          }

          public void afterTextChanged(Editable s) {}
        });
    ChipGroup chips = new ChipGroup(this);
    chips.setSingleSelection(true);
    chips.setSelectionRequired(true);
    int[] names = {R.string.all_apps, R.string.installed_filter, R.string.available_filter};
    for (int i = 0; i < names.length; i++) {
      final int which = i;
      Chip chip = new Chip(this);
      chip.setId(View.generateViewId());
      chip.setText(names[i]);
      chip.setCheckable(true);
      chip.setMinHeight(Ui.dp(this, 48));
      chips.addView(chip);
      chip.setChecked(filter == i);
      chip.setOnCheckedChangeListener(
          (b, on) -> {
            if (on) {
              filter = which;
              renderLists();
            }
          });
    }
    page.addView(chips, new LinearLayout.LayoutParams(-1, -2));
    appList = Ui.vertical(this);
    page.addView(appList, new LinearLayout.LayoutParams(-1, -2));
    empty = Ui.text(this, getString(R.string.no_results), false);
    page.addView(empty);
  }

  private void buildUpdates(LinearLayout page) {
    LinearLayout intro = Ui.card(this, page, true);
    intro.addView(Ui.text(this, getString(R.string.updates_tab), true));
    intro.addView(Ui.text(this, getString(R.string.batch_note), false));
    batch = Ui.button(this, R.string.download_all, true);
    batch.setOnClickListener(
        v -> {
          List<String> repos = new ArrayList<>();
          for (AppEntry app : state.apps)
            if (state.update(app) && ReadyStore.read(this, app) == null) repos.add(app.repo());
          download(repos, false);
        });
    intro.addView(batch, new LinearLayout.LayoutParams(-1, -2));
    installAll = Ui.button(this, R.string.install_all, false);
    installAll.setOnClickListener(
        v -> {
          List<String> repos = new ArrayList<>();
          for (AppEntry app : state.apps)
            if (ReadyStore.read(this, app) != null) repos.add(app.repo());
          installPrepared(repos);
        });
    intro.addView(installAll, new LinearLayout.LayoutParams(-1, -2));
    updateList = Ui.vertical(this);
    page.addView(updateList, new LinearLayout.LayoutParams(-1, -2));
  }

  private void buildSettings(LinearLayout page) {
    LinearLayout channels = Ui.card(this, page, false);
    toggle(channels, R.string.previews, R.string.previews_note, "previews", true);
    toggle(channels, R.string.auto_check, R.string.auto_check_note, "auto_check", true);
    LinearLayout sources = Ui.card(this, page, false);
    sources.addView(Ui.text(this, getString(R.string.network_title), true));
    sources.addView(Ui.text(this, getString(R.string.network_note), false));
    sources.addView(Ui.text(this, getString(R.string.permissions_note), false));
    LinearLayout about = Ui.card(this, page, false);
    about.addView(Ui.text(this, getString(R.string.about), true));
    about.addView(
        Ui.text(this, getString(R.string.version_label, BuildConfig.VERSION_NAME), false));
    about.addView(Ui.text(this, getString(R.string.privacy), false));
    MaterialButton licenses = Ui.button(this, R.string.legal_title, false);
    licenses.setOnClickListener(v -> startActivity(new Intent(this, LicensesActivity.class)));
    about.addView(licenses, new LinearLayout.LayoutParams(-1, -2));
    MaterialButton github = Ui.button(this, R.string.source, false);
    github.setOnClickListener(
        v -> Ui.browse(this, "https://github.com/OneUI-Mods/OneUIModsManager"));
    about.addView(github, new LinearLayout.LayoutParams(-1, -2));
  }

  private void toggle(LinearLayout parent, int title, int note, String key, boolean fallback) {
    MaterialSwitch toggle = new MaterialSwitch(this);
    toggle.setText(title);
    toggle.setMinHeight(Ui.dp(this, 56));
    toggle.setChecked(state.preferences.getBoolean(key, fallback));
    parent.addView(toggle, new LinearLayout.LayoutParams(-1, -2));
    parent.addView(Ui.text(this, getString(note), false));
    toggle.setOnCheckedChangeListener(
        (b, on) -> {
          state.preferences.edit().putBoolean(key, on).apply();
          state.notifyChanged();
        });
  }

  protected void refreshView() {
    if (!built) return;
    int installed = 0, updates = 0, ready = 0;
    for (AppEntry app : state.apps) {
      if (InstalledApp.read(this, app) != null) installed++;
      if (state.update(app)) updates++;
      if (ReadyStore.read(this, app) != null) ready++;
    }
    summary.setText(getString(R.string.overview, installed, updates));
    long checked = state.preferences.getLong("last_check", 0);
    lastCheck.setText(
        checked == 0
            ? getString(R.string.not_checked)
            : getString(
                R.string.last_check,
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(new Date(checked))));
    check.setEnabled(!state.checking);
    progress.setVisibility(state.checking || state.downloading ? View.VISIBLE : View.GONE);
    String message = "";
    if (state.checking) message = getString(R.string.checking, state.completed, state.apps.size());
    else if (state.downloading) {
      AppEntry app = state.find(state.downloadRepo);
      message =
          getString(
              R.string.download_progress,
              app == null ? getString(R.string.preparing) : app.title(),
              state.downloadPercent);
    } else if (state.retryAt > System.currentTimeMillis())
      message =
          getString(
              R.string.limits,
              DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(state.retryAt)));
    else if (!state.downloadError.isEmpty()) message = Ui.error(this, state.downloadError);
    else if (!state.errors.isEmpty()) message = getString(R.string.cached);
    activityMessage.setText(message);
    activityMessage.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
    batch.setEnabled(!state.downloading && updates > 0);
    installAll.setVisibility(ready > 0 ? View.VISIBLE : View.GONE);
    renderLists();
  }

  private void renderLists() {
    if (appList == null || updateList == null) return;
    StringBuilder key =
        new StringBuilder(search)
            .append(filter)
            .append(state.previews())
            .append(state.downloading)
            .append(state.downloadRepo);
    for (AppEntry item : state.apps)
      key.append(item.repo())
          .append(AppStatus.label(this, state, item))
          .append(InstalledApp.read(this, item));
    if (key.toString().equals(renderedKey)) return;
    renderedKey = key.toString();
    appList.removeAllViews();
    updateList.removeAllViews();
    int shown = 0, updating = 0;
    String needle = search.strip().toLowerCase(Locale.ROOT);
    for (AppEntry app : state.apps) {
      boolean installed = InstalledApp.read(this, app) != null;
      boolean visible =
          (filter == 0 || filter == 1 && installed || filter == 2 && !installed)
              && (app.title() + " " + app.description() + " " + app.packageName())
                  .toLowerCase(Locale.ROOT)
                  .contains(needle);
      if (visible) {
        row(appList, app);
        shown++;
      }
      if (state.update(app) || ReadyStore.read(this, app) != null) {
        row(updateList, app);
        updating++;
      }
    }
    empty.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
    if (updating == 0) updateList.addView(Ui.text(this, getString(R.string.no_updates), false));
  }

  private void row(LinearLayout parent, AppEntry app) {
    LinearLayout body = Ui.card(this, parent, false);
    LinearLayout header = new LinearLayout(this);
    header.setGravity(Gravity.CENTER_VERTICAL);
    ImageView icon = new ImageView(this);
    android.graphics.drawable.Drawable picture = icons.get(app.repo());
    if (picture == null) {
      try {
        picture = getPackageManager().getApplicationIcon(app.packageName());
      } catch (android.content.pm.PackageManager.NameNotFoundException missing) {
        picture = getDrawable(R.drawable.ic_manager_mono);
      }
      icons.put(app.repo(), picture);
    }
    icon.setImageDrawable(picture);
    header.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
    LinearLayout title = Ui.vertical(this);
    title.setPadding(Ui.dp(this, 16), 0, 0, 0);
    header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    title.addView(Ui.text(this, app.title(), true));
    InstalledApp installed = InstalledApp.read(this, app);
    title.addView(
        Ui.text(
            this,
            installed == null
                ? getString(R.string.not_installed)
                : getString(R.string.installed_version, installed.name()),
            false));
    body.addView(header, new LinearLayout.LayoutParams(-1, -2));
    MaterialTextView caption = Ui.text(this, AppStatus.label(this, state, app), false);
    caption.setPadding(0, Ui.dp(this, 12), 0, 0);
    body.addView(caption);
    View card = (View) body.getParent();
    card.setOnClickListener(
        v -> startActivity(new Intent(this, AppDetailActivity.class).putExtra("repo", app.repo())));
    card.setFocusable(true);
  }

  private void language() {
    new MaterialAlertDialogBuilder(this)
        .setTitle(R.string.language)
        .setItems(
            new String[] {"System / システム", "日本語", "English"},
            (d, which) ->
                getSystemService(LocaleManager.class)
                    .setApplicationLocales(
                        LocaleList.forLanguageTags(which == 1 ? "ja" : which == 2 ? "en" : "")))
        .show();
  }

  protected void onSaveInstanceState(Bundle out) {
    out.putString("search", search);
    out.putInt("filter", filter);
    tabs.save(out);
    super.onSaveInstanceState(out);
  }
}
