package com.bc.calvalus.wps.wpsoperations;

import static com.bc.wps.api.utils.WpsTypeConverter.str2CodeType;
import static com.bc.wps.api.utils.WpsTypeConverter.str2LanguageStringType;

import com.bc.calvalus.inventory.ProductSet;
import com.bc.calvalus.processing.ProcessorDescriptor;
import com.bc.calvalus.production.ProductionException;
import com.bc.calvalus.wps.calvalusfacade.CalvalusFacade;
import com.bc.calvalus.wps.calvalusfacade.CalvalusProcessor;
import com.bc.calvalus.wps.exceptions.InvalidProcessorIdException;
import com.bc.calvalus.wps.exceptions.ProcessesNotAvailableException;
import com.bc.calvalus.wps.exceptions.ProductSetsNotAvailableException;
import com.bc.calvalus.wps.calvalusfacade.IWpsProcess;
import com.bc.calvalus.wps.utils.ProcessorNameParser;
import com.bc.wps.api.WpsRequestContext;
import com.bc.wps.api.schema.CRSsType;
import com.bc.wps.api.schema.ComplexDataCombinationType;
import com.bc.wps.api.schema.ComplexDataCombinationsType;
import com.bc.wps.api.schema.ComplexDataDescriptionType;
import com.bc.wps.api.schema.InputDescriptionType;
import com.bc.wps.api.schema.OutputDescriptionType;
import com.bc.wps.api.schema.ProcessDescriptionType;
import com.bc.wps.api.schema.SupportedCRSsType;
import com.bc.wps.api.schema.SupportedComplexDataInputType;
import com.bc.wps.api.schema.SupportedComplexDataType;
import com.bc.wps.api.schema.ValueType;
import com.bc.wps.api.utils.InputDescriptionTypeBuilder;
import com.bc.wps.utilities.PropertiesWrapper;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author hans
 */
public class CalvalusDescribeProcessOperation {

    private WpsRequestContext context;

    public CalvalusDescribeProcessOperation(WpsRequestContext context) {
        this.context = context;
    }

    public List<ProcessDescriptionType> getProcesses(String processorId) throws ProcessesNotAvailableException {
        try {
            CalvalusFacade calvalusFacade = new CalvalusFacade(context);

            String[] processorIdArray = processorId.split(",");

            List<ProcessDescriptionType> processDescriptionTypeList = new ArrayList<>();
            if (processorId.equalsIgnoreCase("all")) {
                processDescriptionTypeList.addAll(getMultipleDescribeProcessResponse(calvalusFacade.getProcessors()));
            } else if (processorIdArray.length > 1) {
                List<IWpsProcess> processors = new ArrayList<>();
                for (String singleProcessorId : processorIdArray) {
                    ProcessorNameParser parser = new ProcessorNameParser(singleProcessorId);
                    CalvalusProcessor calvalusProcessor = calvalusFacade.getProcessor(parser);
                    if (calvalusProcessor == null) {
                        throw new ProcessesNotAvailableException("Unable to retrieve the selected process(es) " +
                                                                 "due to invalid process ID '" + singleProcessorId + "'");
                    }
                    processors.add(calvalusProcessor);
                }
                processDescriptionTypeList.addAll(getMultipleDescribeProcessResponse(processors));
            } else {
                ProcessorNameParser parser = new ProcessorNameParser(processorId);
                CalvalusProcessor calvalusProcessor = calvalusFacade.getProcessor(parser);
                if (calvalusProcessor == null) {
                    throw new ProcessesNotAvailableException("Unable to retrieve the selected process(es) " +
                                                             "due to invalid process ID '" + processorId + "'");
                }
                processDescriptionTypeList.add(getSingleDescribeProcessResponse(calvalusProcessor));
            }
            return processDescriptionTypeList;
        } catch (IOException | ProductionException | ProductSetsNotAvailableException | InvalidProcessorIdException exception) {
            throw new ProcessesNotAvailableException("Unable to retrieve the selected process(es)", exception);
        }
    }

