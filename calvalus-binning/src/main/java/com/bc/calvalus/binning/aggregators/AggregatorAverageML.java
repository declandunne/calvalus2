package com.bc.calvalus.binning.aggregators;

import com.bc.calvalus.binning.*;
import com.bc.ceres.binding.PropertyDescriptor;
import com.bc.ceres.binding.PropertySet;

import java.util.Arrays;

import static java.lang.Math.exp;
import static java.lang.Math.sqrt;

/**
 * An aggregator that computes a maximum-likelihood average.
 */
public class AggregatorAverageML extends AbstractAggregator {

    public final static double EPS = 0.000000001;

    private final int varIndex;
    private final WeightFn weightFn;

    public AggregatorAverageML(VariableContext ctx, String varName, Double weightCoeff, Float fillValue) {
        super(Descriptor.NAME,
              createFeatureNames(varName, "sum_x", "sum_xx"),
              createFeatureNames(varName, "sum_x", "sum_xx", "sum_w"),
              createFeatureNames(varName, "mean", "sigma", "median", "mode"),
              fillValue);
        this.varIndex = ctx.getVariableIndex(varName);
        this.weightFn = WeightFn.createPow(weightCoeff != null ? weightCoeff : 0.5);
    }

    @Override
    public void initSpatial(BinContext ctx, WritableVector vector) {
        vector.set(0, 0.0f);
        vector.set(1, 0.0f);
    }

    @Override
    public void initTemporal(BinContext ctx, WritableVector vector) {
        vector.set(0, 0.0f);
        vector.set(1, 0.0f);
        vector.set(2, 0.0f);
    }

    @Override
    public void aggregateSpatial(BinContext ctx, Vector observationVector, WritableVector spatialVector) {
        final double x = observationVector.get(varIndex);
        final double logX = Math.log(x > EPS ? x : EPS);
        spatialVector.set(0, spatialVector.get(0) + (float) logX);
        spatialVector.set(1, spatialVector.get(1) + (float) (logX * logX));
    }

    @Override
    public void completeSpatial(BinContext ctx, int numObs, WritableVector numSpatialObs) {
        final float w = weightFn.eval(numObs);
        numSpatialObs.set(0, numSpatialObs.get(0) / w);
        numSpatialObs.set(1, numSpatialObs.get(1) / w);
    }

    @Override
    public void aggregateTemporal(BinContext ctx, Vector spatialVector, int numSpatialObs, WritableVector temporalVector) {
        temporalVector.set(0, temporalVector.get(0) + spatialVector.get(0));  // sumX
        temporalVector.set(1, temporalVector.get(1) + spatialVector.get(1));  // sumXX
        temporalVector.set(2, temporalVector.get(2) + weightFn.eval(numSpatialObs)); // sumW
    }

    @Override
    public void completeTemporal(BinContext ctx, int numTemporalObs, WritableVector temporalVector) {
    }

    @Override
    public void computeOutput(Vector temporalVector, WritableVector outputVector) {
        final double sumX = temporalVector.get(0);
        final double sumXX = temporalVector.get(1);
        final double sumW = temporalVector.get(2);
        final double avLogs = sumX / sumW;
        final double vrLogs = sumXX / sumW - avLogs * avLogs;
        final double mean = exp(avLogs + 0.5 * vrLogs);
        final double expVrLogs = exp(vrLogs);
        final double sigma = mean * (expVrLogs > 1.0 ? sqrt(expVrLogs - 1.0) : 0.0);
        final double median = exp(avLogs);
        final double mode = exp(avLogs - vrLogs);
        outputVector.set(0, (float) mean);
        outputVector.set(1, (float) sigma);
        outputVector.set(2, (float) median);
        outputVector.set(3, (float) mode);
    }

    @Override
    public String toString() {
        return "AggregatorAverageML{" +
                "varIndex=" + varIndex +
                ", weightFn=" + weightFn +
                ", spatialFeatureNames=" + Arrays.toString(getSpatialFeatureNames()) +
                ", temporalFeatureNames=" + Arrays.toString(getTemporalFeatureNames()) +
                ", outputFeatureNames=" + Arrays.toString(getOutputFeatureNames()) +
                '}';
    }

    public static class Descriptor implements AggregatorDescriptor {

        public static final String NAME = "AVG_ML";

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public PropertyDescriptor[] getParameterDescriptors() {

            return new PropertyDescriptor[]{
                    new PropertyDescriptor("varName", String.class),
                    new PropertyDescriptor("weightCoeff", Double.class),
                    new PropertyDescriptor("fillValue", Float.class),
            };
        }

        @Override
        public Aggregator createAggregator(VariableContext varCtx, PropertySet propertySet) {
            return new AggregatorAverageML(varCtx,
                                           propertySet.<String>getValue("varName"),
                                           propertySet.<Double>getValue("weightCoeff"),
                                           propertySet.<Float>getValue("fillValue"));
        }
    }
}
