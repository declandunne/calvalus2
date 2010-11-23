package com.bc.calvalus.b3;

/**
 * An aggregator that computes an average.
 */
public final class AggregatorAverage implements Aggregator {
    private final int varIndex;
    private final String[] spatialPropertyNames;
    private final String[] temporalPropertyNames;
    private final String[] outputPropertyNames;

    public AggregatorAverage(VariableContext ctx, String varName) {
        varIndex = ctx.getVariableIndex(varName);
        spatialPropertyNames = new String[]{varName + "_sum_x", varName + "_sum_xx"};
        temporalPropertyNames = new String[]{varName + "_sum_x", varName + "_sum_xx", varName + "_sum_w"};
        outputPropertyNames = new String[]{varName + "_mean", varName + "_sigma"};
    }

    @Override
    public String getName() {
        return "AVG";
    }

    @Override
    public int getSpatialPropertyCount() {
        return 2;
    }

    @Override
    public String getSpatialPropertyName(int i) {
        return spatialPropertyNames[i];
    }

    @Override
    public int getTemporalPropertyCount() {
        return 3;
    }

    @Override
    public String getTemporalPropertyName(int i) {
        return temporalPropertyNames[i];
    }

    @Override
    public int getOutputPropertyCount() {
        return 2;
    }

    @Override
    public String getOutputPropertyName(int i) {
        return outputPropertyNames[i]; 
    }

    @Override
    public void initSpatial(WritableVector vector) {
    }

    @Override
    public void initTemporal(WritableVector vector) {
    }

    @Override
    public void aggregateSpatial(Vector observationVector, WritableVector spatialVector) {
        final float value = observationVector.get(varIndex);
        spatialVector.set(0, spatialVector.get(0) + value);
        spatialVector.set(1, spatialVector.get(1) + value * value);
    }

    @Override
    public void completeSpatial(WritableVector spatialVector, int numObs) {
        final float w = weight(numObs);
        spatialVector.set(0, spatialVector.get(0) / w);
        spatialVector.set(1, spatialVector.get(1) / w);
    }

    @Override
    public void aggregateTemporal(Vector spatialVector, int numSpatialObs, WritableVector temporalVector) {
        temporalVector.set(0, temporalVector.get(0) + spatialVector.get(0));
        temporalVector.set(1, temporalVector.get(1) + spatialVector.get(1));
        temporalVector.set(2, temporalVector.get(2) + weight(numSpatialObs));
    }

    @Override
    public void computeOutput(Vector temporalVector, WritableVector outputVector) {
        float sumX = temporalVector.get(0);
        float sumXX = temporalVector.get(1);
        float sumW = temporalVector.get(2);
        float mean = sumX / sumW;
        float sigma = (float) Math.sqrt(sumXX / sumW - mean * mean);
        outputVector.set(0, mean);
        outputVector.set(1, sigma);
    }

    private static float weight(int numObs) {
        return (float) Math.sqrt(numObs);
    }
}