    private List<ProcessDescriptionType> getMultipleDescribeProcessResponse(List<IWpsProcess> processes)
                throws ProductSetsNotAvailableException {
        List<ProcessDescriptionType> processDescriptionTypeList = new ArrayList<>();
        for (IWpsProcess process : processes) {
            ProcessDescriptionType processDescription = getSingleProcess(process);
            processDescriptionTypeList.add(processDescription);
        }
        return processDescriptionTypeList;
    }

    private ProcessDescriptionType getSingleDescribeProcessResponse(CalvalusProcessor calvalusProcessor)
                throws ProductSetsNotAvailableException {
        return getSingleProcess(calvalusProcessor);
    }

    private ProcessDescriptionType getSingleProcess(IWpsProcess process)
                throws ProductSetsNotAvailableException {
        ProductSet[] productSets;
        try {
            productSets = getProductSets();
        } catch (IOException | ProductionException exception) {
            throw new ProductSetsNotAvailableException("Unable to get available product sets", exception);
        }

        CalvalusProcessor calvalusProcessor = (CalvalusProcessor) process;
        ProcessDescriptionType processDescription = new ProcessDescriptionType();

        processDescription.setStoreSupported(true);
        processDescription.setStatusSupported(true);
        processDescription.setProcessVersion(calvalusProcessor.getVersion());

        processDescription.setIdentifier(str2CodeType(calvalusProcessor.getIdentifier()));
        processDescription.setTitle(str2LanguageStringType(calvalusProcessor.getIdentifier()));
        processDescription.setAbstract(str2LanguageStringType(calvalusProcessor.getAbstractText()));

        ProcessDescriptionType.DataInputs dataInputs = getDataInputs(calvalusProcessor, productSets);
        processDescription.setDataInputs(dataInputs);

        ProcessDescriptionType.ProcessOutputs dataOutputs = new ProcessDescriptionType.ProcessOutputs();
        OutputDescriptionType output = new OutputDescriptionType();

        SupportedComplexDataType supportedComplexDataType = new SupportedComplexDataType();
        ComplexDataCombinationType complexDataCombinationType = new ComplexDataCombinationType();
        ComplexDataDescriptionType complexDataDescriptionType = new ComplexDataDescriptionType();
        complexDataDescriptionType.setMimeType("application/octet-stream");
        complexDataCombinationType.setFormat(complexDataDescriptionType);
        supportedComplexDataType.setDefault(complexDataCombinationType);

        ComplexDataCombinationsType complexDataCombinationsType = new ComplexDataCombinationsType();
        complexDataCombinationsType.getFormat().add(complexDataDescriptionType);
        supportedComplexDataType.setSupported(complexDataCombinationsType);

        output.setComplexOutput(supportedComplexDataType);

        output.setIdentifier(str2CodeType("productionResults"));
        output.setTitle(str2LanguageStringType("URL to the production result(s)"));

        dataOutputs.getOutput().add(output);
        processDescription.setProcessOutputs(dataOutputs);
        return processDescription;
    }

    private ProductSet[] getProductSets() throws IOException, ProductionException {
        CalvalusFacade calvalusFacade = new CalvalusFacade(context);
        return calvalusFacade.getProductSets();
    }

