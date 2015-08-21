package com.bc.calvalus.wps2;

import static org.mockito.Mockito.*;

import com.bc.calvalus.wps2.jaxb.AcceptVersionsType;
import com.bc.calvalus.wps2.jaxb.GetCapabilities;
import org.junit.*;
import org.mockito.Matchers;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXB;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringReader;

/**
 * Created by hans on 12/08/2015.
 */
public class ServletExampleTest {

    private HttpServletRequest mockWpsRequest;
    private HttpServletResponse mockWpsResponse;

    @Test
    public void testUnmarshaller() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                     + "<wps:GetCapabilities service=\"WPS\" xmlns:wps=\"http://www.opengis.net/wps/1.0.0\" xmlns:ows=\"http://www.opengis.net/ows/1.1\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.opengis.net/wps/1.0.0\n"
                     + "http://schemas.opengis.net/wps/1.0.0/wpsGetCapabilities_request.xsd\" language=\"en\">\n"
                     + "<wps:AcceptVersions>\n"
                     + "\t\t<ows:Version>1.0.0</ows:Version>\n"
                     + "\t\t<ows:Version>2.0.0</ows:Version>\t\t\n"
                     + "\t\t<ows:Version>1.1.0</ows:Version>\n"
                     + "\t</wps:AcceptVersions>"
                     + "</wps:GetCapabilities>";

