package dev.pesek.turtles.util;

import com.google.common.base.Preconditions;
import io.jactl.TokenType;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class JactlHighlighter {

    private static final Pattern PATTERN;

    static {
        String comments = "(//.*|/\\*[\\s\\S]*?\\*/)";
        String strings = "(\"\"\"[\\s\\S]*?\"\"\"|'''[\\s\\S]*?'''|\"(?:\\\\.|[^\\\\\"])*\"|'(?:\\\\.|[^\\\\'])*')";
        String keywordsList = Arrays.stream(TokenType.values())
                .filter(t -> t.asString != null && t.asString.matches("[a-zA-Z]+"))
                .map(t -> Pattern.quote(t.asString))
                .collect(Collectors.joining("|"));
        String keywords = "\\b(" + keywordsList + ")\\b";
        String numbers = "\\b(0x[0-9a-fA-F]+|\\d+\\.?\\d*(?:[eE][-+]?\\d+)?)\\b";
        PATTERN = Pattern.compile(
                comments + "|" + strings + "|" + keywords + "|" + numbers
        );
    }

    public static String highlight(String code) {
        Preconditions.checkNotNull(code, "code");

        if (code.isEmpty()) {
            return code;
        }

        Matcher matcher = PATTERN.matcher(code);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String replacement = getReplacement(matcher);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    private static String getReplacement(Matcher matcher) {
        String replacement;

        if (matcher.group(1) != null) {
            // Comments
            replacement = Colors.DARK_GRAY + matcher.group(1) + Colors.RESET;
        } else if (matcher.group(2) != null) {
            // Strings
            replacement = Colors.GRAY + matcher.group(2) + Colors.RESET;
        } else if (matcher.group(3) != null) {
            // Keywords
            replacement = Colors.GOLD + matcher.group(3) + Colors.RESET;
        } else if (matcher.group(4) != null) {
            // Numbers
            replacement = Colors.RED + matcher.group(4) + Colors.RESET;
        } else {
            replacement = matcher.group(0);
        }
        return replacement;
    }

    private JactlHighlighter() {
        throw new UnsupportedOperationException();
    }

}
