/*
 * Copyright (C) 2016 Brockmann Consult GmbH (info@brockmann-consult.de)
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

package com.bc.calvalus.sentinel2;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;


/**
 * Splits a S2 product into individual granules
 * <p>
 * Parent-Product:
 * S2A_OPER_PRD_MSIL1C_PDMC_20160215T121255_R093_V20160214T093550_20160214T093550.SAFE/
 * - S2A_OPER_MTD_SAFL1C_PDMC_20160215T121255_R093_V20160214T093550_20160214T093550.xml
 * - GRANULE/
 * - - S2A_OPER_MSI_L1C_TL_SGS__20160214T150139_A003379_T33PVP_N02.01/
 * - - - S2A_OPER_MTD_L1C_TL_SGS__20160214T150139_A003379_T33PVP.xml
 * <p>
 * Single-Granule-Product:
 * S2A_OPER_PRD_MSIL1C_PDMC_20160214T150139_R093_V20160214T093550_20160214T093550_T33PVP.SAFE/
 * - S2A_OPER_MTD_SAFL1C_PDMC_20160214T150139_R093_V20160214T093550_20160214T093550_T33PVP.xml
 * - GRANULE/
 * - - S2A_OPER_MSI_L1C_TL_SGS__20160214T150139_A003379_T33PVP_N02.01/
 * - - - S2A_OPER_MTD_L1C_TL_SGS__20160214T150139_A003379_T33PVP.xml
 * <p>
 * Changes for single granule products are:
 * - the processing time of the product is replaced by the processing time of the granule
 * - the tile identifier is added to the new product name
 * - a history.txt file is added
 */
public class GranuleSplitter {

    private static final boolean ADD_ZIP_SUFFIX = true;
    private static final boolean COMPACT_FORMAT = true;

