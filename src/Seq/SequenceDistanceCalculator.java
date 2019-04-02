package Seq;

import Utils.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

// using this function, sequences with shorted length get more probability (is it bad at all?)
// does this work for an arbitrary penalty function (like the one i use now)? (maybe when using selfAdd it by 1/l (l be the max length of the sequences). 1/l comes from the fact that i normally get -log(mult(prob))=sigma(-log(prob)) and i want it to be more near 1 when l is bugger so -log(mult(prob)*exp(-1/l))=sigma(-log(prob))+-(-1/l). ALSO, note that if i selfAdd this 1/l when I wanna use the result (in main.java) then it is not very good. better is if i selfAdd 1/l in the recursive process of calculating it, so if i want to match tw subsequences, I selfAdd 1/l (l be the max of the length of these subsequences).
// use caching in this function
public class SequenceDistanceCalculator {
    public static class Matching<T> {
        public List<Pair<T, T>> matching;
        public double score;
        public List<T> seq1;
        public List<T> seq2;

        public Matching(List<Pair<T, T>> matching, double score, List<T> seq1, List<T> seq2) {
            this.matching = matching;
            this.score = score;
            this.seq1 = seq1;
            this.seq2 = seq2;
        }

        @Override
        public String toString() {
            StringBuilder res = new StringBuilder("" + score + "\n");
            for (Pair<T, T> entry : matching)
                if (entry.getFirst() == null)
                    res.append("_".repeat(entry.getSecond().toString().length())).append("\t");
                else if (entry.getSecond() == null)
                    res.append(entry.getFirst()).append("\t");
                else
                    res.append(entry.getFirst()).append(" ".repeat(Integer.max(0, entry.getSecond().toString().length() - entry.getFirst().toString().length()))).append("\t");
            res.append("\n");
            for (Pair<T, T> entry : matching)
                if (entry.getSecond() == null)
                    res.append("_".repeat(entry.getFirst().toString().length())).append("\t");
                else if (entry.getFirst() == null)
                    res.append(entry.getSecond()).append("\t");
                else
                    res.append(entry.getSecond()).append(" ".repeat(Integer.max(0, entry.getFirst().toString().length() - entry.getSecond().toString().length()))).append("\t");
            return res.toString();
        }
    }

    // test this
    // use A* for better performance
    public static <T> Matching<T> calculate(List<T> seq1, List<T> seq2, BiFunction<T, T, Double> penalties) {
        double[][] scores = new double[seq1.size() + 1][seq2.size() + 1];
        for (int i = 0; i < scores.length; i++)
            for (int j = 0; j < scores[i].length; j++)
                scores[i][j] = -1;
        Pair<T, T>[][] matchings = new Pair[seq1.size() + 1][seq2.size() + 1];
        calculate(seq1, seq2, penalties, 0, 0, scores, matchings);
        List<Pair<T, T>> result = new ArrayList<>();
        int i1 = 0, i2 = 0;
        while (matchings[i1][i2] != null) {
            result.add(matchings[i1][i2]);
            int addI1 = 0;
            if (matchings[i1][i2].getFirst() != null)
                addI1 = 1;
            if (matchings[i1][i2].getSecond() != null)
                i2++;
            i1 += addI1;
        }

        return new Matching<T>(result, scores[0][0], seq1, seq2);
    }

    // test this
    private static <T> void calculate(List<T> seq1, List<T> seq2, BiFunction<T, T, Double> penalties,
                                      int i1, int i2, double[][] scores, Pair<T, T>[][] matchings) {
        if (scores[i1][i2] > -1)
            return;

        Pair<T, T> matching = null;
        double matchingScore = Double.POSITIVE_INFINITY;
        if (i1 < seq1.size() && i2 < seq2.size()) {
            double penalty = penalties.apply(seq1.get(i1), seq2.get(i2));
            if (penalty < Double.POSITIVE_INFINITY) {
                calculate(seq1, seq2, penalties, i1 + 1, i2 + 1, scores, matchings);
                if (scores[i1 + 1][i2 + 1] + penalty < matchingScore) {
                    matchingScore = penalty + scores[i1 + 1][i2 + 1];
                    matching = new Pair<T, T>(seq1.get(i1), seq2.get(i2));
                }
            }
        }
        if (i2 < seq2.size()) {
            double penalty = penalties.apply(null, seq2.get(i2));
            if (penalty < Double.POSITIVE_INFINITY) {
                calculate(seq1, seq2, penalties, i1, i2 + 1, scores, matchings);
                if (scores[i1][i2 + 1] + penalty < matchingScore) {
                    matchingScore = penalty + scores[i1][i2 + 1];
                    matching = new Pair<T, T>(null, seq2.get(i2));
                }
            }
        }
        if (i1 < seq1.size()) {
            double penalty = penalties.apply(seq1.get(i1), null);
            if (penalty < Double.POSITIVE_INFINITY) {
                calculate(seq1, seq2, penalties, i1 + 1, i2, scores, matchings);
                if (scores[i1 + 1][i2] + penalty < matchingScore) {
                    matchingScore = penalty + scores[i1 + 1][i2];
                    matching = new Pair<T, T>(seq1.get(i1), null);
                }
            }
        }
        if (matching == null && i1 >= seq1.size() && i2 >= seq2.size()) {
            scores[i1][i2] = 0;
            return;
        }
        scores[i1][i2] = matchingScore;
        matchings[i1][i2] = matching;
    }
}
