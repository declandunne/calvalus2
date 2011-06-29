package com.bc.calvalus.production;

import com.bc.ceres.core.Assert;
import org.esa.beam.framework.gpf.annotations.Parameter;

public class ProcessorDescriptor {

    public static class Variable {

        String name;
        String defaultAggregator;
        String defaultWeightCoeff;

        // empty constructor for XML serialization
        public Variable() {
        }

        public Variable(String name, String defaultAggregator, String defaultWeightCoeff) {
            this.name = name;
            this.defaultAggregator = defaultAggregator;
            this.defaultWeightCoeff = defaultWeightCoeff;
        }

        public String getName() {
            return name;
        }

        public String getDefaultAggregator() {
            return defaultAggregator;
        }

        public String getDefaultWeightCoeff() {
            return defaultWeightCoeff;
        }
    }

    @Parameter
    private String executableName;
    @Parameter
    private String processorName;
    @Parameter
    private String defaultParameter;
    @Parameter
    private String bundleName;
    @Parameter
    private String bundleVersion;
    @Parameter
    private String[] outputFormats;

    // Short description in XHTML
    @Parameter
    private String descriptionHtml;

    @Parameter
    private String outputVariableDefaultMaskExpr;

    @Deprecated
    @Parameter
    private String validMaskExpression;

    @Parameter(itemAlias = "outputVariable")
    private Variable[] outputVariables;

    @Parameter
    private String[] inputProductTypes;

    // empty constructor for XML serialization
    public ProcessorDescriptor() {
    }

    public ProcessorDescriptor(String executableName,
                               String processorName,
                               String defaultParameter,
                               String bundleName,
                               String bundleVersion,
                               Variable ... outputVariables) {

        Assert.notNull(executableName, "executableName");
        Assert.notNull(processorName, "processorName");
        Assert.notNull(defaultParameter, "defaultParameters");
        Assert.notNull(bundleName, "bundleName");
        Assert.notNull(bundleVersion, "bundleVersions");
        this.executableName = executableName;
        this.processorName = processorName;
        this.defaultParameter = defaultParameter;
        this.bundleName = bundleName;
        this.bundleVersion = bundleVersion;
        this.outputVariables = outputVariables;
    }

    public String getExecutableName() {
        return executableName;
    }

    public String getProcessorName() {
        return processorName;
    }

    public String getDefaultParameter() {
        return defaultParameter;
    }

    public String getBundleName() {
        return bundleName;
    }

    public String getBundleVersion() {
        return bundleVersion;
    }

    public String[] getOutputFormats() {
        return outputFormats;
    }

    public String getDescriptionHtml() {
        return descriptionHtml;
    }

    public String getMaskExpression() {
        return outputVariableDefaultMaskExpr != null ? outputVariableDefaultMaskExpr : validMaskExpression;
    }

    public Variable[] getOutputVariables() {
        return outputVariables;
    }

    public String[] getInputProductTypes() {
        return inputProductTypes;
    }
}
