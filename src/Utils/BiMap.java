package Utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class BiMap<K1, K2, V> extends DefaultMap<Pair<K1, K2>, V> {
    private static final long serialVersionUID = 9193951733396861212L;
    private final BiFunction<K1, K2, V> defaultProvider;

    public BiMap() {
        defaultProvider = null;
    }

    public BiMap(BiFunction<K1, K2, V> defaultProvider) {
        super(x -> defaultProvider.apply(x.first, x.second));
        this.defaultProvider = defaultProvider;
    }

    public BiMap(BiMap<K1, K2, V> initializer) {
        super(initializer);
        defaultProvider = initializer.defaultProvider;
    }

//    public BiMap(Map<Pair<K1, K2>, V> initializer) {
//        super(initializer);
//        defaultProvider = null;
//    }

    public V get(K1 key1, K2 key2) {
        return get(new Pair<>(key1, key2));
    }

    public void put(K1 key1, K2 key2, V value) {
        put(new Pair<>(key1, key2), value);
    }

    // PERFORMANCE
    public Set<Entry<K1, K2, V>> getEntries() {
        return entrySet().stream()
                .map(x -> new Entry<K1, K2, V>(x.getKey().first, x.getKey().second, x.getValue()))
                .collect(Collectors.toSet());
    }

    public Set<K1> getKeys1() {
        return keySet().stream().map(x -> x.first).collect(Collectors.toSet());
    }

    public Set<K2> getKeys2() {
        return keySet().stream().map(x -> x.second).collect(Collectors.toSet());
    }

    public BiMap<K2, K1, V> getSwapped() {
        BiMap<K2, K1, V> result;
        if (defaultProvider == null)
            result = new BiMap<>();
        else
            result = new BiMap<>((a, b) -> defaultProvider.apply(b, a));
        result.putAll(entrySet().stream()
                .collect(Collectors.toMap(x -> x.getKey().getSwapped(), Map.Entry::getValue)));
        return result;
    }

    public static <T, K1, K2, A, D>
    Collector<T, ?, BiMap<K1, K2, D>> groupingBy(Function<? super T, ? extends K1> classifier1,
                                                 Function<? super T, ? extends K2> classifier2,
                                                 Collector<? super T, A, D> downstream) {
        return Collectors.groupingBy(x -> new Pair<>(classifier1.apply(x), classifier2.apply(x)),
                BiMap::new, downstream);
    }

    public static <T, K1, K2, U>
    Collector<T, ?, BiMap<K1, K2, U>> toMap(Function<? super T, ? extends K1> key1Mapper,
                                            Function<? super T, ? extends K2> key2Mapper,
                                            Function<? super T, ? extends U> valueMapper,
                                            BinaryOperator<U> mergeFunction) {
        return Collectors.toMap(x -> new Pair<>(key1Mapper.apply(x), key2Mapper.apply(x)),
                valueMapper, mergeFunction, BiMap::new);
    }

    public static class Entry<K1, K2, V> {
        private final K1 key1;
        private final K2 key2;
        private final V value;

        public Entry(K1 key1, K2 key2, V value) {
            this.key1 = key1;
            this.key2 = key2;
            this.value = value;
        }

        public K1 getKey1() {
            return key1;
        }

        public K2 getKey2() {
            return key2;
        }

        public V getValue() {
            return value;
        }
    }
}