        StringReader stringReader = new StringReader(xml);
        GetCapabilities getCapabilities = JAXB.unmarshal(stringReader, GetCapabilities.class);
        AcceptVersionsType acceptVersionsType = getCapabilities.getAcceptVersions();
        System.out.println(acceptVersionsType.getVersion().size());
        System.out.println(getCapabilities.getLanguage());
    }

    @Test
    public void testMarshaller() throws Exception {
        GetCapabilities getCapabilities = new GetCapabilities();
        getCapabilities.setLanguage("DE");
        getCapabilities.setService("TEST");
        AcceptVersionsType acceptVersionsType = new AcceptVersionsType();
        acceptVersionsType.getVersion().add(0, "v1");
        acceptVersionsType.getVersion().add(1, "v2");
        getCapabilities.setAcceptVersions(acceptVersionsType);

        JAXBContext jaxbContext = JAXBContext.newInstance(GetCapabilities.class);
        Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
        jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        jaxbMarshaller.setProperty(Marshaller.JAXB_SCHEMA_LOCATION, "http://www.opengis.net/wps/1.0.0");
        jaxbMarshaller.marshal(getCapabilities, System.out);
    }

    @Ignore
    @Test
    public void testExecute() throws Exception {
        mockWpsRequest = mock(HttpServletRequest.class);
        mockWpsResponse = mock(HttpServletResponse.class);

        PrintWriter mockPrintWriter = mock(PrintWriter.class);
        when(mockWpsResponse.getWriter()).thenReturn(mockPrintWriter);
        String requestXml = getRequestXml();
//        ServletInputStream mockRequestInputStream = mock(ServletInputStream.class);
        InputStream mockRequestInputStream = new ByteArrayInputStream(requestXml.getBytes());
//        when(mockRequestInputStream.read(Matchers.<byte[]>any(), anyInt(), anyInt())).thenAnswer(new Answer<Integer>() {
//            @Override
//            public Integer answer(InvocationOnMock invocationOnMock) throws Throwable {
//                Object[] args = invocationOnMock.getArguments();
//                byte[] output = (byte[]) args[0];
//                int offset = (int) args[1];
//                int length = (int) args[2];
//                return byteArrayInputStream.read(output, offset, length);
//            }
//        });

//        when(mockWpsRequest.getInputStream()).thenReturn(mockRequestInputStream);
        when(mockWpsRequest.getParameter("request")).thenReturn("Execute");
        when(mockWpsRequest.getParameter("processor")).thenReturn("beam-idepix~2.0.9~Idepix.Water");

        ServletExample servletExample = new ServletExample();
        servletExample.processExecuteRequest("", mockRequestInputStream, mockPrintWriter);

    }

    private String getRequestXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\" ?>\n" +
               "\n" +
               "<wps:Execute service=\"WPS\"\n" +
               "             version=\"1.0.0\"\n" +
               "             xmlns:wps=\"http://www.opengis.net/wps/1.0.0\"\n" +
               "             xmlns:ows=\"http://www.opengis.net/ows/1.1\"\n" +
               "\t\t\t xmlns:cal=\"http://bc-schema.xsd\"\n" +
               "             xmlns:xlink=\"http://www.w3.org/1999/xlink\">\n" +
               "\n" +
               "\t<ows:Identifier>Calvalus</ows:Identifier>\n" +
               "\n" +
               "\t<wps:DataInputs>\n" +
               "\t\t<wps:Input>\n" +
               "\t\t\t<ows:Identifier>productionType</ows:Identifier>\n" +
               "\t\t\t<wps:Data>\n" +
               "\t\t\t\t<wps:LiteralData>L3</wps:LiteralData>\n" +
               "\t\t\t</wps:Data>\n" +
               "\t\t</wps:Input>\n" +
               "\t\t<wps:Input>\n" +
               "\t\t\t<ows:Identifier>calvalus.calvalus.bundle</ows:Identifier>\n" +
               "\t\t\t<wps:Data>\n" +
               "\t\t\t\t<wps:LiteralData>calvalus-2.0b411</wps:LiteralData>\n" +
               "\t\t\t</wps:Data>\n" +
               "\t\t</wps:Input>\n" +
               "\t\t<wps:Input>\n" +
               "\t\t\t<ows:Identifier>calvalus.beam.bundle</ows:Identifier>\n" +
               "\t\t\t<wps:Data>\n" +
               "\t\t\t\t<wps:LiteralData>beam-4.11.1-SNAPSHOT</wps:LiteralData>\n" +
               "\t\t\t</wps:Data>\n" +
               "\t\t</wps:Input>\n" +
               "\t\t<wps:Input>\n" +
               "\t\t\t<ows:Identifier>productionName</ows:Identifier>\n" +
               "\t\t\t<wps:Data>\n" +
               "\t\t\t\t<wps:LiteralData>Ocean Colour test</wps:LiteralData>\n" +
               "\t\t\t</wps:Data>\n" +
               "\t\t</wps:Input>\n" +
               "\t\t<wps:Input>\n" +
               "\t\t\t<ows:Identifier>processorBundleName</ows:Identifier>\n" +
               "\t\t\t<wps:Data>\n" +
               "\t\t\t\t<wps:LiteralData>case2-regional</wps:LiteralData>\n" +
               "\t\t\t</wps:Data>\n" +
               "\t\t</wps:Input>\n" +
               "\t\t<wps:Input>\n" +
               "\t\t\t<ows:Identifier>processorBundleVersion</ows:Identifier>\n" +
               "\t\t\t<wps:Data>\n" +
               "\t\t\t\t<wps:LiteralData>1.5.3</wps:LiteralData>\n" +
               "\t\t\t</wps:Data>\n" +
               "\t\t</wps:Input>\n" +
               "\t\t<wps:Input>\n" +
               "\t\t\t<ows:Identifier>processorName</ows:Identifier>\n" +
               "\t\t\t<wps:Data>\n" +
               "\t\t\t\t<wps:LiteralData>Meris.Case2Regional</wps:LiteralData>\n" +
               "\t\t\t</wps:Data>\n" +
               "\t\t</wps:Input>\n" +
               "\t\t<wps:Input>\n" +
               "\t\t\t<ows:Identifier>doAtmosphericCorrection</ows:Identifier>\n" +
               "\t\t\t<wps:Data>\n" +
               "\t\t\t\t<wps:LiteralData>true</wps:LiteralData>\n" +
               "\t\t\t</wps:Data>\n" +
               "\t\t</wps:Input>\n" +
               "\t\t<wps:Input>\n" +
               "\t\t\t<ows:Identifier>doSmileCorrection</ows:Identifier>\n" +
               "\t\t\t<wps:Data>\n" +
               "\t\t\t\t<wps:LiteralData>true</wps:LiteralData>\n" +
               "\t\t\t</wps:Data>\n" +
               "\t\t</wps:Input>\n" +
               "\t\t<wps:Input>\n" +
               "\t\t\t<ows:Identifier>outputTosa</ows:Identifier>\n" +
               "\t\t\t<wps:Data>\n" +
               "\t\t\t\t<wps:LiteralData>false</wps:LiteralData>\n" +
               "\t\t\t</wps:Data>\n" +
               "\t\t</wps:Input>\n" +
               "\t\t<wps:Input>\n" +
               "\t\t\t<ows:Identifier>outputReflec</ows:Identifier>\n" +
               "\t\t\t<wps:Data>\n" +
               "\t\t\t\t<wps:LiteralData>true</wps:LiteralData>\n" +
               "\t\t\t</wps:Data>\n" +
               "\t\t</wps:Input>\n" +
               "\t\t<wps:Input>\n" +
               "\t\t\t<ows:Identifier>outputReflecAs</ows:Identifier>\n" +
               "\t\t\t<wps:Data>\n" +
               "\t\t\t\t<wps:LiteralData>RADIANCE_REFLECTANCES</wps:LiteralData>\n" +
               "\t\t\t</wps:Data>\n" +
               "\t\t</wps:Input>\n" +
               "\t\t<wps:Input>\n" +
               "\t\t\t<ows:Identifier>outputPath</ows:Identifier>\n" +
               "\t\t\t<wps:Data>\n" +
               "\t\t\t\t<wps:LiteralData>true</wps:LiteralData>\n" +
               "\t\t\t</wps:Data>\n" +
               "\t\t</wps:Input>\n" +
               "\t\t<wps:Input>\n" +
               "\t\t\t<ows:Identifier>outputTransmittance</ows:Identifier>\n" +
               "\t\t\t<wps:Data>\n" +
               "\t\t\t\t<wps:LiteralData>false</wps:LiteralData>\n" +
               "\t\t\t</wps:Data>\n" +
               "\t\t</wps:Input>\n" +
               "\t\t<wps:Input>\n" +
               "\t\t\t<ows:Identifier>outputNormReflec</ows:Identifier>\n" +
               "\t\t\t<wps:Data>\n" +
               "\t\t\t\t<wps:LiteralData>false</wps:LiteralData>\n" +
               "\t\t\t</wps:Data>\n" +
               "\t\t</wps:Input>\n" +
               "\t\t<wps:Input>\n" +
               "\t\t\t<ows:Identifier>landExpression</ows:Identifier>\n" +
               "\t\t\t<wps:Data>\n" +
               "\t\t\t\t<wps:LiteralData>toa_reflec_10 &gt; toa_reflec_6 AND toa_reflec_13 &gt; 0.0475</wps:LiteralData>\n" +
               "\t\t\t</wps:Data>\n" +
               "\t\t</wps:Input>\n" +
               "\t\t<wps:Input>\n" +
               "\t\t\t<ows:Identifier>cloudIceExpression</ows:Identifier>\n" +
               "\t\t\t<wps:Data>\n" +
               "\t\t\t\t<wps:LiteralData>toa_reflec_14 &gt; 0.2</wps:LiteralData>\n" +
               "\t\t\t</wps:Data>\n" +
               "\t\t</wps:Input>\n" +
               "\t\t<wps:Input>\n" +
               "\t\t\t<ows:Identifier>algorithm</ows:Identifier>\n" +
               "\t\t\t<wps:Data>\n" +
               "\t\t\t\t<wps:LiteralData>REGIONAL</wps:LiteralData>\n" +
               "\t\t\t</wps:Data>\n" +
               "\t\t</wps:Input>\n" +
               "\t\t<wps:Input>\n" +
               "\t\t\t<ows:Identifier>tsmConversionExponent</ows:Identifier>\n" +
               "\t\t\t<wps:Data>\n" +
               "\t\t\t\t<wps:LiteralData>1.0</wps:LiteralData>\n" +
               "\t\t\t</wps:Data>\n" +
               "\t\t</wps:Input>\n" +
               "\t\t<wps:Input>\n" +
               "\t\t\t<ows:Identifier>tsmConversionFactor</ows:Identifier>\n" +
               "\t\t\t<wps:Data>\n" +
               "\t\t\t\t<wps:LiteralData>1.73</wps:LiteralData>\n" +
               "\t\t\t</wps:Data>\n" +
               "\t\t</wps:Input>\n" +
               "\t\t<wps:Input>\n" +
               "\t\t\t<ows:Identifier>chlConversionExponent</ows:Identifier>\n" +
               "\t\t\t<wps:Data>\n" +
               "\t\t\t\t<wps:LiteralData>1.04</wps:LiteralData>\n" +
               "\t\t\t</wps:Data>\n" +
               "\t\t</wps:Input>\n" +
               "\t\t<wps:Input>\n" +
               "\t\t\t<ows:Identifier>chlConversionFactor</ows:Identifier>\n" +
               "\t\t\t<wps:Data>\n" +
               "\t\t\t\t<wps:LiteralData>21.0</wps:LiteralData>\n" +
               "\t\t\t</wps:Data>\n" +
               "\t\t</wps:Input>\n" +
               "\t\t<wps:Input>\n" +
               "\t\t\t<ows:Identifier>spectrumOutOfScopeThreshold</ows:Identifier>\n" +
               "\t\t\t<wps:Data>\n" +
               "\t\t\t\t<wps:LiteralData>4.0</wps:LiteralData>\n" +
               "\t\t\t</wps:Data>\n" +
               "\t\t</wps:Input>\n" +
               "\t\t<wps:Input>\n" +
               "\t\t\t<ows:Identifier>invalidPixelExpression</ows:Identifier>\n" +
               "\t\t\t<wps:Data>\n" +
               "\t\t\t\t<wps:LiteralData>agc_flags.INVALID</wps:LiteralData>\n" +
               "\t\t\t</wps:Data>\n" +
               "\t\t</wps:Input>\n" +
               "\t\t<wps:Input>\n" +
               "\t\t\t<ows:Identifier>inputPath</ows:Identifier>\n" +
               "\t\t\t<wps:Data>\n" +
               "\t\t\t\t<wps:LiteralData>/calvalus/eodata/MER_RR__1P/r03/${yyyy}/${MM}/${dd}/MER_RR__1P....${yyyy}${MM}${dd}.*N1$</wps:LiteralData>\n" +
               "\t\t\t</wps:Data>\n" +
               "\t\t</wps:Input>\n" +
               "\t\t<wps:Input>\n" +
               "\t\t\t<ows:Identifier>minDate</ows:Identifier>\n" +
               "\t\t\t<wps:Data>\n" +
               "\t\t\t\t<wps:LiteralData>2009-06-01</wps:LiteralData>\n" +
               "\t\t\t</wps:Data>\n" +
               "\t\t</wps:Input>\n" +
               "\t\t<wps:Input>\n" +
               "\t\t\t<ows:Identifier>maxDate</ows:Identifier>\n" +
               "\t\t\t<wps:Data>\n" +
               "\t\t\t\t<wps:LiteralData>2009-06-30</wps:LiteralData>\n" +
               "\t\t\t</wps:Data>\n" +
               "\t\t</wps:Input>\n" +
               "\t\t<wps:Input>\n" +
               "\t\t\t<ows:Identifier>periodLength</ows:Identifier>\n" +
               "\t\t\t<wps:Data>\n" +
               "\t\t\t\t<wps:LiteralData>30</wps:LiteralData>\n" +
               "\t\t\t</wps:Data>\n" +
               "\t\t</wps:Input>\n" +
               "\t\t<wps:Input>\n" +
               "\t\t\t<ows:Identifier>regionWKT</ows:Identifier>\n" +
               "\t\t\t<wps:Data>\n" +
               "\t\t\t\t<wps:LiteralData>polygon((10.00 54.00,  14.27 53.47,  20.00 54.00, 21.68 54.77, 22.00 56.70, 24.84 56.70, 30.86 60.01, 26.00 62.00, 26.00 66.00, 22.00 66.00, 10.00 60.00, 10.00 54.00))</wps:LiteralData>\n" +
               "\t\t\t</wps:Data>\n" +
               "\t\t</wps:Input>\n" +
               "\n" +
               "\t\t<wps:Input>\n" +
               "\t\t\t<ows:Identifier>calvalus.l3.parameters</ows:Identifier>\n" +
               "\t\t\t<wps:Data>\n" +
               "\t\t\t\t<wps:ComplexData>\n" +
               "\t\t\t\t\t<cal:parameters>\n" +
               "\t\t\t\t\t\t<cal:compositingType>MOSAICKING</cal:compositingType>\n" +
               "\t\t\t\t\t\t<cal:planetaryGrid>org.esa.beam.binning.support.PlateCarreeGrid</cal:planetaryGrid>\n" +
               "\t\t\t\t\t\t<cal:numRows>21600</cal:numRows>\n" +
               "\t\t\t\t\t\t<cal:superSampling>1</cal:superSampling>\n" +
               "\t\t\t\t\t\t<cal:maskExpr>!case2_flags.INVALID</cal:maskExpr>\n" +
               "\t\t\t\t\t\t<cal:aggregators>\n" +
               "\t\t\t\t\t\t\t<cal:aggregator>\n" +
               "\t\t\t\t\t\t\t\t<cal:type>AVG</cal:type>\n" +
               "\t\t\t\t\t\t\t\t<cal:varName>tsm</cal:varName>\n" +
               "\t\t\t\t\t\t\t</cal:aggregator>\n" +
               "\t\t\t\t\t\t\t<cal:aggregator>\n" +
               "\t\t\t\t\t\t\t\t<cal:type>MIN_MAX</cal:type>\n" +
               "\t\t\t\t\t\t\t\t<cal:varName>chl_conc</cal:varName>\n" +
               "\t\t\t\t\t\t\t</cal:aggregator>\n" +
               "\t\t\t\t\t\t\t<cal:aggregator>\n" +
               "\t\t\t\t\t\t\t\t<cal:type>AVG</cal:type>\n" +
               "\t\t\t\t\t\t\t\t<cal:varName>Z90_max</cal:varName>\n" +
               "\t\t\t\t\t\t\t</cal:aggregator>\n" +
               "\t\t\t\t\t\t</cal:aggregators>\n" +
               "\t\t\t\t\t</cal:parameters>\n" +
               "\t\t\t\t</wps:ComplexData>\n" +
               "\t\t\t</wps:Data>\n" +
               "\t\t</wps:Input>\n" +
               "\n" +
               "\t\t<wps:Input>\n" +
               "\t\t\t<ows:Identifier>calvalus.output.dir</ows:Identifier>\n" +
               "\t\t\t<wps:Data>\n" +
               "\t\t\t\t<wps:LiteralData>/calvalus/home/hans/ocean-colour-test</wps:LiteralData>\n" +
               "\t\t\t</wps:Data>\n" +
               "\t\t</wps:Input>\n" +
               "\t\t<wps:Input>\n" +
               "\t\t\t<ows:Identifier>calvalus.output.format</ows:Identifier>\n" +
               "\t\t\t<wps:Data>\n" +
               "\t\t\t\t<wps:LiteralData>NetCDF4</wps:LiteralData>\n" +
               "\t\t\t</wps:Data>\n" +
               "\t\t</wps:Input>\n" +
               "\n" +
               "\t\t<wps:Input>\n" +
               "\t\t\t<ows:Identifier>calvalus.system.beam.pixelGeoCoding.useTiling</ows:Identifier>\n" +
               "\t\t\t<wps:Data>\n" +
               "\t\t\t\t<wps:LiteralData>true</wps:LiteralData>\n" +
               "\t\t\t</wps:Data>\n" +
               "\t\t</wps:Input>\n" +
               "\t\t<wps:Input>\n" +
               "\t\t\t<ows:Identifier>calvalus.hadoop.mapreduce.job.queuename</ows:Identifier>\n" +
               "\t\t\t<wps:Data>\n" +
               "\t\t\t\t<wps:LiteralData>test</wps:LiteralData>\n" +
               "\t\t\t</wps:Data>\n" +
               "\t\t</wps:Input>     \n" +
               "\t\t<wps:Input>\n" +
               "\t\t\t<ows:Identifier>calvalus.hadoop.mapreduce.map.maxattempts</ows:Identifier>\n" +
               "\t\t\t<wps:Data>\n" +
               "\t\t\t\t<wps:LiteralData>1</wps:LiteralData>\n" +
               "\t\t\t</wps:Data>\n" +
               "\t\t</wps:Input>\n" +
               "\t\t<wps:Input>\n" +
               "\t\t\t<ows:Identifier>autoStaging</ows:Identifier>\n" +
               "\t\t\t<wps:Data>\n" +
               "\t\t\t\t<wps:LiteralData>true</wps:LiteralData>\n" +
               "\t\t\t</wps:Data>\n" +
               "\t\t</wps:Input>\n" +
               "\t</wps:DataInputs>\n" +
               "\t<wps:ResponseForm>\n" +
               "\t\t<wps:ResponseDocument storeExecuteResponse=\"true\" status=\"true\">\n" +
               "\t\t\t<wps:Output>\n" +
               "\t\t\t\t<ows:Identifier>productionResults</ows:Identifier>\n" +
               "\t\t\t</wps:Output>\n" +
               "\t\t</wps:ResponseDocument>\n" +
               "\t</wps:ResponseForm>\n" +
               "\n" +
               "</wps:Execute>\n";
    }
}