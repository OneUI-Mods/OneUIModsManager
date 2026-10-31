package dev.ryuya.oneuimods

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.view.View
import android.view.View.OnClickListener
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.Toast
import androidx.core.widget.NestedScrollView
import com.google.android.material.textview.MaterialTextView
import java.util.List
import kotlin.math.max

class AppDetailActivity : InstallerActivity() {
    private var app: AppEntry? = null
    private var body: LinearLayout? = null
    private var scroll: NestedScrollView? = null
    private var savedScroll: Int = 0
    private var renderedKey: String = ""
    private var downloadProgress: MaterialTextView? = null

    public override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        app = state!!.find(getIntent().getStringExtra("repo"))
        if (app == null) {
            finish()
            return
        }
        val root = Ui.root(this, app!!.title, true)
        scroll = NestedScrollView(this)
        body = Ui.vertical(this)
        body!!.setPadding(Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 24))
        scroll!!.addView(body!!)
        root.addView(scroll, LayoutParams(-1, 0, 1f))
        savedScroll = if (saved == null) 0 else saved.getInt("scroll")
        refreshView()
    }

    override fun refreshView() {
        if (body == null) return
        val currentRelease = state!!.release(app!!)
        val key: String =
            (AppStatus.label(this, state!!, app!!) +
                InstalledApp.read(this, app!!) +
                (if (currentRelease == null) "" else currentRelease.tag) +
                state!!.downloading +
                state!!.downloadError)
        if (key == renderedKey) {
            if (downloadProgress != null)
                downloadProgress!!.setText(
                    getString(R.string.download_progress, app!!.title, state!!.downloadPercent)
                )
            return
        }
        renderedKey = key
        downloadProgress = null
        val y = max(savedScroll, scroll!!.getScrollY())
        savedScroll = 0
        body!!.removeAllViews()
        val intro = Ui.card(this, body!!, true)
        intro.addView(Ui.text(this, app!!.title, true))
        intro.addView(Ui.text(this, app!!.description, false))
        intro.addView(Ui.text(this, AppStatus.label(this, state!!, app!!), true))
        val installed = InstalledApp.read(this, app!!)
        val release = state!!.release(app!!)
        val ready = ReadyStore.read(this, app!!)
        if (installed != null)
            intro.addView(
                Ui.text(this, getString(R.string.installed_version, installed.name), false)
            )
        if (release != null) {
            intro.addView(
                Ui.text(this, getString(R.string.release_version, release.versionName), false)
            )
            intro.addView(
                Ui.text(
                    this,
                    getString(
                        if (release.preview) R.string.preview_badge else R.string.stable_badge
                    ),
                    false,
                )
            )
        }
        if (ready != null) {
            intro.addView(Ui.text(this, getString(R.string.verified), false))
            val install = Ui.button(this, R.string.install, true)
            install!!.setOnClickListener(OnClickListener { v: View? -> install(app!!.repo!!) })
            intro.addView(install, LayoutParams(-1, -2))
        } else if (release != null && (installed == null || state!!.update(app!!))) {
            val download =
                Ui.button(this, if (installed == null) R.string.download else R.string.update, true)
            download!!.setEnabled(!state!!.downloading)
            download!!.setOnClickListener(
                OnClickListener { v: View? -> download(List.of(app!!.repo), true) }
            )
            intro.addView(download, LayoutParams(-1, -2))
        }
        if (state!!.downloading && state!!.downloadRepo == app!!.repo) {
            downloadProgress =
                Ui.text(
                    this,
                    getString(R.string.download_progress, app!!.title, state!!.downloadPercent),
                    false,
                )
            intro.addView(downloadProgress)
        }
        if (!state!!.downloadError.isEmpty())
            intro.addView(Ui.text(this, Ui.error(this, state!!.downloadError), false))
        if (installed != null) {
            val open = Ui.button(this, R.string.open_app, false)
            open!!.setOnClickListener(
                OnClickListener { v: View? ->
                    val i = getPackageManager().getLaunchIntentForPackage(app!!.packageName!!)
                    if (i != null) startActivity(i)
                    else Toast.makeText(this, R.string.app_missing, Toast.LENGTH_SHORT).show()
                }
            )
            intro.addView(open, LayoutParams(-1, -2))
        }
        val notes = Ui.card(this, body!!, false)
        notes.addView(Ui.text(this, getString(R.string.whats_new), true))
        val text =
            Ui.text(
                this,
                renderNotes(if (release == null) getString(R.string.no_notes) else release.notes),
                false,
            )
        text.setTextIsSelectable(true)
        notes.addView(text)
        notes.addView(Ui.text(this, getString(R.string.reload_note), false))
        val github = Ui.button(this, R.string.release_page, false)
        github!!.setOnClickListener(
            OnClickListener { v: View? ->
                Ui.browse(
                    this,
                    (if (release == null) app!!.repositoryUrl() + "/releases"
                    else release.pageUrl)!!,
                )
            }
        )
        notes.addView(github, LayoutParams(-1, -2))
        if (installed != null) {
            val info = Ui.button(this, R.string.app_info, false)
            info!!.setOnClickListener(
                OnClickListener { v: View? ->
                    startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + app!!.packageName),
                        )
                    )
                }
            )
            body!!.addView(info, LayoutParams(-1, -2))
        }
        scroll!!.post(Runnable { scroll!!.scrollTo(0, y) })
    }

    /*
     * Small, non-executable Markdown subset for headings, bullets and emphasis. Full notes stay
     * linked.
     */
    private fun renderNotes(input: String?): CharSequence? {
        if (input == null || TextCompatibility.isBlank(input)) return getString(R.string.no_notes)
        val out = SpannableStringBuilder()
        for (line in TextCompatibility.split(input, "\\R")) {
            val heading = line.startsWith("#")
            val clean =
                line
                    .replaceFirst("^#{1,6}\\s+".toRegex(), "")
                    .replaceFirst("^[-*] ".toRegex(), "• ")
                    .replace("**", "")
                    .replace("`", "")
            val start = out.length
            out.append(clean).append("\n")
            if (heading)
                out.setSpan(
                    StyleSpan(Typeface.BOLD),
                    start,
                    out.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
        }
        return out
    }

    override fun onSaveInstanceState(out: Bundle) {
        if (scroll != null) out.putInt("scroll", scroll!!.getScrollY())
        super.onSaveInstanceState(out)
    }
}
