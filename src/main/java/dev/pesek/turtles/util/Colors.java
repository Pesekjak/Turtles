package dev.pesek.turtles.util;

import com.google.common.base.Preconditions;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum Colors {

    BLACK('0'),
    DARK_BLUE('1'),
    DARK_GREEN('2'),
    DARK_AQUA('3'),
    DARK_RED('4'),
    DARK_PURPLE('5'),
    GOLD('6'),
    GRAY('7'),
    DARK_GRAY('8'),
    BLUE('9'),
    GREEN('a'),
    AQUA('b'),
    RED('c'),
    LIGHT_PURPLE('d'),
    YELLOW('e'),
    WHITE('f'),

    OBFUSCATED('k'),
    BOLD('l'),
    STRIKETHROUGH('m'),
    UNDERLINE('n'),
    ITALIC('o'),
    RESET('r');

    private static final Set<Character> COLOR_CHARS = Arrays.stream(values())
            .map(Colors::getChar)
            .collect(Collectors.toSet());

    public static boolean isColorChar(char c) {
        return COLOR_CHARS.contains(c);
    }

    public static String stripColors(String str) {
        Preconditions.checkNotNull(str, "str");
        return str.replaceAll("(?i)§[0-9a-fk-or]", "");
    }

    private final char c;

    Colors(char c) {
        this.c = c;
    }

    public char getChar() {
        return c;
    }

    @Override
    public String toString() {
        return "§" + c;
    }

}