    private ProcessDescriptionType.DataInputs getDataInputs(CalvalusProcessor calvalusProcessor, ProductSet[] productSets) {
        ProcessDescriptionType.DataInputs dataInputs = new ProcessDescriptionType.DataInputs();
        ProcessorDescriptor.ParameterDescriptor[] parameterDescriptors = calvalusProcessor.getParameterDescriptors();

        InputDescriptionType productionNameInput = InputDescriptionTypeBuilder
                    .create()
                    .withIdentifier("productionName")
                    .withTitle("Production name")
                    .withAbstract("The name of the product. When not specified, a random unique name is generated")
                    .withDataType("string")
                    .build();
        dataInputs.getInput().add(productionNameInput);

        List<Object> allowedProductionTypes = new ArrayList<>();
        ValueType allowedProductionType = new ValueType();
        allowedProductionType.setValue("L2Plus");
        allowedProductionTypes.add(allowedProductionType);

        InputDescriptionType productionType = InputDescriptionTypeBuilder
                    .create()
                    .withIdentifier("productionType")
                    .withTitle("Production type")
                    .withAbstract("The type of the requested product. When not specified, L2Plus type is used")
                    .withDataType("string")
                    .withAllowedValues(allowedProductionTypes)
                    .withDefaultValue("L2Plus")
                    .build();
        dataInputs.getInput().add(productionType);

        if (parameterDescriptors != null) {
            for (ProcessorDescriptor.ParameterDescriptor parameterDescriptor : parameterDescriptors) {
                InputDescriptionType input = InputDescriptionTypeBuilder
                            .create()
                            .withIdentifier(parameterDescriptor.getName())
                            .withTitle(parameterDescriptor.getName())
                            .withAbstract(parameterDescriptor.getDescription())
                            .withDefaultValue(parameterDescriptor.getDefaultValue())
                            .withDataType(parameterDescriptor.getType())
                            .build();

                dataInputs.getInput().add(input);
            }
        } else if (!StringUtils.isBlank(calvalusProcessor.getDefaultParameters())) {
            InputDescriptionType input = InputDescriptionTypeBuilder
                        .create()
                        .withIdentifier("processorParameters")
                        .withTitle("Processor parameters")
                        .withAbstract("Parameters specific to this processor")
                        .withDefaultValue(calvalusProcessor.getDefaultParameters())
                        .withDataType("string")
                        .build();

            dataInputs.getInput().add(input);
        }

        List<Object> allowedInputDataSets = new ArrayList<>();
        for (ProductSet productSet : productSets) {
            if (ArrayUtils.contains(calvalusProcessor.getInputProductTypes(), productSet.getProductType())) {
                ValueType value = new ValueType();
                value.setValue(productSet.getName());
                allowedInputDataSets.add(value);
            }
        }
        InputDescriptionTypeBuilder inputDataSetNameBuilder = InputDescriptionTypeBuilder
                    .create()
                    .withIdentifier("inputDataSetName")
                    .withTitle("Input data set name")
                    .withAbstract("The input dataset required for the processing")
                    .withDataType("string");
        if (!allowedInputDataSets.isEmpty()) {
            inputDataSetNameBuilder = inputDataSetNameBuilder.withAllowedValues(allowedInputDataSets);
        }
        InputDescriptionType inputDataSetName = inputDataSetNameBuilder.build();

        dataInputs.getInput().add(inputDataSetName);

        InputDescriptionType minDate = InputDescriptionTypeBuilder
                    .create()
                    .withIdentifier("minDate")
                    .withTitle("Date from")
                    .withAbstract("The desired start date of the product")
                    .withDataType("string")
                    .build();

        dataInputs.getInput().add(minDate);

        InputDescriptionType maxDate = InputDescriptionTypeBuilder
                    .create()
                    .withIdentifier("maxDate")
                    .withTitle("Date to")
                    .withAbstract("The desired end date of the product")
                    .withDataType("string")
                    .build();

        dataInputs.getInput().add(maxDate);

        InputDescriptionType periodLength = InputDescriptionTypeBuilder
                    .create()
                    .withIdentifier("periodLength")
                    .withTitle("Period length")
                    .withAbstract("The desired temporal range of the product")
                    .withDataType("string")
                    .build();

        dataInputs.getInput().add(periodLength);

        InputDescriptionType regionWkt = InputDescriptionTypeBuilder
                    .create()
                    .withIdentifier("regionWKT")
                    .withTitle("Region WKT")
                    .withAbstract("The spatial range in the format of text. Example: POLYGON((100 -10,100 0,110 0,110 -10,100 -10))")
                    .withDataType("string")
                    .build();

        dataInputs.getInput().add(regionWkt);

        InputDescriptionType regionBoundingBox = InputDescriptionTypeBuilder
                    .create()
                    .withIdentifier("regionWKT")
                    .withTitle("Region with bounding box")
                    .withAbstract("The spatial range in the format of bounding box. Use LowerCorner and UpperCorner (a pair of double values) " +
                                  "to specify the box. Example: <LowerCorner>100.74453 -10.0000</LowerCorner><UpperCorner>110.25000 0.12443</UpperCorner>")
                    .withDataType("string")
                    .build();

        SupportedCRSsType boundingBox = new SupportedCRSsType();
        SupportedCRSsType.Default defaultBoundingBox = new SupportedCRSsType.Default();
        defaultBoundingBox.setCRS("urn:ogc:def:crs:EPSG:6:6:4326");
        boundingBox.setDefault(defaultBoundingBox);
        CRSsType supportedBoundingBox = new CRSsType();
        supportedBoundingBox.getCRS().add("urn:ogc:def:crs:EPSG:6:6:4326");
        boundingBox.setSupported(supportedBoundingBox);
        regionBoundingBox.setBoundingBoxData(boundingBox);
        regionBoundingBox.setLiteralData(null);

        dataInputs.getInput().add(regionBoundingBox);

        InputDescriptionType l3ParametersComplexType = getL3ParametersComplexTypeWithSchema(
                    PropertiesWrapper.get("wps.l3.parameters.schema.location"));
        dataInputs.getInput().add(l3ParametersComplexType);

        List<String> allowedOutputFormat;
        if (calvalusProcessor.getPossibleOutputFormats() != null) {
            allowedOutputFormat = Arrays.asList(calvalusProcessor.getPossibleOutputFormats());
        } else {
            allowedOutputFormat = new ArrayList<>();
        }
        List<Object> allowedValues = new ArrayList<>();
        for (String outputFormat : allowedOutputFormat) {
            ValueType value = new ValueType();
            value.setValue(outputFormat);
            allowedValues.add(value);
        }
        InputDescriptionTypeBuilder calvalusOutputFormatBuilder = InputDescriptionTypeBuilder
                    .create()
                    .withIdentifier("outputFormat")
                    .withTitle("Output file format")
                    .withAbstract("The desired format of the product")
                    .withDataType("string");
        if (!allowedValues.isEmpty()) {
            calvalusOutputFormatBuilder = calvalusOutputFormatBuilder.withAllowedValues(allowedValues);
        }
        InputDescriptionType calvalusOutputFormat = calvalusOutputFormatBuilder.build();

        dataInputs.getInput().add(calvalusOutputFormat);

        return dataInputs;
    }

