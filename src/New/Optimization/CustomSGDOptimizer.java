package New.Optimization;

import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import New.Optimization.Parameters.Parameter;

public class CustomSGDOptimizer extends Optimizer {
    double lrRate;

    public CustomSGDOptimizer(Supplier<Double> costFunction, double lrRate) {
        super(costFunction);
        this.lrRate = lrRate;
    }

    @Override
    protected void changeParameters(double cost) {
        learnableParameters.entrySet().stream()
                .collect(Collectors.toMap(x -> x.getKey(),
                        x -> {
                            Parameter param = x.getValue();
                            double val = param.getValue();
                            double retVal = val;
                            double learningRate = (param.getMaxValue() - param.getMinValue()) / lrRate;
                            param.setValue(val + learningRate);
                            if (param.getValue() != val)
                                retVal = param.getValue() - Math.signum(costFunction.get() - cost) * learningRate;
                            else {
                                param.setValue(val - learningRate);
                                if (param.getValue() != val)
                                    retVal = param.getValue() + Math.signum(costFunction.get() - cost) * learningRate;
                            }
                            param.setValue(val);
                            return retVal;
                        }))
                .forEach((key, value) -> learnableParameters.get(key).setValue(value));
    }

    @Override
    protected double getInitValue(String name) {
        return new Random().nextDouble() *
                (learnableParameters.get(name).getMaxValue() - learnableParameters.get(name).getMinValue())
                + learnableParameters.get(name).getMinValue();
    }
}
