package dev.pesek.turtles.computer.script;

import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.Nullable;
import org.openjdk.jol.info.ClassLayout;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility to estimate sizes of objects.
 * <p>
 * This class can be implemented by types exported to script environment,
 * especially if such objects can dynamically resize or store large amounts of data.
 */
public interface SizedObject {

    /**
     * Returns estimated size of objects.
     *
     * @param objects objects
     * @return their estimated size
     */
    static long sizeOf(Collection<?> objects) {
        Visitor visitor = new Visitor();
        objects.forEach(visitor::visit);
        return visitor.size;
    }

    static SizedObject of(@Nullable Object obj) {
        return switch (obj) {
            case SizedObject sizedObject -> sizedObject;

            case String str -> () -> instanceSize(String.class) + 16 + (str.length() * 2L);

            case BigDecimal _ -> () -> instanceSize(BigDecimal.class) + 48;

            case List<?> list -> new SizedObject() {
                @Override
                public long getEstimatedByteSize() {
                    return instanceSize(list.getClass()) + 16 + (list.size() * 4L);
                }

                @Override
                public void visit(Visitor visitor) {
                    SizedObject.super.visit(visitor);
                    list.forEach(visitor::visit);
                }
            };

            case Map<?, ?> map -> new SizedObject() {
                @Override
                public long getEstimatedByteSize() {
                    return instanceSize(map.getClass()) + 16 + (map.size() * 32L);
                }

                @Override
                public void visit(Visitor visitor) {
                    SizedObject.super.visit(visitor);
                    map.forEach((k, v) -> {
                        visitor.visit(k);
                        visitor.visit(v);
                    });
                }
            };

            case Object[] array -> new SizedObject() {
                @Override
                public long getEstimatedByteSize() {
                    return 16 + (array.length * 4L);
                }

                @Override
                public void visit(Visitor visitor) {
                    SizedObject.super.visit(visitor);
                    for (Object element : array) {
                        visitor.visit(element);
                    }
                }
            };

            case byte[] arr -> () -> 16 + arr.length;
            case boolean[] arr -> () -> 16 + arr.length;
            case short[] arr -> () -> 16 + (arr.length * 2L);
            case char[] arr -> () -> 16 + (arr.length * 2L);
            case int[] arr -> () -> 16 + (arr.length * 4L);
            case float[] arr -> () -> 16 + (arr.length * 4L);
            case long[] arr -> () -> 16 + (arr.length * 8L);
            case double[] arr -> () -> 16 + (arr.length * 8L);

            case Object _ -> () -> instanceSize(obj.getClass());

            case null -> () -> 0;
        };
    }

    /**
     * Returns size of instances of a type.
     * <p>
     * This does not include sizes of owned objects by such instance,
     * it is only based on the class layout.
     *
     * @param type type
     * @return size of an instance of such type
     */
    static long instanceSize(Class<?> type) {
        class Holder {
            static final Map<Class<?>, Long> SHALLOW_SIZES = new ConcurrentHashMap<>();
        }
        return Holder.SHALLOW_SIZES.computeIfAbsent(type, _ -> ClassLayout.parseClass(type).instanceSize());
    }

    /**
     * Returns the estimated memory footprint of this object in bytes.
     * <p>
     * This does not include size of additional objects this instance owns.
     * For that override {@link #visit(Visitor)} and visit such objects.
     *
     * @return the estimated memory footprint of this object in bytes
     */
    long getEstimatedByteSize();

    /**
     * Visits objects owned by this instance.
     *
     * @param visitor visitor
     */
    @MustBeInvokedByOverriders
    default void visit(Visitor visitor) {
    }

    final class Visitor {

        private final Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        long size = 0;

        public void visit(@Nullable Object... objects) {
            for (Object object : objects) {
                visit(object);
            }
        }

        public void visit(@Nullable Object object) {
            if (object != null && visited.add(object)) {
                SizedObject sizedObject = of(object);
                size += sizedObject.getEstimatedByteSize();
                sizedObject.visit(this);
            }
        }

    }

}
