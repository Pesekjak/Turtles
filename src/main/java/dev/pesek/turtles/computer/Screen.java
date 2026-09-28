package dev.pesek.turtles.computer;

import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.ThreadSafe;
import dev.pesek.turtles.util.Colors;
import dev.pesek.turtles.util.DefaultFontInfo;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@ThreadSafe
public final class Screen {

    public static final int DEFAULT_SCREEN_LINES = 16;
    public static final int DEFAULT_SCREEN_WIDTH = 300;

    public static Screen create() {
        return create(null);
    }

    public static Screen create(@Nullable Runnable drawListener) {
        return create(DEFAULT_SCREEN_WIDTH, DEFAULT_SCREEN_LINES, drawListener);
    }

    public static Screen create(int width, int height) {
        return new Screen(width, height, null);
    }

    public static Screen create(int width, int height, @Nullable Runnable drawListener) {
        return new Screen(width, height, drawListener);
    }

    private final int width;
    private final int height;

    private final Deque<String> lines = new LinkedList<>();
    private final @Nullable Runnable drawListener;

    private final ThreadLocal<List<String>> capturedOutput = new ThreadLocal<>();

    private Screen(int width, int height, @Nullable Runnable drawListener) {
        this.width = width;
        this.height = height;
        this.drawListener = drawListener;
        for (int i = 0; i < height; i++) {
            lines.add("");
        }
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void startCapture() {
        capturedOutput.set(new ArrayList<>());
    }

    public String[] endCapture() {
        List<String> captured = capturedOutput.get();
        capturedOutput.remove();
        if (captured == null) {
            return new String[0];
        }
        return captured.toArray(new String[0]);
    }

    public void print(Collection<String> lines) {
        lines.forEach(this::print);
    }

    public void print(String... lines) {
        for (String line : lines) {
            print(line);
        }
    }

    public void print(String line) {
        Preconditions.checkNotNull(line, "line");
        String[] split = line.split("\n");

        List<String> wrapped = new ArrayList<>();
        for (String s : split) {
            wrapped.addAll(wrapText(s));
        }

        List<String> captured = capturedOutput.get();
        if (captured != null) {
            captured.addAll(wrapped);
            return;
        }

        synchronized (lines) {
            lines.addAll(wrapped);
            evictOldLines();
        }
        if (drawListener != null) {
            drawListener.run();
        }
    }

    public String[] render() {
        synchronized (lines) {
            return lines.toArray(new String[0]);
        }
    }

    private List<String> wrapText(String text) {
        List<String> wrappedLines = new ArrayList<>();
        if (text.isEmpty()) {
            wrappedLines.add("");
            return wrappedLines;
        }

        StringBuilder currentLine = new StringBuilder();
        int currentLineWidth = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == '§' && i + 1 < text.length()) {
                char formatChar = text.charAt(i + 1);
                if (Colors.isColorChar(formatChar)) {
                    currentLine.append(c).append(formatChar);
                    i++;
                    continue;
                }
            }

            int charWidth = DefaultFontInfo.getDefaultFontInfo(c).getLength();

            if (currentLineWidth + charWidth > width) {
                wrappedLines.add(currentLine.toString());
                currentLine.setLength(0);
                currentLineWidth = 0;
            }

            currentLine.append(c);
            currentLineWidth += charWidth;
        }

        if (!currentLine.isEmpty()) {
            wrappedLines.add(currentLine.toString());
        }

        return wrappedLines;
    }

    private void evictOldLines() {
        while (lines.size() > height) {
            lines.removeFirst();
        }
    }

}
