package com.foggyframework.core.tuple;

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//


import java.util.function.Function;

public class Tuple8<T1, T2, T3, T4, T5, T6, T7, T8> extends Tuple7<T1, T2, T3, T4, T5, T6, T7> {
    private static final long serialVersionUID = -8746796646535446242L;

    final T8 t8;

    public Tuple8(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5, T6 t6, T7 t7, T8 t8) {
        super(t1, t2, t3, t4, t5, t6, t7);
        this.t8 = t8;//Objects.requireNonNull(t8, "t8");
    }

    public T8 getT8() {
        return this.t8;
    }

    public <R> Tuple8<R, T2, T3, T4, T5, T6, T7, T8> mapT1(Function<T1, R> mapper) {
        return new Tuple8(mapper.apply(this.t1), this.t2, this.t3, this.t4, this.t5, this.t6, this.t7, this.t8);
    }

    public <R> Tuple8<T1, R, T3, T4, T5, T6, T7, T8> mapT2(Function<T2, R> mapper) {
        return new Tuple8(this.t1, mapper.apply(this.t2), this.t3, this.t4, this.t5, this.t6, this.t7, this.t8);
    }

    public <R> Tuple8<T1, T2, R, T4, T5, T6, T7, T8> mapT3(Function<T3, R> mapper) {
        return new Tuple8(this.t1, this.t2, mapper.apply(this.t3), this.t4, this.t5, this.t6, this.t7, this.t8);
    }

    public <R> Tuple8<T1, T2, T3, R, T5, T6, T7, T8> mapT4(Function<T4, R> mapper) {
        return new Tuple8(this.t1, this.t2, this.t3, mapper.apply(this.t4), this.t5, this.t6, this.t7, this.t8);
    }

    public <R> Tuple8<T1, T2, T3, T4, R, T6, T7, T8> mapT5(Function<T5, R> mapper) {
        return new Tuple8(this.t1, this.t2, this.t3, this.t4, mapper.apply(this.t5), this.t6, this.t7, this.t8);
    }

    public <R> Tuple8<T1, T2, T3, T4, T5, R, T7, T8> mapT6(Function<T6, R> mapper) {
        return new Tuple8(this.t1, this.t2, this.t3, this.t4, this.t5, mapper.apply(this.t6), this.t7, this.t8);
    }

    public <R> Tuple8<T1, T2, T3, T4, T5, T6, R, T8> mapT7(Function<T7, R> mapper) {
        return new Tuple8(this.t1, this.t2, this.t3, this.t4, this.t5, this.t6, mapper.apply(this.t7), this.t8);
    }

    public <R> Tuple8<T1, T2, T3, T4, T5, T6, T7, R> mapT8(Function<T8, R> mapper) {
        return new Tuple8(this.t1, this.t2, this.t3, this.t4, this.t5, this.t6, this.t7, mapper.apply(this.t8));
    }


    public Object get(int index) {
        switch (index) {
            case 0:
                return this.t1;
            case 1:
                return this.t2;
            case 2:
                return this.t3;
            case 3:
                return this.t4;
            case 4:
                return this.t5;
            case 5:
                return this.t6;
            case 6:
                return this.t7;
            case 7:
                return this.t8;
            default:
                return null;
        }
    }

    public Object[] toArray() {
        return new Object[]{this.t1, this.t2, this.t3, this.t4, this.t5, this.t6, this.t7, this.t8};
    }

    public boolean equals( Object o) {
        if (this == o) {
            return true;
        } else if (!(o instanceof Tuple8)) {
            return false;
        } else if (!super.equals(o)) {
            return false;
        } else {
            Tuple8 tuple8 = (Tuple8)o;
            return this.t8.equals(tuple8.t8);
        }
    }

    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + this.t8.hashCode();
        return result;
    }

    public int size() {
        return 8;
    }
}


