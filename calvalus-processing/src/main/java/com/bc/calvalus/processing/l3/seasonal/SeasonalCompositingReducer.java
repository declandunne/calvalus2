package com.bc.calvalus.processing.l3.seasonal;

import com.bc.calvalus.commons.CalvalusLogger;
import com.bc.calvalus.processing.JobConfigNames;
import com.bc.calvalus.processing.l2.ProductFormatter;
import com.bc.ceres.core.ProgressMonitor;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.Reducer;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.dataio.ProductWriter;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.CrsGeoCoding;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.TransformException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

/**
 * TBD
 */
public class SeasonalCompositingReducer extends Reducer<IntWritable, BandTileWritable, NullWritable, NullWritable> {

    static {
        System.getProperties().put("org.esa.beam.dataio.bigtiff.compression.type", "LZW");
        System.getProperties().put("org.esa.beam.dataio.bigtiff.tiling.width", "256");
        System.getProperties().put("org.esa.beam.dataio.bigtiff.tiling.height", "256");
        System.getProperties().put("org.esa.beam.dataio.bigtiff.force.bigtiff", "true");
    }

    protected static final Logger LOG = CalvalusLogger.getLogger();

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private static final SimpleDateFormat COMPACT_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd");
    public static final int NUM_TILE_COLUMNS = 72;
    public static final int NUM_TILE_ROWS = 36;

    public static String[] BAND_NAMES = {
            "status",
            "status_count",
            "obs_count",
            "sr_1_mean",
            "sr_2_mean",
            "sr_3_mean",
            "sr_4_mean",
            "sr_5_mean",
            "sr_6_mean",
            "sr_7_mean",
            "sr_8_mean",
            "sr_9_mean",
            "sr_10_mean",
            "sr_12_mean",
            "sr_13_mean",
            "sr_14_mean",
            "vegetation_index_mean"
    };

    private final List<float[]> usedTiles = new ArrayList<float[]>(BandTileWritable.TILE_WIDTH);

