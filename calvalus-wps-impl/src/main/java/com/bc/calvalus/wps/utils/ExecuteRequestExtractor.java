package com.bc.calvalus.wps.utils;

import com.bc.calvalus.wps.exceptions.WpsMissingParameterValueException;
import com.bc.wps.api.schema.DataInputsType;
import com.bc.wps.api.schema.Execute;
import com.bc.wps.api.schema.InputType;
import com.bc.wps.api.schema.L3Parameters;
import com.bc.wps.api.schema.ObjectFactory;
import com.bc.wps.utilities.JaxbHelper;
import org.apache.commons.lang.StringUtils;
import org.apache.xerces.dom.ElementNSImpl;
import org.w3c.dom.Node;

import javax.xml.bind.JAXBException;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * This class handles the transformation of the parameters on an Execute request into a format
 * that conforms to Calvalus Production.
 *
 * @author hans
 */
public class ExecuteRequestExtractor {

    private final Map<String, String> inputParametersMapRaw;

    public ExecuteRequestExtractor(Execute execute) throws JAXBException {
        inputParametersMapRaw = new HashMap<>();
        extractInputParameters(execute);
    }

    public Map<String, String> getInputParametersMapRaw() {
        return inputParametersMapRaw;
    }

    public String getValue(String parameterName) {
        return inputParametersMapRaw.get(parameterName);
    }

    private void extractInputParameters(Execute execute) throws JAXBException {
        DataInputsType dataInputs = execute.getDataInputs();
        for (InputType dataInput : dataInputs.getInput()) {
            if (dataInput.getIdentifier() == null) {
                continue;
            }
            if (!dataInput.getIdentifier().getValue().equals("calvalus.l3.parameters")) {
                String value = dataInput.getData().getLiteralData().getValue();
                if (StringUtils.isBlank(value)) {
                    throw new WpsMissingParameterValueException(dataInput.getIdentifier().getValue());
                }
                inputParametersMapRaw.put(dataInput.getIdentifier().getValue(), value);
            } else {
                ElementNSImpl elementNS = null;
                for (Object object : dataInput.getData().getComplexData().getContent()) {
                    if (object instanceof ElementNSImpl) {
                        elementNS = (ElementNSImpl) object;
                    }
                }
                InputStream l3ParametersStream = getL3ParametersStream(elementNS);
                L3Parameters l3Parameters = (L3Parameters) JaxbHelper.unmarshal(l3ParametersStream, new ObjectFactory());
                extractL3Parameters(l3Parameters);
            }
        }
    }

    private void extractL3Parameters(L3Parameters l3Parameters) {
        L3ParameterXmlGenerator xmlGenerator = new L3ParameterXmlGenerator(l3Parameters);
        inputParametersMapRaw.put("calvalus.l3.parameters", xmlGenerator.createXml());
    }

    private InputStream getL3ParametersStream(Node elementNS) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Source xmlSource = new DOMSource(elementNS);
        Result outputTarget = new StreamResult(outputStream);
        try {
            TransformerFactory.newInstance().newTransformer().transform(xmlSource, outputTarget);
        } catch (TransformerException e) {
            System.out.println("Error : " + e.getMessage());
        }
        return new ByteArrayInputStream(outputStream.toByteArray());
    }
}
