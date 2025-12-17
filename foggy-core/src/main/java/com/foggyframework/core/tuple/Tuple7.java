package com.foggyframework.core.tuple;

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//


import java.util.function.Function;

public class Tuple7<T1, T2, T3, T4, T5, T6, T7> extends Tuple6<T1, T2, T3, T4, T5, T6> {
    private static final long serialVersionUID = -8002391247456579281L;
    
    final T7 t7;

    public   Tuple7(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5, T6 t6, T7 t7) {
        super(t1, t2, t3, t4, t5, t6);
        this.t7 = t7;//Objects.requireNonNull(t7, "t7");
    }

    public T7 getT7() {
        return this.t7;
    }

    public <R> Tuple7<R, T2, T3, T4, T5, T6, T7> mapT1(Function<T1, R> mapper) {
        return new Tuple7(mapper.apply(this.t1), this.t2, this.t3, this.t4, this.t5, this.t6, this.t7);
    }

    public <R> Tuple7<T1, R, T3, T4, T5, T6, T7> mapT2(Function<T2, R> mapper) {
        return new Tuple7(this.t1, mapper.apply(this.t2), this.t3, this.t4, this.t5, this.t6, this.t7);
    }

    public <R> Tuple7<T1, T2, R, T4, T5, T6, T7> mapT3(Function<T3, R> mapper) {
        return new Tuple7(this.t1, this.t2, mapper.apply(this.t3), this.t4, this.t5, this.t6, this.t7);
    }

    public <R> Tuple7<T1, T2, T3, R, T5, T6, T7> mapT4(Function<T4, R> mapper) {
        return new Tuple7(this.t1, this.t2, this.t3, mapper.apply(this.t4), this.t5, this.t6, this.t7);
    }

    public <R> Tuple7<T1, T2, T3, T4, R, T6, T7> mapT5(Function<T5, R> mapper) {
        return new Tuple7(this.t1, this.t2, this.t3, this.t4, mapper.apply(this.t5), this.t6, this.t7);
    }

    public <R> Tuple7<T1, T2, T3, T4, T5, R, T7> mapT6(Function<T6, R> mapper) {
        return new Tuple7(this.t1, this.t2, this.t3, this.t4, this.t5, mapper.apply(this.t6), this.t7);
    }

    public <R> Tuple7<T1, T2, T3, T4, T5, T6, R> mapT7(Function<T7, R> mapper) {
        return new Tuple7(this.t1, this.t2, this.t3, this.t4, this.t5, this.t6, mapper.apply(this.t7));
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
            default:
                return null;
        }
    }

    public Object[] toArray() {
        return new Object[]{this.t1, this.t2, this.t3, this.t4, this.t5, this.t6, this.t7};
    }

    public boolean equals( Object o) {
        if (this == o) {
            return true;
        } else if (!(o instanceof Tuple7)) {
            return false;
        } else if (!super.equals(o)) {
            return false;
        } else {
            Tuple7 tuple7 = (Tuple7)o;
            return this.t7.equals(tuple7.t7);
        }
    }

    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + this.t7.hashCode();
        return result;
    }

    public int size() {
        return 7;
    }
}