    static Map<String,ZipEntry> entryMap = new HashMap<>();

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("usage: GranuleSplitter <inputFile|-> <outputDir>");
            System.exit(1);
        }

        InputStream inputStream;
        if (args[0].equals("-")) {
            inputStream = System.in;
        } else {
            File inputFile = new File(args[0]);
            if (inputFile.exists()) {
                if (inputFile.canRead()) {
                    try (ZipFile zipFile = new ZipFile(inputFile)) {
                        Enumeration<? extends ZipEntry> entries = zipFile.entries();
                        while (entries.hasMoreElements()) {
                            ZipEntry entry = entries.nextElement();
                            entryMap.put(entry.getName(), entry);
                        }
                    }
                    inputStream = new FileInputStream(inputFile);
                } else {
                    throw new IllegalArgumentException("Can not read input file: " + inputFile.getAbsolutePath());
                }
            } else {
                throw new IllegalArgumentException("Input file does not exist: " + inputFile.getAbsolutePath());
            }
        }


        File outputDir = new File(args[1]);
        outputDir.mkdirs();
        new GranuleSplitter(inputStream, outputDir).run();
    }

    private static final int BUFFER_SIZE = 8192;
    private static final Pattern TILE_PATTERN = Pattern.compile(".*/GRANULE/.*(_T[0-9]{2}[A-Z]{3}).*");
    private static final Pattern BASELINE_PATTERN = Pattern.compile(".*/GRANULE/.*_T[0-9]{2}[A-Z]{3}_N(..).(..).*");

    private final InputStream inputStream;
    private final File outputDir;

    private GranuleSplitter(InputStream inputStream, File outputDir) {
        this.inputStream = inputStream;
        this.outputDir = outputDir;
    }

    private void run() throws IOException {
        Map<String, GranuleWriter> granuleWriters = new HashMap<>();
        String parentProductName = null;
        byte[] parentProductTxt = new byte[0];
        try (ZipInputStream zipIn = new ZipInputStream(new BufferedInputStream(inputStream))) {
            List<ZipEntryBuffer> zipEntryBuffers = new ArrayList<>();
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                String entryName = entry.getName();
                long size = entry.getSize() >= 0 ? entry.getSize() : entryMap.containsKey(entry.getName()) ? entryMap.get(entry.getName()).getSize() : -1;
                if (entryName.endsWith(GranuleMerger.PARENT_PRODUCT_TXT)) {
                    ZipEntryBuffer buffer = new ZipEntryBuffer(entryName, (int) size, zipIn);
                    parentProductTxt = buffer.getBuffer();
                } else if (detectGranule(entryName) == null) {
                    ZipEntryBuffer zipEntryBuffer = new ZipEntryBuffer(entryName, (int) size, zipIn);
                    zipEntryBuffers.add(zipEntryBuffer);

                    for (GranuleWriter granuleWriter : granuleWriters.values()) {
                        writeBufferToWriter(zipEntryBuffer, granuleWriter);
                    }
                } else {
                    if (parentProductName == null) {
                        parentProductName = entryName.split("/")[0];
                        int i = parentProductName.indexOf(".SAFE");
                        if (i > -1) {
                            parentProductName = parentProductName.substring(0, i);
                        }
                    }
                    // entry belongs to a specific granule
                    GranuleSpec granuleSpec = GranuleSpec.parse(entryName);
                    GranuleWriter granuleWriter = granuleWriters.get(granuleSpec.getGranuleName());
                    if (granuleWriter == null) {

                        granuleWriter = new GranuleWriter(granuleSpec);
                        granuleWriters.put(granuleSpec.getGranuleName(), granuleWriter);

                        // write buffered entries
                        for (ZipEntryBuffer zipEntryBuffer : zipEntryBuffers) {
                            writeBufferToWriter(zipEntryBuffer, granuleWriter);
                        }
                    }
                    copyToAWriter(entryName, size, zipIn, granuleWriter);
                }
            }
        }
        List<String> newProductNames = new ArrayList<>(granuleWriters.size());
        for (GranuleWriter granuleWriter : granuleWriters.values()) {
            newProductNames.add(granuleWriter.getOutputFile().getName());
        }
        for (GranuleWriter granuleWriter : granuleWriters.values()) {
            granuleWriter.writeParentProductInfo(parentProductTxt, parentProductName, newProductNames);
            granuleWriter.close();
        }
        writeMarkerFile(parentProductName, newProductNames);
    }

    static String detectGranule(String entryName) {
        if (entryName.contains("GRANULE")) {
            Matcher matcher = TILE_PATTERN.matcher(entryName);
            if (matcher.matches()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    static String processingBaselineOf(String entryName) {
        Matcher m = BASELINE_PATTERN.matcher(entryName);
        if (m.matches()) {
            return m.group(1) + m.group(2);
        }
        return null;
    }

    private static void copyToAWriter(String name, long size, ZipInputStream zipIn, GranuleWriter writer) throws IOException {
        copyToAllWriters(name, size, zipIn, Collections.singletonList(writer));
    }

    private static void copyToAllWriters(String name, long size, ZipInputStream zipIn, Collection<GranuleWriter> writers) throws IOException {
        for (GranuleWriter writer : writers) {
            writer.putNextEntry(name, size);
        }
        if (!name.endsWith("/")) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int n;
            while ((n = zipIn.read(buffer)) > 0) {
                for (GranuleWriter writer : writers) {
                    writer.write(buffer, n);
                }
            }
            for (GranuleWriter writer : writers) {
                writer.closeEntry();
            }
        }
    }

    private static void writeBufferToWriter(ZipEntryBuffer zipEntryBuffer, GranuleWriter writer) throws IOException {
        writer.putNextEntry(zipEntryBuffer.getName(), zipEntryBuffer.getSize());
        if (!zipEntryBuffer.getName().endsWith("/")) {
            byte[] buffer = zipEntryBuffer.getBuffer();
            writer.write(buffer, buffer.length);
            writer.closeEntry();
        }
    }

    private void writeMarkerFile(String parentProductName, List<String> newProductNames) throws IOException {
        File markerFile = new File(outputDir, "_" + parentProductName);
        try(FileWriter fileWriter = new FileWriter(markerFile)) {
            for (String newProductName : newProductNames) {
                fileWriter.write(newProductName + "\n");
            }
        }
    }

    private class GranuleWriter implements AutoCloseable {

        private final ZipOutputStream zipOutputStream;
        private final GranuleSpec granuleSpec;
        private final File outputFile;

        GranuleWriter(GranuleSpec granuleSpec) throws IOException {
            this.granuleSpec = granuleSpec;
            File outputFileTemp = new File(outputDir, getGranuleFilename(0));
            int counter = 1;
            while (outputFileTemp.exists()) {
                outputFileTemp = new File(outputDir, getGranuleFilename(counter));
                counter++;
            }
            System.out.println("writing to: " + outputFileTemp);
            this.outputFile = outputFileTemp;
            this.zipOutputStream = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outputFile)));
        }

        private String getGranuleFilename(int counter) {
            String filename = granuleSpec.getTopDirName();
            if (counter > 0) {
                filename += "_" + counter;
            }
            if (ADD_ZIP_SUFFIX) {
                filename += ".zip";
            }
            return filename;
        }

        File getOutputFile() {
            return outputFile;
        }

        void putNextEntry(String entryName, long size) throws IOException {
            String[] entryNameParts = entryName.split("/");
            entryNameParts[0] = granuleSpec.getTopDirName() + ".SAFE";
            if (entryNameParts.length > 1 && entryNameParts[1].contains("SAFL1C") && entryNameParts[1].endsWith(".xml")) {
                entryNameParts[1] = granuleSpec.convertXmlName(entryNameParts[1]);
            }
            String newEntryName = String.join("/", entryNameParts);
            if (entryName.endsWith("/")) {
                newEntryName += "/";
            }
            final ZipEntry entry = new ZipEntry(newEntryName);
            entry.setSize(size);
            zipOutputStream.putNextEntry(entry);
        }

        void write(byte[] buffer, int n) throws IOException {
            zipOutputStream.write(buffer, 0, n);
        }

        void closeEntry() throws IOException {
            zipOutputStream.closeEntry();
        }

        public void writeParentProductInfo(byte[] oldParentProductTxt, String productName, List<String> newProductNames) throws IOException {
            StringBuilder sb = new StringBuilder();
            sb.append(productName);
            sb.append("\n");
            for (String newProductName : newProductNames) {
                sb.append(newProductName);
                sb.append("\n");
            }
            byte[] buffer = sb.toString().getBytes(Charset.forName("UTF-8"));

            putNextEntry(productName + ".SAFE/" + GranuleMerger.PARENT_PRODUCT_TXT, oldParentProductTxt.length + buffer.length);
            write(oldParentProductTxt, oldParentProductTxt.length);
            write(buffer, buffer.length);
        }

        @Override
        public void close() throws IOException {
            zipOutputStream.close();
        }
    }

    private class ZipEntryBuffer {
        private String name;
        private byte[] buffer;

        ZipEntryBuffer(String name, int size, ZipInputStream zipIn) throws IOException {
            this.name = name;
            this.buffer = new byte[size];
            int offset = 0;
            int n;
            int len = buffer.length;
            while ((n = zipIn.read(buffer, offset, len)) > 0) {
                offset += n;
                len -= n;
            }
        }

        public String getName() {
            return name;
        }

        public byte[] getBuffer() {
            return buffer;
        }

        public long getSize() {
            return buffer.length;
        }
    }

    static class GranuleSpec {
        private final String topDirName;
        private final String granuleName;
        private final String tileId;
        private final String processingBaseline;
        private final String granuleProcessingTime;
        public static final Pattern LONG_MSIL1CNAME_PATTERN = Pattern.compile("S2(.)_...._..._(......)_...._(........T......)_(....)_V(........T......)_(........T......).*");

        GranuleSpec(String topDirName, String granuleName, String tileId, String processingBaseline, String granuleProcessingTime) {
            this.topDirName = topDirName;
            this.granuleName = granuleName;
            this.tileId = tileId;
            this.processingBaseline = processingBaseline;
            this.granuleProcessingTime = granuleProcessingTime;
        }

        String getTopDirName() {
            return topDirName;
        }

        String getGranuleName() {
            return granuleName;
        }

        String getTileId() {
            return tileId;
        }

        String getProcessingBaseline() {
            return processingBaseline;
        }

        String getGranuleProcessingTime() {
            return granuleProcessingTime;
        }

        String convertXmlName(String xmlName) {
            return convertName(xmlName, granuleProcessingTime, tileId, processingBaseline, ".xml") + ".xml";
        }

        private static String convertName(String name, String granuleProcessingTime, String tileId, String processingBaseline, String extension) {
            //S2A_OPER_PRD_MSIL1C_PDMC_20160921T112517_R092_V20160921T073612_20160921T075523.SAFE
            //S2A_OPER_PRD_MSIL1C_PDMC_20160921T112517_R092_V20160921T073612_20160921T075523_T36LWL.SAFE
            if (COMPACT_FORMAT) {
                final Matcher m = LONG_MSIL1CNAME_PATTERN.matcher(name);
                if (! m.matches()) {
                    throw new IllegalArgumentException("dir name " + name + " does not match pattern " + LONG_MSIL1CNAME_PATTERN.pattern());
                }
                final String platform = m.group(1);
                final String type = m.group(2);
                final String relOrbit = m.group(4);
                final String startTime = m.group(5);
                //S2A_MSIL1C_20161212T100412_N0204_R122_T33UVT_20161212T100409
                return "S2" + platform + "_" + type + "_" + startTime + "_N" + processingBaseline + "_" + relOrbit + tileId + "_" + granuleProcessingTime;
                //return "S2%s_%s_%s_N%s_%s%s_%s".format(platform, type, startTime, processingBaseline, relOrbit, tileId, granuleProcessingTime);
            } else {
                String p1 = name.substring(0, 25);
                String p2 = name.substring(40);
                int i = p2.indexOf(extension);
                if (i > -1) {
                    p2 = p2.substring(0, i);
                }
                return p1 + granuleProcessingTime + p2 + tileId;
            }
        }

        static GranuleSpec parse(String entryName) {
            String[] entrySplit = entryName.split("/");
            if (entrySplit.length >= 3) {
                String productName = entrySplit[0];
                String granuleName = entrySplit[2];

                String granuleProcessingTime = granuleName.substring(25, 40);
                String tileId = detectGranule(entryName);
                String processingBaseline = processingBaselineOf(entryName);

                String topDirName = convertName(productName, granuleProcessingTime, tileId, processingBaseline, ".SAFE");
                return new GranuleSpec(topDirName, granuleName, tileId, processingBaseline, granuleProcessingTime);
            }
            throw new IllegalArgumentException("unable to parse granule spec");
        }
    }
}
