package dev.pesek.turtles.computer.script;

@FunctionalInterface
public interface ComputerRunnable {

    void run(ComputerInvocationContext context) throws Exception;

}
