/*
 * Copyright (C) 2011 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package com.bc.calvalus.production;


import com.bc.calvalus.processing.beam.BeamUtils;
import org.esa.beam.util.io.FileUtils;
import org.junit.Test;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStreamReader;

public class ProcessorDescriptorTest {

    @Test
    public void testConstructFromXML() throws Exception {
        String xml = getResourceAsString("test1-processor-descriptor.xml");
        ProcessorDescriptor processorDescriptor = new ProcessorDescriptor();

        assertNull(processorDescriptor.getExecutableName());

        BeamUtils.convertXmlToObject(xml, processorDescriptor);

        assertNotNull(processorDescriptor.getExecutableName());
        assertEquals("CoastColour.L2W", processorDescriptor.getExecutableName());
        assertEquals("MERIS CoastColour", processorDescriptor.getProcessorName());
        assertEquals("coastcolour-processing", processorDescriptor.getBundleName());
        assertEquals("0.5-SNAPSHOT", processorDescriptor.getBundleVersion());

        String defaultParameter = processorDescriptor.getDefaultParameter().trim();
        String expectedParameter = "<parameters>\n" +
                "    <useIdepix>false</useIdepix>\n" +
                "    <landExpression>l1_flags.LAND_OCEAN</landExpression>\n" +
                "    <outputReflec>false</outputReflec>\n" +
                "</parameters>";
        assertEquals(expectedParameter, defaultParameter);

        String[] outputFormats = processorDescriptor.getOutputFormats();
        assertNotNull(outputFormats);
        assertEquals(3, outputFormats.length);
        assertEquals("BEAM-DIMAP", outputFormats[0]);
        assertEquals("NetCDF", outputFormats[1]);
        assertEquals("GeoTIFF", outputFormats[2]);

        String descriptionHtml = processorDescriptor.getDescriptionHtml().trim();
        String expectedDescription = "<p>\n" +
                "<h1>This is a cool processor</h1>\n" +
                "done by the cool folks at Brockmann-Consult.\n" +
                "</p>";
        assertEquals(expectedDescription, descriptionHtml);

        assertNotNull(processorDescriptor.getMaskExpression());
        assertEquals("!l1_flags.INVALID", processorDescriptor.getMaskExpression());

        String[] inputProductTypes = processorDescriptor.getInputProductTypes();
        assertNotNull(inputProductTypes);
        assertEquals(3, inputProductTypes.length);
        assertEquals("MER_RR__1P", inputProductTypes[0]);
        assertEquals("MER_FR__1P", inputProductTypes[1]);
        assertEquals("MER_FSG_1P", inputProductTypes[2]);

        ProcessorDescriptor.Variable[] outputVariables = processorDescriptor.getOutputVariables();
        assertNotNull(outputVariables);
        assertEquals(2, outputVariables.length);

        assertEquals("l1_flags", outputVariables[0].getName());
        assertNull(outputVariables[0].getDefaultAggregator());

        assertNull(outputVariables[0].getDefaultWeightCoeff());

        assertEquals("chl_conc", outputVariables[1].getName());
        assertEquals("AVG_ML", outputVariables[1].getDefaultAggregator());
        assertEquals("0.5", outputVariables[1].getDefaultWeightCoeff());
    }

    private String getResourceAsString(String name) throws IOException {
        InputStreamReader inputStreamReader = new InputStreamReader(getClass().getResourceAsStream(name));
        try {
            String text = FileUtils.readText(inputStreamReader);
            return text.trim();
        } finally {
            inputStreamReader.close();
        }
    }

}
