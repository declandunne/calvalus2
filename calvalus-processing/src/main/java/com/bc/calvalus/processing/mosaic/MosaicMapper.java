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

package com.bc.calvalus.processing.mosaic;

import com.bc.calvalus.binning.VariableContext;
import com.bc.calvalus.commons.CalvalusLogger;
import com.bc.calvalus.processing.JobConfigNames;
import com.bc.calvalus.processing.JobUtils;
import com.bc.calvalus.processing.beam.ProductFactory;
import com.bc.calvalus.processing.l3.L3Config;
import com.bc.ceres.glevel.MultiLevelImage;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.simplify.DouglasPeuckerSimplifier;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.RasterDataNode;
import org.esa.beam.framework.datamodel.VirtualBand;
import org.esa.beam.gpf.operators.standard.reproject.ReprojectionOp;
import org.esa.beam.jai.ImageManager;
import org.esa.beam.util.ImageUtils;
import org.esa.beam.util.ProductUtils;
import org.geotools.referencing.crs.DefaultGeographicCRS;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * Reads an N1 product and emits (tileIndex, tileData) pairs.
 *
 * @author Marco Zuehlke
 */
public class MosaicMapper extends Mapper<NullWritable, NullWritable, TileIndexWritable, TileDataWritable> {

    private static final Logger LOG = CalvalusLogger.getLogger();
    private static final String COUNTER_GROUP_NAME_PRODUCTS = "Product Counts";
    private static final String COUNTER_GROUP_NAME_PRODUCT_TILE_COUNTS = "Product Tile Counts";

    @Override
    public void run(Context context) throws IOException, InterruptedException {
        final Configuration jobConfig = context.getConfiguration();
        final L3Config l3Config = L3Config.get(jobConfig);
        final ProductFactory productFactory = new ProductFactory(jobConfig);
        final VariableContext ctx = l3Config.getVariableContext();
        final FileSplit split = (FileSplit) context.getInputSplit();

        // write initial log entry for runtime measurements
        LOG.info(MessageFormat.format("{0} starts processing of split {1}", context.getTaskAttemptID(), split));
        final long startTime = System.nanoTime();

        Path inputPath = split.getPath();
        String inputFormat = jobConfig.get(JobConfigNames.CALVALUS_INPUT_FORMAT, null);
        Geometry regionGeometry = JobUtils.createGeometry(jobConfig.get(JobConfigNames.CALVALUS_REGION_GEOMETRY));
        String level2OperatorName = jobConfig.get(JobConfigNames.CALVALUS_L2_OPERATOR);
        String level2Parameters = jobConfig.get(JobConfigNames.CALVALUS_L2_PARAMETERS);
        Product product = productFactory.getProduct(inputPath,
                                                    inputFormat,
                                                    regionGeometry,
                                                    true,
                                                    level2OperatorName,
                                                    level2Parameters);
        int numTiles = 0;
        if (product != null) {
            try {
                if (product.getGeoCoding() == null) {
                    throw new IllegalArgumentException("product.getGeoCoding() == null");
                }
                numTiles = processProduct(product, ctx, context);
                if (numTiles > 0L) {
                    context.getCounter(COUNTER_GROUP_NAME_PRODUCTS, inputPath.getName()).increment(1);
                    context.getCounter(COUNTER_GROUP_NAME_PRODUCT_TILE_COUNTS, inputPath.getName()).increment(numTiles);
                    context.getCounter(COUNTER_GROUP_NAME_PRODUCT_TILE_COUNTS, "Total").increment(numTiles);
                }
            } finally {
                product.dispose();
            }
        } else {
            LOG.info("Product not used");
        }

        long stopTime = System.nanoTime();

        // write final log entry for runtime measurements
        LOG.info(MessageFormat.format("{0} stops processing of split {1} after {2} sec ({3} tiless produced)",
                                      context.getTaskAttemptID(), split, (stopTime - startTime) / 1E9, numTiles));
    }

