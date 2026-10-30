package dev.ryuya.oneuimods

import android.app.LocaleManager
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager.NameNotFoundException
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.LocaleList
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MenuItem
import android.view.MenuItem.OnMenuItemClickListener
import android.view.View
import android.widget.CompoundButton
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView
import java.text.DateFormat
import java.util.ArrayList
import java.util.Date
import java.util.HashMap

/* A searchable catalog, a focused update queue, and user-controlled release channels. */
class MainActivity : InstallerActivity() {
    private var tabs: SettingsTabs? = null
    private var appList: LinearLayout? = null
    private var updateList: LinearLayout? = null
    private var summary: MaterialTextView? = null
    private var lastCheck: MaterialTextView? = null
    private var activityMessage: MaterialTextView? = null
    private var empty: MaterialTextView? = null
    private var progress: LinearProgressIndicator? = null
    private var check: MaterialButton? = null
    private var batch: MaterialButton? = null
    private var installAll: MaterialButton? = null
    private var search: String = ""
    private var filter: Int = 0
    private var built: Boolean = false
    private var renderedKey: String = ""
    private val icons: MutableMap<String?, Drawable?> = HashMap<String?, Drawable?>()

    public override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        if (saved != null) {
            search = saved.getString("search", "")
            filter = saved.getInt("filter")
        }
        val root = Ui.root(this, getString(R.string.app_name), false)
        val toolbar: MaterialToolbar? = root.getChildAt(0) as MaterialToolbar?
        toolbar!!
            .getMenu()
            .add(R.string.language)
            .setOnMenuItemClickListener(
                OnMenuItemClickListener { item: MenuItem? ->
                    language()
                    true
                }
            )
        tabs = SettingsTabs(this, root, saved)
        buildApps(tabs!!.page(R.string.apps_tab))
        buildUpdates(tabs!!.page(R.string.updates_tab))
        buildSettings(tabs!!.page(R.string.settings_tab))
        built = true
        tabs!!.restore()
        refreshView()
        if (state!!.preferences.getBoolean("auto_check", true)) state!!.refresh(false)
    }

    private fun buildApps(page: LinearLayout) {
        val top = Ui.card(this, page, true)
        summary = Ui.text(this, "", true)
        top.addView(summary)
        lastCheck = Ui.text(this, "", false)
        top.addView(lastCheck)
        check = Ui.button(this, R.string.check_updates, true)
        check!!.setOnClickListener(
            android.view.View.OnClickListener { v: View? -> state!!.refresh(true) }
        )
        top.addView(check, LayoutParams(-1, -2))
        progress = LinearProgressIndicator(this)
        progress!!.setIndeterminate(true)
        top.addView(progress, LayoutParams(-1, Ui.dp(this, 4)))
        activityMessage = Ui.text(this, "", false)
        top.addView(activityMessage)
        val field = TextInputLayout(this)
        field.setHint(R.string.search_apps)
        field.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE)
        val input = TextInputEditText(field.getContext())
        input.setSingleLine(true)
        input.setText(search)
        field.addView(input, LayoutParams(-1, -2))
        page.addView(field, LayoutParams(-1, -2))
        input.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) {}

                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    search = s.toString()
                    renderLists()
                }

                override fun afterTextChanged(s: Editable?) {}
            }
        )
        val chips = ChipGroup(this)
        chips.setSingleSelection(true)
        chips.setSelectionRequired(true)
        val names =
            intArrayOf(R.string.all_apps, R.string.installed_filter, R.string.available_filter)
        for (i in names.indices) {
            val which: Int = i
            val chip = Chip(this)
            chip.setId(View.generateViewId())
            chip.setText(names[i])
            chip.setCheckable(true)
            chip.setMinHeight(Ui.dp(this, 48))
            chips.addView(chip)
            chip.setChecked(filter == i)
            chip.setOnCheckedChangeListener(
                OnCheckedChangeListener { b: CompoundButton?, on: Boolean ->
                    if (on) {
                        filter = which
                        renderLists()
                    }
                }
            )
        }
        page.addView(chips, LayoutParams(-1, -2))
        appList = Ui.vertical(this)
        page.addView(appList, LayoutParams(-1, -2))
        empty = Ui.text(this, getString(R.string.no_results), false)
        page.addView(empty)
    }

    private fun buildUpdates(page: LinearLayout) {
        val intro = Ui.card(this, page, true)
        intro.addView(Ui.text(this, getString(R.string.updates_tab), true))
        intro.addView(Ui.text(this, getString(R.string.batch_note), false))
        batch = Ui.button(this, R.string.download_all, true)
        batch!!.setOnClickListener(
            android.view.View.OnClickListener { v: View? ->
                val repos = ArrayList<String?>()
                for (app in state!!.apps!!) if (
                    state!!.update(app!!) && ReadyStore.read(this, app!!) == null
                )
                    repos.add(app.repo)
                download(repos, false)
            }
        )
        intro.addView(batch, LayoutParams(-1, -2))
        installAll = Ui.button(this, R.string.install_all, false)
        installAll!!.setOnClickListener(
            android.view.View.OnClickListener { v: View? ->
                val repos = ArrayList<String?>()
                for (app in state!!.apps!!) if (ReadyStore.read(this, app!!) != null)
                    repos.add(app.repo)
                installPrepared(repos)
            }
        )
        intro.addView(installAll, LayoutParams(-1, -2))
        updateList = Ui.vertical(this)
        page.addView(updateList, LayoutParams(-1, -2))
    }

    private fun buildSettings(page: LinearLayout?) {
        val channels = Ui.card(this, page!!, false)
        toggle(channels, R.string.previews, R.string.previews_note, "previews", true)
        toggle(channels, R.string.auto_check, R.string.auto_check_note, "auto_check", true)
        val sources = Ui.card(this, page!!, false)
        sources.addView(Ui.text(this, getString(R.string.network_title), true))
        sources.addView(Ui.text(this, getString(R.string.network_note), false))
        sources.addView(Ui.text(this, getString(R.string.permissions_note), false))
        val about = Ui.card(this, page!!, false)
        about.addView(Ui.text(this, getString(R.string.about), true))
        about.addView(
            Ui.text(this, getString(R.string.version_label, BuildConfig.VERSION_NAME), false)
        )
        about.addView(Ui.text(this, getString(R.string.privacy), false))
        val licenses = Ui.button(this, R.string.legal_title, false)
        licenses!!.setOnClickListener(
            android.view.View.OnClickListener { v: View? ->
                startActivity(Intent(this, LicensesActivity::class.java))
            }
        )
        about.addView(licenses, LayoutParams(-1, -2))
        val github = Ui.button(this, R.string.source, false)
        github!!.setOnClickListener(
            android.view.View.OnClickListener { v: View? ->
                Ui.browse(this, "https://github.com/OneUI-Mods/OneUIModsManager")
            }
        )
        about.addView(github, LayoutParams(-1, -2))
    }

    private fun toggle(
        parent: LinearLayout,
        title: Int,
        note: Int,
        key: String?,
        fallback: Boolean,
    ) {
        val toggle = MaterialSwitch(this)
        toggle.setText(title)
        toggle.setMinHeight(Ui.dp(this, 56))
        toggle.setChecked(state!!.preferences.getBoolean(key, fallback))
        parent.addView(toggle, LayoutParams(-1, -2))
        parent.addView(Ui.text(this, getString(note), false))
        toggle.setOnCheckedChangeListener(
            OnCheckedChangeListener { b: CompoundButton?, on: Boolean ->
                state!!.preferences.edit().putBoolean(key, on).apply()
                state!!.notifyChanged()
            }
        )
    }

    override fun refreshView() {
        if (!built) return
        var installed: Int = 0
        var updates: Int = 0
        var ready: Int = 0
        for (app in state!!.apps!!) {
            if (InstalledApp.read(this, app!!) != null) installed++
            if (state!!.update(app!!)) updates++
            if (ReadyStore.read(this, app!!) != null) ready++
        }
        summary!!.setText(getString(R.string.overview, installed, updates))
        val checked = state!!.preferences.getLong("last_check", 0)
        lastCheck!!.setText(
            if (checked == 0L) getString(R.string.not_checked)
            else
                getString(
                    R.string.last_check,
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(checked)),
                )
        )
        check!!.setEnabled(!state!!.checking)
        progress!!.setVisibility(
            if (state!!.checking || state!!.downloading) View.VISIBLE else View.GONE
        )
        var message: String = ""
        if (state!!.checking)
            message = getString(R.string.checking, state!!.completed, state!!.apps!!.size)
        else if (state!!.downloading) {
            val app = state!!.find(state!!.downloadRepo)
            message =
                getString(
                    R.string.download_progress,
                    if (app == null) getString(R.string.preparing) else app.title,
                    state!!.downloadPercent,
                )
        } else if (state!!.retryAt > System.currentTimeMillis())
            message =
                getString(
                    R.string.limits,
                    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state!!.retryAt)),
                )
        else if (!state!!.downloadError.isEmpty()) message = Ui.error(this, state!!.downloadError)
        else if (!state!!.errors.isEmpty()) message = getString(R.string.cached)
        activityMessage!!.setText(message)
        activityMessage!!.setVisibility(if (message.isEmpty()) View.GONE else View.VISIBLE)
        batch!!.setEnabled(!state!!.downloading && updates > 0)
        installAll!!.setVisibility(if (ready > 0) View.VISIBLE else View.GONE)
        renderLists()
    }

    private fun renderLists() {
        if (appList == null || updateList == null) return
        val key =
            StringBuilder(search)
                .append(filter)
                .append(state!!.previews())
                .append(state!!.downloading)
                .append(state!!.downloadRepo)
        for (item in state!!.apps!!) key.append(item!!.repo)
            .append(AppStatus.label(this, state!!, item!!))
            .append(InstalledApp.read(this, item!!))
        if (key.toString() == renderedKey) return
        renderedKey = key.toString()
        appList!!.removeAllViews()
        updateList!!.removeAllViews()
        var shown: Int = 0
        var updating: Int = 0
        val needle = search.trim().lowercase()
        for (app in state!!.apps!!) {
            val installed: Boolean = InstalledApp.read(this, app!!) != null
            val visible: Boolean =
                (filter == 0 || filter == 1 && installed || filter == 2 && !installed) &&
                    (app.title + " " + app.description + " " + app.packageName)
                        .lowercase()
                        .contains(needle)
            if (visible) {
                row(appList, app!!)
                shown++
            }
            if (state!!.update(app!!) || ReadyStore.read(this, app!!) != null) {
                row(updateList, app!!)
                updating++
            }
        }
        empty!!.setVisibility(if (shown == 0) View.VISIBLE else View.GONE)
        if (updating == 0)
            updateList!!.addView(Ui.text(this, getString(R.string.no_updates), false))
    }

    private fun row(parent: LinearLayout?, app: AppEntry) {
        val body = Ui.card(this, parent!!, false)
        val header = LinearLayout(this)
        header.setGravity(Gravity.CENTER_VERTICAL)
        val icon = ImageView(this)
        var picture = icons.get(app.repo)
        if (picture == null) {
            try {
                picture = getPackageManager().getApplicationIcon(app.packageName!!)
            } catch (missing: NameNotFoundException) {
                picture = getDrawable(R.drawable.ic_manager_mono)
            }
            icons.put(app.repo, picture)
        }
        icon.setImageDrawable(picture)
        header.addView(icon, LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)))
        val title = Ui.vertical(this)
        title.setPadding(Ui.dp(this, 16), 0, 0, 0)
        header.addView(title, LayoutParams(0, -2, 1f))
        title.addView(Ui.text(this, app.title, true))
        val installed = InstalledApp.read(this, app)
        title.addView(
            Ui.text(
                this,
                if (installed == null) getString(R.string.not_installed)
                else getString(R.string.installed_version, installed.name),
                false,
            )
        )
        body.addView(header, LayoutParams(-1, -2))
        val caption = Ui.text(this, AppStatus.label(this, state!!, app), false)
        caption.setPadding(0, Ui.dp(this, 12), 0, 0)
        body.addView(caption)
        val card: View? = body.getParent() as View?
        card!!.setOnClickListener(
            android.view.View.OnClickListener { v: View? ->
                startActivity(
                    Intent(this, AppDetailActivity::class.java).putExtra("repo", app.repo)
                )
            }
        )
        card!!.setFocusable(true)
    }

    private fun language() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.language)
            .setItems(
                arrayOf<String>("System / システム", "日本語", "English"),
                android.content.DialogInterface.OnClickListener { d: DialogInterface?, which: Int ->
                    getSystemService<LocaleManager?>(LocaleManager::class.java)
                        .setApplicationLocales(
                            LocaleList.forLanguageTags(
                                if (which == 1) "ja" else if (which == 2) "en" else ""
                            )
                        )
                },
            )
            .show()
    }

    override fun onSaveInstanceState(out: Bundle) {
        out.putString("search", search)
        out.putInt("filter", filter)
        tabs!!.save(out)
        super.onSaveInstanceState(out)
    }
}
