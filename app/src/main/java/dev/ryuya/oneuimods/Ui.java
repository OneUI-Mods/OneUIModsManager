package dev.ryuya.oneuimods;

import android.content.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.*;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textview.MaterialTextView;

final class Ui {
  static int dp(Context c, int value) {
    return Math.round(value * c.getResources().getDisplayMetrics().density);
  }

  static LinearLayout vertical(Context c) {
    var v = new LinearLayout(c);
    v.setOrientation(LinearLayout.VERTICAL);
    return v;
  }

  static LinearLayout root(AppCompatActivity c, String title, boolean back) {
    WindowCompat.setDecorFitsSystemWindows(c.getWindow(), false);
    LinearLayout root = vertical(c);
    c.setContentView(root);
    ViewCompat.setOnApplyWindowInsetsListener(
        root,
        (v, insets) -> {
          var b =
              insets.getInsets(
                  WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
          int bottom = Math.max(b.bottom, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom);
          v.setPadding(b.left, b.top, b.right, bottom);
          return insets;
        });
    MaterialToolbar toolbar = new MaterialToolbar(c);
    toolbar.setTitle(title);
    if (back) {
      toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
      toolbar.setNavigationContentDescription(R.string.back);
      toolbar.setNavigationOnClickListener(v -> c.finish());
    }
    root.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
    ViewCompat.requestApplyInsets(root);
    return root;
  }

  static LinearLayout card(Context c, LinearLayout parent, boolean primary) {
    MaterialCardView card = new MaterialCardView(c);
    card.setRadius(dp(c, 24));
    card.setStrokeWidth(0);
    card.setCardElevation(0);
    card.setCardBackgroundColor(
        MaterialColors.getColor(
            c,
            primary
                ? com.google.android.material.R.attr.colorPrimaryContainer
                : com.google.android.material.R.attr.colorSurfaceContainerLow,
            "OneUIMods"));
    LinearLayout body = vertical(c);
    body.setPadding(dp(c, 20), dp(c, 16), dp(c, 20), dp(c, 16));
    card.addView(body);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.bottomMargin = dp(c, 14);
    parent.addView(card, p);
    return body;
  }

  static MaterialTextView text(Context c, CharSequence text, boolean title) {
    MaterialTextView v = new MaterialTextView(c);
    v.setText(text);
    v.setTextAppearance(
        title
            ? com.google.android.material.R.style.TextAppearance_Material3_TitleMedium
            : com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
    v.setLineSpacing(dp(c, 3), 1);
    v.setPadding(0, dp(c, 4), 0, dp(c, 4));
    return v;
  }

  static MaterialButton button(Context c, int text, boolean primary) {
    MaterialButton v =
        primary
            ? new MaterialButton(c)
            : new MaterialButton(
                c, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
    v.setText(text);
    v.setMinHeight(dp(c, 48));
    return v;
  }

  static String error(Context c, String code) {
    int id =
        switch (code) {
          case "hash" -> R.string.error_hash;
          case "signature" -> R.string.error_signature;
          case "package" -> R.string.error_package;
          case "version" -> R.string.error_version;
          case "not_newer" -> R.string.error_not_newer;
          case "android" -> R.string.error_android;
          case "missing_hash" -> R.string.error_missing_hash;
          case "debug" -> R.string.error_debug;
          case "canceled" -> R.string.error_canceled;
          default -> R.string.error_network;
        };
    return c.getString(id);
  }

  static void browse(Context c, String url) {
    if (!url.startsWith("https://github.com/OneUI-Mods/")) return;
    try {
      c.startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
    } catch (ActivityNotFoundException missing) {
      Toast.makeText(c, R.string.app_missing, Toast.LENGTH_SHORT).show();
    }
  }

  private Ui() {}
}
