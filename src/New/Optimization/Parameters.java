package New.Optimization;

// one problem that happens in optimization is tat my algorithm may produce same cost function in small changes(the resolution of my algorithm(my algorithm=matching and patching algorithm) is not high)
public class Parameters {
    public static class NodeMatcher {
        public static Parameter parentWeight = new Parameter(1, 100); //2
        public static Parameter childWeight = new Parameter(1, 100); //3
        public static Parameter variableMatchWeight = new Parameter(1, 100); //3

        public static Parameter generalNull = new Parameter(0, .5); //.5
        public static Parameter generalNullStep = new Parameter(0, .5); //.07
        public static Parameter generalNullMin = new Parameter(0, .5); //.1

        public static Parameter branchNull = new Parameter(0, .5);
        public static Parameter loopNull = new Parameter(0, .5);
        public static Parameter blockNull = new Parameter(.5);
        public static Parameter valueNull = new Parameter(0, .5);
        public static Parameter asisgnmentNull = new Parameter(0, .5);
        public static Parameter methodCallNull = new Parameter(0, .5);
        public static Parameter compareOperatorNull = new Parameter(0, .5);
        public static Parameter booleanOperatorNull = new Parameter(0, .5);
        public static Parameter arithmeticOperatorNull = new Parameter(0, .5);
        public static Parameter breakNull = new Parameter(0, .5);
        public static Parameter continueNull = new Parameter(0, .5);
        public static Parameter argumentsBlockNull = new Parameter(.5);
    }

    public static class Parameter {
        private double value;
        private double minValue;
        private double maxValue;

        private Parameter(double minValue, double maxValue) {
            this.minValue = minValue;
            this.maxValue = maxValue;
        }

        private Parameter(double value) {
            this.value = value;
            maxValue = value;
            minValue = value;
        }

        public void setValue(double value) {
            this.value = Math.min(Math.max(value, minValue), maxValue);
        }

        public double getValue() {
            return value;
        }

        public double getMinValue() {
            return minValue;
        }

        public double getMaxValue() {
            return maxValue;
        }
    }
}
