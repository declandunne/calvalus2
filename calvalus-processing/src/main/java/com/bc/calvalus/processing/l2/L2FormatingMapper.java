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

package com.bc.calvalus.processing.l2;

import com.bc.calvalus.commons.CalvalusLogger;
import com.bc.calvalus.processing.JobConfigNames;
import com.bc.calvalus.processing.beam.ProductFactory;
import com.bc.calvalus.processing.shellexec.ProcessorException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.util.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * A mapper which converts L2 products from the
 * (internal) SequenceFiles into different BAM product formats.
 */
public class L2FormatingMapper extends Mapper<NullWritable, NullWritable, NullWritable, NullWritable> {

    private static final Logger LOG = CalvalusLogger.getLogger();
    private Configuration jobConfig;

    @Override
    public void run(Mapper.Context context) throws IOException, InterruptedException, ProcessorException {
        jobConfig = context.getConfiguration();
        ProductFactory productFactory = new ProductFactory(jobConfig);
        final FileSplit split = (FileSplit) context.getInputSplit();

        // write initial log entry for runtime measurements
        LOG.info(context.getTaskAttemptID() + " starts processing of split " + split);
        final long startTime = System.nanoTime();

        Path inputPath = split.getPath();
        String productName = getProductName(inputPath.getName());

        String format = jobConfig.get(JobConfigNames.CALVALUS_OUTPUT_FORMAT, null);
        String compression = jobConfig.get(JobConfigNames.CALVALUS_OUTPUT_COMPRESSION, null);
        ProductFormatter productFormatter = new ProductFormatter(productName, format, compression);
        String outputFilename = productFormatter.getOutputFilename();
        String outputFormat = productFormatter.getOutputFormat();

        if (jobConfig.getBoolean(JobConfigNames.CALVALUS_RESUME_PROCESSING, false)) {
            Path outputProductPath = new Path(FileOutputFormat.getOutputPath(context), outputFilename);
            if (FileSystem.get(jobConfig).exists(outputProductPath)) {
                LOG.info("resume: target product already exist, skip processing");
                final long stopTime = System.nanoTime();
                LOG.info(context.getTaskAttemptID() + " stops processing of split " + split + " after " + ((stopTime - startTime) / 1E9) + " sec");
                return;
            }
        }

        String inputFormat = jobConfig.get(JobConfigNames.CALVALUS_INPUT_FORMAT, null);
        String level2OperatorName = jobConfig.get(JobConfigNames.CALVALUS_L2_OPERATOR);
        String level2Parameters = jobConfig.get(JobConfigNames.CALVALUS_L2_PARAMETERS);
        Product product = productFactory.getProduct(inputPath,
                                                    inputFormat,
                                                    null,
                                                    true,
                                                    level2OperatorName,
                                                    level2Parameters);


        product.setName(productName);

        try {
            File productFile = productFormatter.createTemporaryProductFile();
            LOG.info("Start writing product to file: " + productFile.getName());
            context.setStatus("writing");
            ProductIO.writeProduct(product, productFile, outputFormat, false, new ProductFormatter.ProgressMonitorAdapter(context));
            LOG.info("Finished writing product.");
            context.setStatus("copying");
            productFormatter.compressToHDFS(context, productFile);
        } finally {
            productFormatter.cleanupTempDir();
            product.dispose();
            final long stopTime = System.nanoTime();
            LOG.info(context.getTaskAttemptID() + " stops processing of split " + split + " after " + ((stopTime - startTime) / 1E9) + " sec");
        }
    }

    private String getProductName(String fileName) {
        String regex = jobConfig.get(JobConfigNames.CALVALUS_OUTPUT_REGEX, null);
        String replacement = jobConfig.get(JobConfigNames.CALVALUS_OUTPUT_REPLACEMENT, null);
        String newProductName = FileUtils.getFilenameWithoutExtension(fileName);
        LOG.info("Product name: " + newProductName);
        if (regex != null && replacement != null) {
            newProductName = getNewProductName(newProductName, regex, replacement);
        }
        LOG.info("New product name: " + newProductName);
        return newProductName;
    }

    static String getNewProductName(String productName, String regex, String replacement) {
        return productName.replaceAll(regex, replacement);
    }
}
