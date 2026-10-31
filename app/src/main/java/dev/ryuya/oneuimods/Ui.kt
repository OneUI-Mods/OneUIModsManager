package dev.ryuya.oneuimods

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.View.OnClickListener
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.textview.MaterialTextView
import kotlin.jvm.JvmStatic
import kotlin.math.max
import kotlin.math.round

object Ui {
    @JvmStatic
    fun dp(c: Context, value: Int): Int {
        return Math.round(value * c.getResources().getDisplayMetrics().density)
    }

    @JvmStatic
    fun vertical(c: Context?): LinearLayout {
        val v = LinearLayout(c)
        v.setOrientation(LinearLayout.VERTICAL)
        return v
    }

    @JvmStatic
    fun root(c: AppCompatActivity, title: String?, back: Boolean): LinearLayout {
        WindowCompat.setDecorFitsSystemWindows(c.getWindow(), false)
        val root = Ui.vertical(c)
        c.setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(
            root,
            OnApplyWindowInsetsListener { v: View?, insets: WindowInsetsCompat? ->
                val b = insets!!.getInsets(Type.systemBars() or Type.displayCutout())
                val bottom = max(b.bottom, insets!!.getInsets(Type.ime()).bottom)
                v!!.setPadding(b.left, b.top, b.right, bottom)
                insets!!
            },
        )
        val toolbar = MaterialToolbar(c)
        toolbar.setTitle(title)
        if (back) {
            toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            toolbar.setNavigationContentDescription(R.string.back)
            toolbar.setNavigationOnClickListener(OnClickListener { v: View? -> c.finish() })
        }
        root.addView(toolbar, LayoutParams(-1, -2))
        ViewCompat.requestApplyInsets(root)
        return root
    }

    @JvmStatic
    fun card(c: Context, parent: LinearLayout, primary: Boolean): LinearLayout {
        val card = MaterialCardView(c)
        card.setRadius(Ui.dp(c, 24).toFloat())
        card.setStrokeWidth(0)
        card.setCardElevation(0f)
        card.setCardBackgroundColor(
            MaterialColors.getColor(
                c,
                if (primary) com.google.android.material.R.attr.colorPrimaryContainer
                else com.google.android.material.R.attr.colorSurfaceContainerLow,
                "OneUIMods",
            )
        )
        val body = Ui.vertical(c)
        body.setPadding(Ui.dp(c, 20), Ui.dp(c, 16), Ui.dp(c, 20), Ui.dp(c, 16))
        card.addView(body)
        val p = LayoutParams(-1, -2)
        p.bottomMargin = Ui.dp(c, 14)
        parent.addView(card, p)
        return body
    }

    @JvmStatic
    fun text(c: Context, text: CharSequence?, title: Boolean): MaterialTextView {
        val v = MaterialTextView(c)
        v.setText(text)
        v.setTextAppearance(
            if (title) com.google.android.material.R.style.TextAppearance_Material3_TitleMedium
            else com.google.android.material.R.style.TextAppearance_Material3_BodyMedium
        )
        v.setLineSpacing(Ui.dp(c, 3).toFloat(), 1f)
        v.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 4))
        return v
    }

    @JvmStatic
    fun button(c: Context, text: Int, primary: Boolean): MaterialButton? {
        val v: MaterialButton? =
            if (primary) MaterialButton(c)
            else
                MaterialButton(
                    c,
                    null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle,
                )
        v!!.setText(text)
        v!!.setMinHeight(Ui.dp(c, 48))
        return v
    }

    @JvmStatic
    fun error(c: Context, code: String?): String {
        val id: Int =
            when (code) {
                "hash" -> R.string.error_hash
                "signature" -> R.string.error_signature
                "package" -> R.string.error_package
                "version" -> R.string.error_version
                "not_newer" -> R.string.error_not_newer
                "android" -> R.string.error_android
                "missing_hash" -> R.string.error_missing_hash
                "debug" -> R.string.error_debug
                "canceled" -> R.string.error_canceled
                else -> R.string.error_network
            }
        return c.getString(id)
    }

    @JvmStatic
    fun browse(c: Context, url: String) {
        if (!url.startsWith("https://github.com/OneUI-Mods/")) return
        try {
            c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (missing: ActivityNotFoundException) {
            Toast.makeText(c, R.string.app_missing, Toast.LENGTH_SHORT).show()
        }
    }
}
