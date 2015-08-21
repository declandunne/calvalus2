package com.bc.calvalus.wps2;

import com.bc.calvalus.wps2.jaxb.AggregatorConfig;
import com.bc.calvalus.wps2.jaxb.Aggregators;
import com.bc.calvalus.wps2.jaxb.L3Parameters;
import com.bc.calvalus.wps2.jaxb.VariableConfig;
import com.bc.calvalus.wps2.jaxb.Variables;
import org.apache.commons.lang.StringUtils;

/**
 * Created by hans on 19/08/2015.
 */
public class L3ParameterXmlGenerator {

    private final L3Parameters l3Parameters;
    private StringBuilder sb;

    public L3ParameterXmlGenerator(L3Parameters l3Parameters) {
        this.l3Parameters = l3Parameters;
        sb = new StringBuilder();
    }

    public String createXml() {
        sb.append("<parameters>\n");

        constructSingleElement("planetaryGrid", l3Parameters.getPlanetaryGrid());
        constructSingleElement("numRows", String.valueOf(l3Parameters.getNumRows()));
        constructSingleElement("compositingType", String.valueOf(l3Parameters.getCompositingType()));
        constructSingleElement("superSampling", String.valueOf(l3Parameters.getSuperSampling()));
        constructSingleElement("maskExpr", l3Parameters.getMaskExpr());
        constructSingleElement("minDataHour", String.valueOf(l3Parameters.getMinDataHour()));
        constructSingleElement("metadataAggregatorName", l3Parameters.getMetadataAggregatorName());
        constructSingleElement("startDateTime", l3Parameters.getStartDateTime());
        constructSingleElement("periodDuration", String.valueOf(l3Parameters.getPeriodDuration()));
        constructSingleElement("timeFilterMethod", String.valueOf(l3Parameters.getTimeFilterMethod()));
        constructSingleElement("outputFile", l3Parameters.getOutputFile());
        constructVariablesElement();
        constructAggregatorsElement();
        constructPostProcessorElement();

        sb.append("</parameters>");
        return sb.toString();
    }

    private void constructVariablesElement() {
        if (l3Parameters.getVariables() != null) {
            createOpeningTag("variables");
            Variables variables = l3Parameters.getVariables();
            for (VariableConfig variableConfig : variables.getVariable()) {
                createOpeningTag("variable");
                constructSingleElement("name", variableConfig.getName());
                constructSingleElement("expr", variableConfig.getExpr());
                createClosingTag("variables");
            }
            createClosingTag("variable");
        }
    }

    private void constructAggregatorsElement() {
        if (l3Parameters.getAggregators() != null) {
            createOpeningTag("aggregators");
            Aggregators aggregators = l3Parameters.getAggregators();
            for (AggregatorConfig aggregatorConfig : aggregators.getAggregator()) {
                createOpeningTag("aggregator");
                constructSingleElement("type", aggregatorConfig.getType());
                constructSingleElement("varName", aggregatorConfig.getVarName());
                createClosingTag("aggregator");
            }
            createClosingTag("aggregators");
        }
    }

    private void constructPostProcessorElement() {
        if (l3Parameters.getPostProcessorConfig() != null) {
            createOpeningTag("postProcessor");
            constructSingleElement("type", l3Parameters.getPostProcessorConfig().getType());
            constructSingleElement("varName", l3Parameters.getPostProcessorConfig().getVarName());
            createClosingTag("postProcessor");
        }
    }


    private void constructSingleElement(String elementName, String elementValue) {
        if (StringUtils.isNotBlank(elementValue) && !elementValue.equals("null")) {
            createOpeningTag(elementName);
            sb.append(elementValue);
            createClosingTag(elementName);
        }
    }

    private void createOpeningTag(String elementName) {
        sb.append("<");
        sb.append(elementName);
        sb.append(">");
    }

    private void createClosingTag(String elementName) {
        sb.append("</");
        sb.append(elementName);
        sb.append(">\n");
    }
}
