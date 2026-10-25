package dev.ryuya.oneuimods;

import java.math.BigInteger;
import java.util.*;
import java.util.regex.*;

/** Numeric components precede prerelease identifiers; no lexicographic 1.10 < 1.9 mistake. */
final class VersionOrder {
  private static final Pattern FORMAT =
      Pattern.compile("^[vV]?(\\d+(?:\\.\\d+)*)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$");

  static OptionalInt compare(String a, String b) {
    Matcher left = FORMAT.matcher(a), right = FORMAT.matcher(b);
    if (!left.matches() || !right.matches()) return OptionalInt.empty();
    String[] x = left.group(1).split("\\."), y = right.group(1).split("\\.");
    for (int i = 0; i < Math.max(x.length, y.length); i++) {
      int c =
          new BigInteger(i < x.length ? x[i] : "0")
              .compareTo(new BigInteger(i < y.length ? y[i] : "0"));
      if (c != 0) return OptionalInt.of(c);
    }
    String p = left.group(2), q = right.group(2);
    if (p == null || q == null) return OptionalInt.of(p == q ? 0 : p == null ? 1 : -1);
    String[] ps = p.split("\\."), qs = q.split("\\.");
    for (int i = 0; i < Math.min(ps.length, qs.length); i++) {
      boolean pn = ps[i].matches("[0-9]+"), qn = qs[i].matches("[0-9]+");
      int c =
          pn && qn
              ? new BigInteger(ps[i]).compareTo(new BigInteger(qs[i]))
              : pn != qn ? (pn ? -1 : 1) : ps[i].compareTo(qs[i]);
      if (c != 0) return OptionalInt.of(c);
    }
    return OptionalInt.of(Integer.compare(ps.length, qs.length));
  }

  private VersionOrder() {}
}
