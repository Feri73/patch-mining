package Utils;

import java.io.Serializable;
import java.util.Objects;

public class Pair<A, B> implements Serializable {
    private static final long serialVersionUID = 3841961424044112820L;
    protected final A first;
    protected final B second;

    public Pair(A first, B second) {
        this.first = first;
        this.second = second;
    }

    public A getFirst() {
        return first;
    }

    public B getSecond() {
        return second;
    }

    public <E> boolean hasElement(E e) {
        if (e == null) {
            return first == null || second == null;
        } else {
            return e.equals(first) || e.equals(second);
        }
    }

    @Override
    public String toString() {
        return "(" + first + "," + second + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        else if (!(o instanceof Pair))
            return false;
        else {
            Pair<A, B> other = (Pair) o;
            return Objects.equals(first, other.first) && Objects.equals(second, other.second);
        }
    }

    public Pair<B, A> getSwapped() {
        return new Pair<>(second, first);
    }

    @Override
    public int hashCode() {
        return Objects.hash(first, second);
    }

    public static <A, B> Pair<A, B> of(A a, B b) {
        return new Pair<A, B>(a, b);
    }
}
