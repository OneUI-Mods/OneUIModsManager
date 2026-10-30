package dev.ryuya.oneuimods

import java.math.BigInteger
import java.util.OptionalInt
import java.util.regex.Pattern
import kotlin.jvm.JvmStatic
import kotlin.math.max
import kotlin.math.min

/* Numeric components precede prerelease identifiers; no lexicographic 1.10 < 1.9 mistake. */
object VersionOrder {
    private val FORMAT: Pattern? =
        Pattern.compile("^[vV]?(\\d+(?:\\.\\d+)*)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$")

    @JvmStatic
    fun compare(a: String?, b: String?): OptionalInt? {
        val left = VersionOrder.FORMAT!!.matcher(a)
        val right = VersionOrder.FORMAT!!.matcher(b)
        if (!left.matches() || !right.matches()) return OptionalInt.empty()
        val x = left.group(1).split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val y = right.group(1).split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (i in 0 until max(x.size, y.size)) {
            val c =
                BigInteger(if (i < x.size) x[i] else "0")
                    .compareTo(BigInteger(if (i < y.size) y[i] else "0"))
            if (c != 0) return OptionalInt.of(c)
        }
        val p = left.group(2)
        val q = right.group(2)
        if (p == null || q == null)
            return OptionalInt.of(if (p === q) 0 else if (p == null) 1 else -1)
        val ps = p.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val qs = q.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (i in 0 until min(ps.size, qs.size)) {
            val pn = ps[i].matches("[0-9]+".toRegex())
            val qn = qs[i].matches("[0-9]+".toRegex())
            val c: Int =
                if (pn && qn) BigInteger(ps[i]).compareTo(BigInteger(qs[i]))
                else if (pn != qn) (if (pn) -1 else 1) else ps[i].compareTo(qs[i])
            if (c != 0) return OptionalInt.of(c)
        }
        return OptionalInt.of(Integer.compare(ps.size, qs.size))
    }
}
