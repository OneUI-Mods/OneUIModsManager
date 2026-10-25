package dev.ryuya.oneuimods;

import android.content.Context;
import android.content.pm.*;
import android.os.Build;
import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;

final class ApkVerifier {
  record Verified(long code, String version, String sha256) {}

  static final class Rejected extends IOException {
    final String reason;

    Rejected(String reason) {
      super(reason);
      this.reason = reason;
    }
  }

  static String hash(File file) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (InputStream in = Files.newInputStream(file.toPath())) {
      byte[] buffer = new byte[65536];
      int count;
      while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  static Verified verify(Context c, AppEntry app, File file, String expected, long expectedCode)
      throws Exception {
    String digest = hash(file);
    if (expected == null
        || !expected.matches("[a-fA-F0-9]{64}")
        || !digest.equalsIgnoreCase(expected)) throw new Rejected("hash");
    PackageManager pm = c.getPackageManager();
    PackageInfo apk =
        pm.getPackageArchiveInfo(file.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
    if (apk == null || !app.packageName().equals(apk.packageName) || apk.applicationInfo == null)
      throw new Rejected("package");
    if ((apk.applicationInfo.flags
            & (ApplicationInfo.FLAG_DEBUGGABLE | ApplicationInfo.FLAG_TEST_ONLY))
        != 0) throw new Rejected("debug");
    if (apk.applicationInfo.minSdkVersion > Build.VERSION.SDK_INT) throw new Rejected("android");
    if (apk.signingInfo == null || apk.signingInfo.hasMultipleSigners())
      throw new Rejected("signature");
    Signature[] current = apk.signingInfo.getApkContentsSigners();
    if (current == null || current.length != 1) throw new Rejected("signature");
    Signature[] history = apk.signingInfo.getSigningCertificateHistory();
    if (history == null) history = current;
    boolean trusted = false;
    for (Signature cert : history) {
      String pin =
          HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(cert.toByteArray()));
      trusted |= app.certificates().contains(pin);
    }
    if (!trusted) throw new Rejected("signature");
    InstalledApp installed = InstalledApp.fresh(c, app);
    if (installed != null) {
      if (apk.getLongVersionCode() <= installed.code()) throw new Rejected("not_newer");
      boolean same = false;
      for (Signature cert : history)
        same |=
            pm.hasSigningCertificate(
                app.packageName(), cert.toByteArray(), PackageManager.CERT_INPUT_RAW_X509);
      if (!same) throw new Rejected("signature");
    }
    if (expectedCode > 0 && expectedCode != apk.getLongVersionCode()) throw new Rejected("version");
    return new Verified(apk.getLongVersionCode(), apk.versionName, digest);
  }

  private ApkVerifier() {}
}