    private int processProduct(Product product, VariableContext ctx, Context context) throws IOException, InterruptedException {
        Geometry sourceGeometry = computeProductGeometry(product);
        if (sourceGeometry == null || sourceGeometry.isEmpty()) {
            LOG.info("Product geometry is empty");
            return 0;
        }
        Product gridProduct = toPlateCareGrid(product);
        for (int i = 0; i < ctx.getVariableCount(); i++) {
            String variableName = ctx.getVariableName(i);
            String variableExpr = ctx.getVariableExpr(i);
            if (variableExpr != null) {
                VirtualBand band = new VirtualBand(variableName,
                                                   ProductData.TYPE_FLOAT32,
                                                   gridProduct.getSceneRasterWidth(),
                                                   gridProduct.getSceneRasterHeight(),
                                                   variableExpr);
                band.setValidPixelExpression(ctx.getMaskExpr());
                gridProduct.addBand(band);
            }
        }

        final String maskExpr = ctx.getMaskExpr();
        final MultiLevelImage maskImage = ImageManager.getInstance().getMaskImage(maskExpr, product);

        final MultiLevelImage[] varImages = new MultiLevelImage[ctx.getVariableCount()];
        for (int i = 0; i < ctx.getVariableCount(); i++) {
            final String nodeName = ctx.getVariableName(i);
            final RasterDataNode node = getRasterDataNode(gridProduct, nodeName);
            final MultiLevelImage varImage = node.getGeophysicalImage();
            varImages[i] = varImage;
        }
        final Point[] tileIndices = maskImage.getTileIndices(null);

        int numTileTotal = 0;
        TileFactory tileFactory = new TileFactory(gridProduct, maskImage, varImages, sourceGeometry, context);
        for (Point tileIndex : tileIndices) {
            if (tileFactory.processTile(tileIndex)) {
                numTileTotal++;
            }
        }
        return numTileTotal;
    }


    private static RasterDataNode getRasterDataNode(Product product, String nodeName) {
        final RasterDataNode node = product.getRasterDataNode(nodeName);
        if (node == null) {
            throw new IllegalStateException(String.format("Can't find raster data node '%s' in product '%s'",
                                                          nodeName, product.getName()));
        }
        return node;
    }

    private static Product toPlateCareGrid(Product sourceProduct) {
        final ReprojectionOp repro = new ReprojectionOp();

        repro.setParameter("resampling", "Nearest");
        repro.setParameter("includeTiePointGrids", true);
        repro.setParameter("orientation", 0.0);
        repro.setParameter("pixelSizeX", 1.0 / 370.0);
        repro.setParameter("pixelSizeY", 1.0 / 370.0);
        repro.setParameter("tileSizeX", 370);
        repro.setParameter("tileSizeY", 370);
        repro.setParameter("crs", DefaultGeographicCRS.WGS84.toString());

        //TODO specify resolution
        int width = 370 * 360;
        int height = width / 2;
        double x = width / 2.0;
        double y = height / 2;
//        System.out.println("x = " + x);
//        System.out.println("y = " + y);
//        System.out.println("width = " + width);
//        System.out.println("height = " + height);

        repro.setParameter("easting", 0.0);
        repro.setParameter("northing", 0.0);
        repro.setParameter("referencePixelX", x);
        repro.setParameter("referencePixelY", y);
        repro.setParameter("width", width);
        repro.setParameter("height", height);

        repro.setSourceProduct(sourceProduct);
        return repro.getTargetProduct();
    }

    static Geometry computeProductGeometry(Product product) {
        try {
            final GeneralPath[] paths = ProductUtils.createGeoBoundaryPaths(product);
            final Polygon[] polygons = new Polygon[paths.length];
            final GeometryFactory factory = new GeometryFactory();
            for (int i = 0; i < paths.length; i++) {
                polygons[i] = convertAwtPathToJtsPolygon(paths[i], factory);
            }
            final DouglasPeuckerSimplifier peuckerSimplifier = new DouglasPeuckerSimplifier(
                    polygons.length == 1 ? polygons[0] : factory.createMultiPolygon(polygons));
            return peuckerSimplifier.getResultGeometry();
        } catch (Exception e) {
            return null;
        }
    }

