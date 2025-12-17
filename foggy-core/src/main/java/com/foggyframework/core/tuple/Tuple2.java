package com.foggyframework.core.tuple;

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//


import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

public class Tuple2<T1, T2> implements Iterable<Object>, Serializable {
    private static final long serialVersionUID = -3518082018884860684L;
    final T1 t1;
    final T2 t2;

    public  Tuple2(T1 t1, T2 t2) {
        this.t1 = t1;//Objects.requireNonNull(t1, "t1");
        this.t2 = t2;//Objects.requireNonNull(t2, "t2");
    }

    public T1 getT1() {
        return this.t1;
    }

    public T2 getT2() {
        return this.t2;
    }

    public <R> Tuple2<R, T2> mapT1(Function<T1, R> mapper) {
        return new Tuple2(mapper.apply(this.t1), this.t2);
    }

    public <R> Tuple2<T1, R> mapT2(Function<T2, R> mapper) {
        return new Tuple2(this.t1, mapper.apply(this.t2));
    }

    public Object get(int index) {
        switch (index) {
            case 0:
                return this.t1;
            case 1:
                return this.t2;
            default:
                return null;
        }
    }

    public List<Object> toList() {
        return Arrays.asList(this.toArray());
    }

    public Object[] toArray() {
        return new Object[]{this.t1, this.t2};
    }

    public Iterator<Object> iterator() {
        return Collections.unmodifiableList(this.toList()).iterator();
    }

    public boolean equals( Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            Tuple2<?, ?> tuple2 = (Tuple2)o;
            return this.t1.equals(tuple2.t1) && this.t2.equals(tuple2.t2);
        } else {
            return false;
        }
    }

    public int hashCode() {
        int result = this.size();
        result = 31 * result + this.t1.hashCode();
        result = 31 * result + this.t2.hashCode();
        return result;
    }

    public int size() {
        return 2;
    }

    public final String toString() {
        return Tuples.tupleStringRepresentation(this.toArray()).insert(0, '[').append(']').toString();
    }
}

