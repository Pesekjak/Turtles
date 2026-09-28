package dev.pesek.turtles.util;

import com.google.common.base.Preconditions;
import dev.pesek.turtles.computer.Computer;
import org.jetbrains.annotations.NotNull;

import java.io.Writer;

public class ScreenWriter extends Writer {

    private final Computer computer;
    private final StringBuilder buffer = new StringBuilder();

    public ScreenWriter(Computer computer) {
        this.computer = Preconditions.checkNotNull(computer, "computer");
    }

    @Override
    public void write(char @NotNull [] cbuf, int off, int len) {
        //noinspection SynchronizeOnNonFinalField
        synchronized (lock) {
            for (int i = off; i < off + len; i++) {
                char c = cbuf[i];
                if (c == '\n') {
                    computer.print(buffer.toString());
                    buffer.setLength(0);
                } else if (c != '\r') {
                    buffer.append(c);
                }
            }
        }
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }

}
