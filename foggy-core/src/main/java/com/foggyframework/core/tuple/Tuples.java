package com.foggyframework.core.tuple;

import java.util.function.Function;

public abstract class Tuples implements Function {
    static final Tuples empty = new Tuples() {
    };

    public static Tuple2 fromArray(Object[] list) {
        if (list != null && list.length >= 2) {
            switch (list.length) {
                case 2:
                    return of(list[0], list[1]);
                case 3:
                    return of(list[0], list[1], list[2]);
                case 4:
                    return of(list[0], list[1], list[2], list[3]);
                case 5:
                    return of(list[0], list[1], list[2], list[3], list[4]);
                case 6:
                    return of(list[0], list[1], list[2], list[3], list[4], list[5]);
                case 7:
                    return of(list[0], list[1], list[2], list[3], list[4], list[5], list[6]);
                case 8:
                    return of(list[0], list[1], list[2], list[3], list[4], list[5], list[6], list[7]);
                default:
                    throw new IllegalArgumentException("too many arguments (" + list.length + "), need between 2 and 8 values");
            }
        } else {
            throw new IllegalArgumentException("null or too small array, need between 2 and 8 values");
        }
    }

    public static <T1, T2> Tuple2<T1, T2> of(T1 t1, T2 t2) {
        return new Tuple2(t1, t2);
    }

    public static <T1, T2, T3> Tuple3<T1, T2, T3> of(T1 t1, T2 t2, T3 t3) {
        return new Tuple3(t1, t2, t3);
    }

    public static <T1, T2, T3, T4> Tuple4<T1, T2, T3, T4> of(T1 t1, T2 t2, T3 t3, T4 t4) {
        return new Tuple4(t1, t2, t3, t4);
    }

    public static <T1, T2, T3, T4, T5> Tuple5<T1, T2, T3, T4, T5> of(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5) {
        return new Tuple5(t1, t2, t3, t4, t5);
    }

    public static <T1, T2, T3, T4, T5, T6> Tuple6<T1, T2, T3, T4, T5, T6> of(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5, T6 t6) {
        return new Tuple6(t1, t2, t3, t4, t5, t6);
    }

    public static <T1, T2, T3, T4, T5, T6, T7> Tuple7<T1, T2, T3, T4, T5, T6, T7> of(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5, T6 t6, T7 t7) {
        return new Tuple7(t1, t2, t3, t4, t5, t6, t7);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8> Tuple8<T1, T2, T3, T4, T5, T6, T7, T8> of(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5, T6 t6, T7 t7, T8 t8) {
        return new Tuple8(t1, t2, t3, t4, t5, t6, t7, t8);
    }

    public static Function<Object[], Tuple2> fnAny() {
        return empty;
    }

    public static <R> Function<Object[], R> fnAny(Function<Tuple2, R> delegate) {
        return (objects) -> {
            return delegate.apply(fnAny().apply(objects));
        };
    }

    public static <T1, T2> Function<Object[], Tuple2<T1, T2>> fn2() {
        return empty;
    }

    public static <T1, T2, T3> Function<Object[], Tuple3<T1, T2, T3>> fn3() {
        return empty;
    }

    public Tuple2 apply(Object o) {
        return fromArray((Object[])((Object[])o));
    }

    static StringBuilder tupleStringRepresentation(Object... values) {
        StringBuilder sb = new StringBuilder();

        for(int i = 0; i < values.length; ++i) {
            Object t = values[i];
            if (i != 0) {
                sb.append(',');
            }

            if (t != null) {
                sb.append(t);
            }
        }

        return sb;
    }

    Tuples() {
    }
}
