package org.roketsim.core.math;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Internal NumPy/Python 3.12 numeric-text boundary, not a tolerance policy.
 * Unicode 15.0 decimal-zero blocks and surrounding whitespace were enumerated
 * against Python 3.12.4 / NumPy 2.5.2 as_vector (AUD-002).
 * Fixed tables avoid dependence on a different JDK Unicode database.
 */
final class PythonNumericText {
    private static final int[] DECIMAL_ZEROS = {0x30, 0x660, 0x6f0, 0x7c0, 0x966, 0x9e6, 0xa66, 0xae6, 0xb66, 0xbe6, 0xc66, 0xce6, 0xd66, 0xde6, 0xe50, 0xed0, 0xf20, 0x1040, 0x1090, 0x17e0, 0x1810, 0x1946, 0x19d0, 0x1a80, 0x1a90, 0x1b50, 0x1bb0, 0x1c40, 0x1c50, 0xa620, 0xa8d0, 0xa900, 0xa9d0, 0xa9f0, 0xaa50, 0xabf0, 0xff10, 0x104a0, 0x10d30, 0x11066, 0x110f0, 0x11136, 0x111d0, 0x112f0, 0x11450, 0x114d0, 0x11650, 0x116c0, 0x11730, 0x118e0, 0x11950, 0x11c50, 0x11d50, 0x11da0, 0x11f50, 0x16a60, 0x16ac0, 0x16b50, 0x1d7ce, 0x1d7d8, 0x1d7e2, 0x1d7ec, 0x1d7f6, 0x1e140, 0x1e2f0, 0x1e4f0, 0x1e950, 0x1fbf0};
    private static final int[] WHITESPACE = {0x9, 0xa, 0xb, 0xc, 0xd, 0x20, 0x85, 0xa0, 0x1680, 0x2000, 0x2001, 0x2002, 0x2003, 0x2004, 0x2005, 0x2006, 0x2007, 0x2008, 0x2009, 0x200a, 0x2028, 0x2029, 0x202f, 0x205f, 0x3000};
    private static final Pattern SPECIAL = Pattern.compile("(?i:[+-]?(?:inf(?:inity)?|nan))");

    private PythonNumericText() { }

    static double parse(String input) {
        StringBuilder mapped = new StringBuilder();
        input.codePoints().forEach(cp -> {
            for (int zero : DECIMAL_ZEROS) {
                if (cp >= zero && cp < zero + 10) {
                    mapped.append((char) ('0' + cp - zero));
                    return;
                }
            }
            for (int space : WHITESPACE) {
                if (cp == space) {
                    mapped.append(' ');
                    return;
                }
            }
            mapped.appendCodePoint(cp);
        });
        int begin = 0, end = mapped.length();
        while (begin < end && mapped.charAt(begin) == ' ') { begin++; }
        while (end > begin && mapped.charAt(end - 1) == ' ') { end--; }
        String text = mapped.substring(begin, end);
        if (SPECIAL.matcher(text).matches()) {
            String lower = text.toLowerCase(Locale.ROOT);
            if (lower.endsWith("nan")) { return Double.NaN; }
            return lower.startsWith("-") ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        }
        if (!decimal(text)) {
            throw new NumberFormatException("Invalid Python numeric text");
        }
        return Double.parseDouble(text.replace("_", ""));
    }

    // Iterative grammar validation avoids regex recursion for long valid digitparts.
    private static boolean decimal(String text) {
        int size = text.length();
        int begin = sign(text, 0);
        int end = digits(text, begin);
        boolean hasDigits = end > begin;
        if (end < size && text.charAt(end) == '.') {
            int fraction = end + 1;
            end = digits(text, fraction);
            hasDigits |= end > fraction;
        }
        if (!hasDigits) { return false; }
        if (end < size && (text.charAt(end) == 'e' || text.charAt(end) == 'E')) {
            int exponent = sign(text, end + 1);
            end = digits(text, exponent);
            if (end == exponent) { return false; }
        }
        return end == size;
    }

    private static int sign(String text, int index) {
        return index < text.length() && (text.charAt(index) == '+' || text.charAt(index) == '-')
            ? index + 1 : index;
    }

    private static int digits(String text, int index) {
        if (index >= text.length() || !digit(text.charAt(index))) { return index; }
        int end = index + 1;
        while (end < text.length()) {
            if (digit(text.charAt(end))) { end++; }
            else if (text.charAt(end) == '_' && end + 1 < text.length() && digit(text.charAt(end + 1))) {
                end += 2;
            } else { break; }
        }
        return end;
    }

    private static boolean digit(char value) { return value >= '0' && value <= '9'; }
}