    static Polygon convertAwtPathToJtsPolygon(Path2D path, GeometryFactory factory) {
        final PathIterator pathIterator = path.getPathIterator(null);
        ArrayList<double[]> coordList = new ArrayList<double[]>();
        int lastOpenIndex = 0;
        while (!pathIterator.isDone()) {
            final double[] coords = new double[6];
            final int segType = pathIterator.currentSegment(coords);
            if (segType == PathIterator.SEG_CLOSE) {
                // we should only detect a single SEG_CLOSE
                coordList.add(coordList.get(lastOpenIndex));
                lastOpenIndex = coordList.size();
            } else {
                coordList.add(coords);
            }
            pathIterator.next();
        }
        final Coordinate[] coordinates = new Coordinate[coordList.size()];
        for (int i1 = 0; i1 < coordinates.length; i1++) {
            final double[] coord = coordList.get(i1);
            coordinates[i1] = new Coordinate(coord[0], coord[1]);
        }

        return factory.createPolygon(factory.createLinearRing(coordinates), null);
    }

    private static class TileFactory {

        private final Product gridProduct;
        private final MultiLevelImage maskImage;
        private final MultiLevelImage[] varImages;
        private final Geometry sourceGeometry;
        private final Context context;
        private final GeometryFactory factory;

        public TileFactory(Product gridProduct, MultiLevelImage maskImage, MultiLevelImage[] varImages, Geometry sourceGeometry, Context context) {
            this.gridProduct = gridProduct;
            this.maskImage = maskImage;
            this.varImages = varImages;
            this.sourceGeometry = sourceGeometry;
            this.context = context;
            factory = new GeometryFactory();
        }

        private boolean processTile(Point tileIndex) throws IOException, InterruptedException {
            Rectangle tileRect = maskImage.getTileRect(tileIndex.x, tileIndex.y);
            int step = Math.min(tileRect.width, tileRect.height) / 8;
            step = step > 0 ? step : 1;
            final GeoPos[] geoPoints = ProductUtils.createGeoBoundary(gridProduct, tileRect, step, true);
            boolean intersects = false;

            for (GeoPos geoPoint : geoPoints) {
                if (geoPoint.isValid() && sourceGeometry.contains(factory.createPoint(new Coordinate(geoPoint.getLon(), geoPoint.getLat())))) {
                    intersects = true;
                    break;
                }
            }
            if (intersects) {
                LOG.info("tile intersects: " + tileIndex);
                Raster maskRaster = maskImage.getTile(tileIndex.x, tileIndex.y);

                boolean containsData = containsData(maskRaster);
                LOG.info("containsData = " + containsData);
                if (containsData) {
                    float[][] sampleValues = new float[varImages.length][370 * 370];//TODO
                    for (int i = 0; i < varImages.length; i++) {
                        Raster raster = varImages[i].getTile(tileIndex.x, tileIndex.y);
                        raster.getPixels(raster.getMinX(), raster.getMinY(), raster.getWidth(), raster.getHeight(), sampleValues[i]);
                    }

                    TileIndexWritable key = new TileIndexWritable(tileIndex.x, tileIndex.y);
                    TileDataWritable value = new TileDataWritable(370, 370, sampleValues);
                    context.write(key, value);
                    return true;
                }
            }
            return false;
        }

        private static boolean containsData(Raster mask) {
            DataBuffer dataBuffer = mask.getDataBuffer();
            Object primitiveArray = ImageUtils.getPrimitiveArray(dataBuffer);
            byte[] byteBuffer = (primitiveArray instanceof byte[]) ? (byte[]) primitiveArray : null;
            if (byteBuffer == null) {
                throw new IllegalStateException("mask is not of type byte");
            }

            for (int sample : byteBuffer) {
                if (sample == 1) {
                    return true;
                }
            }
            return false;
        }

    }
}
