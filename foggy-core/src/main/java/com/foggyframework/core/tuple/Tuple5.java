package com.foggyframework.core.tuple;

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//


import java.util.function.Function;

public class Tuple5<T1, T2, T3, T4, T5> extends Tuple4<T1, T2, T3, T4> {
    private static final long serialVersionUID = 3541548454198133275L;
    
    final T5 t5;

    public Tuple5(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5) {
        super(t1, t2, t3, t4);
        this.t5 = t5;//Objects.requireNonNull(t5, "t5");
    }

    public T5 getT5() {
        return this.t5;
    }

    public <R> Tuple5<R, T2, T3, T4, T5> mapT1(Function<T1, R> mapper) {
        return new Tuple5(mapper.apply(this.t1), this.t2, this.t3, this.t4, this.t5);
    }

    public <R> Tuple5<T1, R, T3, T4, T5> mapT2(Function<T2, R> mapper) {
        return new Tuple5(this.t1, mapper.apply(this.t2), this.t3, this.t4, this.t5);
    }

    public <R> Tuple5<T1, T2, R, T4, T5> mapT3(Function<T3, R> mapper) {
        return new Tuple5(this.t1, this.t2, mapper.apply(this.t3), this.t4, this.t5);
    }

    public <R> Tuple5<T1, T2, T3, R, T5> mapT4(Function<T4, R> mapper) {
        return new Tuple5(this.t1, this.t2, this.t3, mapper.apply(this.t4), this.t5);
    }

    public <R> Tuple5<T1, T2, T3, T4, R> mapT5(Function<T5, R> mapper) {
        return new Tuple5(this.t1, this.t2, this.t3, this.t4, mapper.apply(this.t5));
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
            default:
                return null;
        }
    }

    public Object[] toArray() {
        return new Object[]{this.t1, this.t2, this.t3, this.t4, this.t5};
    }

    public boolean equals( Object o) {
        if (this == o) {
            return true;
        } else if (!(o instanceof Tuple5)) {
            return false;
        } else if (!super.equals(o)) {
            return false;
        } else {
            Tuple5 tuple5 = (Tuple5)o;
            return this.t5.equals(tuple5.t5);
        }
    }

    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + this.t5.hashCode();
        return result;
    }

    public int size() {
        return 5;
    }
}