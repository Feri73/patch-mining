package Utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class General {
    private General() {
    }

    // PERFORMANCE
    // this should not be greedy
    public static <T> List<Pair<T, T>> getGreedyMatches(BiMap<T, T, Double> t1T2Scores) {
        Map<T, T> matches = new HashMap<>();

        while (true) {
            double currentMax = Double.NEGATIVE_INFINITY;
            Pair<T, T> currentMatching = null;

            for (BiMap.Entry<T, T, Double> t1T2Score : t1T2Scores.getEntries())
                // PERFORMANCE: CONTAINS_VALUE
                if (!matches.containsKey(t1T2Score.getKey1()) && !matches.containsValue(t1T2Score.getKey2())
                        && t1T2Score.getValue() > currentMax) {
                    currentMax = t1T2Score.getValue();
                    currentMatching = new Pair<>(t1T2Score.getKey1(), t1T2Score.getKey2());
                }

            if (currentMatching == null)
                break;
            matches.put(currentMatching.getFirst(), currentMatching.getSecond());
        }

        return matches.entrySet().stream().map(x -> new Pair<>(x.getKey(), x.getValue())).collect(Collectors.toList());
    }

    public static <T> Map<T, T> getMatchMap(List<Pair<T, T>> matches) {
        return matches.stream().collect(() -> new HashMap<T, T>(), (a, b) -> {
            a.put(b.getFirst(), b.getSecond());
            a.put(b.getSecond(), b.getFirst());
        }, (a, b) -> a.putAll(b));
    }
}
