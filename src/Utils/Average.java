//package Utils;
//
//public class Average {
//    private double value;
//    private double weight;
//
//    public Average() {
//        this(0, 0);
//    }
//
//    public Average(double value, double weight) {
//        this.value = value;
//        this.weight = weight;
//    }
//
//    public double getValue() {
//        return value;
//    }
//
//    public double getWeight() {
//        return weight;
//    }
//
//    public void setValue(double value) {
//        this.value = value;
//    }
//
//    public void setWeight(double weight) {
//        this.weight = weight;
//    }
//
//    public void selfAdd(Average average) {
//        value = (value * weight + average.value * average.weight) / (weight + average.weight);
//        weight += average.weight;
//    }
//
//    public Average add(Average average) {
//        Average result = new Average();
//        result.selfAdd(average);
//        return result;
//    }
//
//    public Average copy() {
//        return new Average(value, weight);
//    }
//}
