package dev.pesek.turtles.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation can be used at different places to
 * specify documentation of different parts of the computer
 * programs used by players.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Docs {

    /**
     * @apiNote is meant to be used with Java's text block
     */
    String value();

}
