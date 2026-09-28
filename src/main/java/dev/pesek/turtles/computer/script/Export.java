package dev.pesek.turtles.computer.script;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Exported methods can be used by the end-users in their scripts
 * either as class methods or functions.
 * <p>
 * Keep in mind that methods and functions do not support overloading, so each
 * must have a unique name.
 */
public interface Export {

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Type {

        /**
         * @return fully qualified name of the exported type
         */
        String name();

        /**
         * @return whether the type should be automatically imported
         */
        boolean autoImport() default false;

    }

    /**
     * Marks method for the scripting API.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Method {

        /**
         * @return name of the exported method for the scripting API
         */
        String name();

        /**
         * Names of the parameters.
         *
         * @return names of the parameters
         */
        String[] params();

        /**
         * @return whether the method can throw an exception
         */
        boolean doesThrow() default false;

    }

    /**
     * Marks function for the scripting API.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Function {

        /**
         * @return name of the exported function for the scripting API
         */
        String name();

        /**
         * Names of the parameters, this excludes source, offset parameters.
         *
         * @return names of the parameters
         */
        String[] params();

    }

}