    @Override
    public void run(Context context) throws IOException, InterruptedException {
        final Configuration conf = context.getConfiguration();
        final Date start = getDate(conf, JobConfigNames.CALVALUS_MIN_DATE);
        final Date stop = getDate(conf, JobConfigNames.CALVALUS_MAX_DATE);
        final int noOfWeeks = Math.max((int) ((stop.getTime() - start.getTime()) / 86400 / 7 / 1000), 1);
        LOG.info("reducing start=" + DATE_FORMAT.format(start) + " weeks=" + noOfWeeks);
        if (! context.nextKey()) {
//        if (context.getCurrentKey() == null) {
            return;
        }
        boolean moreTilesAvailable = true;
        final int bandNumber = context.getCurrentKey().get() >> 16;
        final String bandName = BAND_NAMES[bandNumber];
        final String targetFileName = String.format("ESACCI-LC-L3-SR-MERIS-300m-P%dW-%s-%s-v1.0", noOfWeeks, bandName, COMPACT_DATE_FORMAT.format(start));
        final String outputDirName = conf.get("calvalus.output.dir");
        final Path outputPath = new Path(outputDirName, targetFileName + ".tif");
        final Product dimapOutput = new Product(targetFileName, bandName + " of seasonal composite",
                                          NUM_TILE_COLUMNS * BandTileWritable.TILE_WIDTH,
                                          NUM_TILE_ROWS * BandTileWritable.TILE_HEIGHT);
        try {
            dimapOutput.setGeoCoding(new CrsGeoCoding(DefaultGeographicCRS.WGS84,
                                                      dimapOutput.getSceneRasterWidth(), dimapOutput.getSceneRasterHeight(),
                                                      -180.0, 90.0,
                                                      360.0/NUM_TILE_COLUMNS/BandTileWritable.TILE_WIDTH, 180.0/NUM_TILE_ROWS/BandTileWritable.TILE_HEIGHT,
                                                      0.0, 0.0));
        } catch (Exception e) {
            throw new IllegalArgumentException("failed to create CRS geocoding: " + e.getMessage(), e);
        }

        final Band band;
        float[] floatBuffer = null;
        short[] shortBuffer = null;
        byte[] byteBuffer = null;
        ProductData dimapData;
        if (bandNumber == 0) {
            band = dimapOutput.addBand(bandName, ProductData.TYPE_INT8);
            byteBuffer = new byte[NUM_TILE_COLUMNS * BandTileWritable.TILE_WIDTH];
            dimapData = ProductData.createInstance(byteBuffer);
        } else if (bandNumber < 3) {
            band = dimapOutput.addBand(bandName, ProductData.TYPE_INT16);
            shortBuffer = new short[NUM_TILE_COLUMNS * BandTileWritable.TILE_WIDTH];
            dimapData = ProductData.createInstance(shortBuffer);
        } else {
            band = dimapOutput.addBand(bandName, ProductData.TYPE_FLOAT32);
            floatBuffer = new float[NUM_TILE_COLUMNS * BandTileWritable.TILE_WIDTH];
            dimapData = ProductData.createInstance(floatBuffer);
        }
        // product metadata

/*
        ProductFormatter formatter = new ProductFormatter(targetFileName, "BEAM-DIMAP", "zip");
        File tmpDimapFile = formatter.createTemporaryProductFile();
*/
        final String dimapFileName = targetFileName + ".dim";
        ProductWriter dimapWriter = ProductIO.getProductWriter(ProductIO.DEFAULT_FORMAT_NAME);
        dimapWriter.writeProductNodes(dimapOutput, dimapFileName);

        LOG.info("writing to " + targetFileName + ".dim");

        final float[][] tiles = new float[NUM_TILE_COLUMNS][];

        for (int tileRow = 0; tileRow < NUM_TILE_ROWS; tileRow++) {

            //LOG.info("processing tile row " + tileRow);
            // sort tiles of tile row into tile columns
            int count = 0;
            while (moreTilesAvailable && (context.getCurrentKey().get() & 0xFF00) >> 8 == tileRow) {
                final int tileColumn = context.getCurrentKey().get() & 0xFF;
                LOG.info("looking at tile " + context.getCurrentKey().get() + " tile column " + tileColumn);
                tiles[tileColumn] = copyOf(context.getValues().iterator().next().getTileData());
                if (! context.nextKey()) {
                    moreTilesAvailable = false;
                    LOG.info("no more tiles in context.");
                }
                ++count;
            }
            LOG.info(count + " tiles in tile row " + tileRow);

            // write lines of tile row to output file
            for (int r = 0; r < BandTileWritable.TILE_HEIGHT; r++) {
                for (int tileColumn = 0; tileColumn < NUM_TILE_COLUMNS; tileColumn++) {
                    if (tiles[tileColumn] != null) {
                        for (int c = 0; c < BandTileWritable.TILE_WIDTH; c++) {
                            final int iSrc = r*BandTileWritable.TILE_WIDTH+c;
                            final int iDst = tileColumn * BandTileWritable.TILE_WIDTH + c;
                            if (bandNumber == 0) {
                                byteBuffer[iDst] = (byte)tiles[tileColumn][iSrc];
                            } else if (bandNumber < 3) {
                                shortBuffer[iDst] = (short)tiles[tileColumn][iSrc];
                            } else {
                                floatBuffer[iDst] = tiles[tileColumn][iSrc];
                            }
                        }
                    } else {
                        for (int c = 0; c < BandTileWritable.TILE_WIDTH; c++) {
                            final int iDst = tileColumn * BandTileWritable.TILE_WIDTH + c;
                            if (bandNumber == 0) {
                                byteBuffer[iDst] = 0;
                            } else if (bandNumber < 3) {
                                shortBuffer[iDst] = 0;
                            } else {
                                floatBuffer[iDst] = Float.NaN;
                            }
                        }
                    }
                }
                dimapWriter.writeBandRasterData(band,
                                                0, tileRow * BandTileWritable.TILE_HEIGHT + r,
                                                NUM_TILE_COLUMNS * BandTileWritable.TILE_WIDTH, 1,
                                                dimapData, ProgressMonitor.NULL);
            }

            // collect garbage
            for (int tileColumn = 0; tileColumn < NUM_TILE_COLUMNS; tileColumn++) {
                if (tiles[tileColumn] != null) {
                    usedTiles.add(tiles[tileColumn]);
                    tiles[tileColumn] = null;
                }
            }
        }

        dimapOutput.closeIO();

/*
        LOG.info("copying dimap to " + outputPath);
        formatter.compressToHDFS(context, tmpDimapFile);
*/

        LOG.info("converting dimap to geotiff ...");
        final Product dimapInput = ProductIO.readProduct(dimapFileName);
        final ProductWriter geotiffWriter = ProductIO.getProductWriter("BigGeoTiff");
        geotiffWriter.writeProductNodes(dimapInput, targetFileName + ".tif");
        geotiffWriter.writeBandRasterData(dimapInput.getBandAt(0), 0, 0, 0, 0, null, null);
        dimapInput.closeIO();

        LOG.info("copying geotiff to " + outputPath);
        final DataOutputStream out = new DataOutputStream(new BufferedOutputStream(FileSystem.get(conf).create(outputPath)));
        final InputStream in = new BufferedInputStream(new FileInputStream(new File(targetFileName + ".tif")));
        ProductFormatter.copyAndClose(in, out, context);

        LOG.info("copying to " + outputPath + " done");
    }

    private static Date getDate(Configuration conf, String parameterName) {
        try {
            return DATE_FORMAT.parse(conf.get(parameterName));
        } catch (ParseException e) {
            throw new IllegalArgumentException("parameter " + parameterName + " value " + conf.get(parameterName) +
                                               " does not match pattern " + DATE_FORMAT.toPattern() + ": " + e.getMessage(), e);
        }
    }
    private float[] copyOf(float[] tileData) {
        if (usedTiles.isEmpty()) {
            usedTiles.add(new float[BandTileWritable.TILE_WIDTH * BandTileWritable.TILE_HEIGHT]);
        }
        float[] copy = usedTiles.remove(0);
        System.arraycopy(tileData, 0, copy, 0, tileData.length);
        return copy;
    }
}
