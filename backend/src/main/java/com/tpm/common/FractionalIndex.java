package com.tpm.common;

import java.util.ArrayList;
import java.util.List;

/**
 * Fractional indexing for ordering Kanban cards and columns (the idea behind Jira's LexoRank
 * and Figma's multiplayer ordering).
 *
 * <p>Each key is a base-62 fraction {@code 0.d1d2d3...} written as a string. Because keys are
 * compared byte-wise, a new key can always be generated <em>between</em> any two existing ones,
 * so moving a card is a single-row UPDATE instead of renumbering the whole column.
 *
 * <p>Invariant: keys never end with the zero digit, which guarantees that string order equals
 * numeric order and that a midpoint always exists.
 */
public final class FractionalIndex {

    public static final String DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = DIGITS.length();
    private static final char ZERO = DIGITS.charAt(0);

    private FractionalIndex() {}

    /**
     * Returns a key strictly between {@code lower} and {@code upper}.
     *
     * @param lower lower bound, {@code null} or empty for "start of list"
     * @param upper upper bound, {@code null} for "end of list"
     */
    public static String between(String lower, String upper) {
        var a = lower == null ? "" : lower;
        validate(a);
        if (upper != null) {
            validate(upper);
            if (upper.isEmpty() || a.compareTo(upper) >= 0) {
                throw new IllegalArgumentException("lower (%s) must be < upper (%s)".formatted(a, upper));
            }
        }
        return midpoint(a, upper);
    }

    /** Generates {@code n} evenly spaced keys, used for seeding and rebalancing a list. */
    public static List<String> evenlySpaced(int n) {
        if (n < 0) throw new IllegalArgumentException("n must be >= 0");
        var keys = new ArrayList<String>(n);
        if (n == 0) return keys;
        int width = 2;
        while (Math.pow(BASE, width) <= n + 1) width++;
        long space = (long) Math.pow(BASE, width);
        long step = space / (n + 1);
        for (int i = 1; i <= n; i++) {
            keys.add(stripTrailingZeros(encode(step * i, width)));
        }
        return keys;
    }

    private static String midpoint(String a, String b) {
        if (b != null) {
            // Skip the longest common prefix (treating a missing digit in `a` as zero).
            int n = 0;
            while (n < b.length() && charAt(a, n) == b.charAt(n)) n++;
            if (n > 0) {
                return b.substring(0, n) + midpoint(a.length() > n ? a.substring(n) : "", b.substring(n));
            }
        }
        int digitA = a.isEmpty() ? 0 : DIGITS.indexOf(a.charAt(0));
        int digitB = b == null ? BASE : DIGITS.indexOf(b.charAt(0));
        if (digitB - digitA > 1) {
            return String.valueOf(DIGITS.charAt((digitA + digitB + 1) / 2));
        }
        // First digits are consecutive.
        if (b != null && b.length() > 1) {
            return b.substring(0, 1);
        }
        return DIGITS.charAt(digitA) + midpoint(a.length() > 1 ? a.substring(1) : "", null);
    }

    private static char charAt(String s, int i) {
        return i < s.length() ? s.charAt(i) : ZERO;
    }

    private static String encode(long value, int width) {
        var chars = new char[width];
        for (int i = width - 1; i >= 0; i--) {
            chars[i] = DIGITS.charAt((int) (value % BASE));
            value /= BASE;
        }
        return new String(chars);
    }

    private static String stripTrailingZeros(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == ZERO) end--;
        return s.substring(0, end);
    }

    private static void validate(String key) {
        if (!key.isEmpty() && key.charAt(key.length() - 1) == ZERO) {
            throw new IllegalArgumentException("Invalid key (trailing zero): " + key);
        }
        for (int i = 0; i < key.length(); i++) {
            if (DIGITS.indexOf(key.charAt(i)) < 0) {
                throw new IllegalArgumentException("Invalid key character in: " + key);
            }
        }
    }
}
