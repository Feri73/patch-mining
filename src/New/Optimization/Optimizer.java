package New.Optimization;

import New.Optimization.Parameters.Parameter;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public abstract class Optimizer {
    protected Map<String, Parameter> learnableParameters;
    protected Supplier<Double> costFunction;

    public Optimizer(Supplier<Double> costFunction) {
        learnableParameters = Arrays.stream(Parameters.class.getClasses())
                .filter(x -> !x.getSimpleName().equals("Parameter"))
                .flatMap(x -> Arrays.stream(x.getFields()))
                .collect(Collectors.toMap(x -> x.getName(), x -> {
                    try {
                        return (Parameter) x.get(null);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException();
                    }
                }));
        this.costFunction = costFunction;
    }

    public void optimize(int epochs) {
        initialize();
        for (int i = 0; i < epochs; i++)
            changeParameters(costFunction.get());
    }

    protected abstract void changeParameters(double cost);

    private void initialize() {
        learnableParameters.forEach((name, parameter) -> parameter.setValue(getInitValue(name)));
    }

    protected abstract double getInitValue(String name);
}
