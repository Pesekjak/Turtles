package dev.pesek.turtles.computer.script;

@FunctionalInterface
public interface ComputerCallable<V> {

    V call(ComputerInvocationContext context) throws Exception;

}