    private InputDescriptionType getL3ParametersComplexTypeWithSchema(String schemaUrl) {
        InputDescriptionType l3ParametersComplexType = new InputDescriptionType();

        l3ParametersComplexType.setMinOccurs(BigInteger.ZERO);
        l3ParametersComplexType.setMaxOccurs(BigInteger.ONE);
        l3ParametersComplexType.setIdentifier(str2CodeType("calvalus.l3.parameters"));
        l3ParametersComplexType.setTitle(str2LanguageStringType("Specific Calvalus parameters for L3 processing"));

        SupportedComplexDataInputType l3Parameters = new SupportedComplexDataInputType();
        ComplexDataCombinationType complexDataCombinationType = new ComplexDataCombinationType();
        ComplexDataDescriptionType complexDataDescriptionType = new ComplexDataDescriptionType();
        complexDataDescriptionType.setMimeType("application/xml");
        complexDataDescriptionType.setSchema(schemaUrl);
        complexDataCombinationType.setFormat(complexDataDescriptionType);
        l3Parameters.setDefault(complexDataCombinationType);

        ComplexDataCombinationsType complexDataCombinationsType = new ComplexDataCombinationsType();
        complexDataCombinationsType.getFormat().add(complexDataDescriptionType);
        l3Parameters.setSupported(complexDataCombinationsType);
        l3ParametersComplexType.setComplexData(l3Parameters);
        return l3ParametersComplexType;
    }
}
