package Utils;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class DefaultMap<K, V> extends HashMap<K, V> {
    private static final long serialVersionUID = -5182318159336321208L;
    private final Function<K, V> defaultProvider;

    public DefaultMap() {
        defaultProvider = null;
    }

    public DefaultMap(Function<K, V> defaultProvider) {
        this.defaultProvider = defaultProvider;
    }

    public DefaultMap(DefaultMap<K, V> initializer) {
        super(initializer);
        defaultProvider = initializer.defaultProvider;
    }

    public DefaultMap(Map<K, V> initializer) {
        super(initializer);
        defaultProvider = null;
    }

    @Override
    public V get(Object key) {
        K k = (K) key;
        V res = super.get(k);
        if (res == null && defaultProvider != null) {
            put(k, defaultProvider.apply(k));
            res = super.get(k);
        }
        return res;
    }
}
