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

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.geom.prep.PreparedGeometry;
import com.vividsolutions.jts.geom.prep.PreparedGeometryFactory;
import com.vividsolutions.jts.simplify.DouglasPeuckerSimplifier;
import org.apache.hadoop.conf.Configuration;
import org.esa.snap.core.datamodel.CrsGeoCoding;
import org.esa.snap.core.datamodel.GeoCoding;
import org.esa.snap.core.datamodel.GeoPos;
import org.esa.snap.core.datamodel.PixelPos;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.util.ProductUtils;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.GeneralPath;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Defines the Grid on which the mosaic-ing is happening.
 *
 * @author MarcoZ
 */
public class MosaicGrid {

    private final int gridWidth;
    private final int gridHeight;
    private final int tileSize;
    private final double pixelSize;
    private final int numTileX;
    private final int macroTileSize;
    private final int numTileY;
    private GeometryFactory geometryFactory;

    public static MosaicGrid create(Configuration conf) {
        int macroTileSize = conf.getInt("calvalus.mosaic.macroTileSize", 5);
        int numTileY = conf.getInt("calvalus.mosaic.numTileY", 180);
        int tileSize = conf.getInt("calvalus.mosaic.tileSize", 370);
        return new MosaicGrid(macroTileSize, numTileY, tileSize);
    }

    MosaicGrid() {
        this(5, 180, 370);
    }

    MosaicGrid(int macroTileSize, int numTileY, int tileSize) {
        this.macroTileSize = macroTileSize;
        this.numTileY = numTileY;
        this.numTileX = numTileY * 2;
        this.tileSize = tileSize;
        this.gridWidth = numTileX * tileSize;
        this.gridHeight = numTileY * tileSize;
        this.pixelSize = 180.0 / gridHeight;
    }

    public void saveToConfiguration(Configuration conf) {
        conf.setInt("calvalus.mosaic.macroTileSize", macroTileSize);
        conf.setInt("calvalus.mosaic.numTileY", numTileY);
        conf.setInt("calvalus.mosaic.tileSize", tileSize);
    }

    private GeometryFactory getGeometryFactory() {
        if (geometryFactory == null) {
            geometryFactory = new GeometryFactory();
        }
        return geometryFactory;
    }

    public int getMacroTileSize() {
        return macroTileSize;
    }

    public int getTileSize() {
        return tileSize;
    }

    public double getPixelSize() {
        return pixelSize;
    }

    public int getNumTileX() {
        return numTileX;
    }

    public int getNumTileY() {
        return numTileY;
    }

    public int getNumMacroTileX() {
        return numTileX / macroTileSize;
    }

    public int getNumMacroTileY() {
        return numTileY / macroTileSize;
    }

    public Rectangle computeBounds(Geometry roiGeometry) {
        Rectangle region = new Rectangle(gridWidth, gridHeight);
        if (roiGeometry != null) {
            final Coordinate[] coordinates = roiGeometry.getBoundary().getCoordinates();
            double gxmin = Double.POSITIVE_INFINITY;
            double gxmax = Double.NEGATIVE_INFINITY;
            double gymin = Double.POSITIVE_INFINITY;
            double gymax = Double.NEGATIVE_INFINITY;
            for (Coordinate coordinate : coordinates) {
                gxmin = Math.min(gxmin, coordinate.x);
                gxmax = Math.max(gxmax, coordinate.x);
                gymin = Math.min(gymin, coordinate.y);
                gymax = Math.max(gymax, coordinate.y);
            }
            final int x = (int) Math.floor((180.0 + gxmin) / pixelSize);
            final int y = (int) Math.floor((90.0 - gymax) / pixelSize);
            final int width = (int) Math.ceil((gxmax - gxmin) / pixelSize);
            final int height = (int) Math.ceil((gymax - gymin) / pixelSize);
            final Rectangle unclippedOutputRegion = new Rectangle(x, y, width, height);
            region = unclippedOutputRegion.intersection(region);
        }
        return region;
    }

    public Rectangle alignToTileGrid(Rectangle rectangle) {
        int minX = rectangle.x / tileSize * tileSize;
        int maxX = (rectangle.x + rectangle.width + tileSize - 1) / tileSize * tileSize;
        int minY = (rectangle.y / tileSize) * tileSize;
        int maxY = (rectangle.y + rectangle.height + tileSize - 1) / tileSize * tileSize;

        return new Rectangle(minX, minY, maxX - minX, maxY - minY);
    }

    Geometry getTileGeometry(int tileX, int tileY) {
        double x1 = tileXToDegree(tileX);
        double x2 = tileXToDegree(tileX + 1);
        double y1 = tileYToDegree(tileY);
        double y2 = tileYToDegree(tileY + 1);
        return getGeometryFactory().toGeometry(new Envelope(x1, x2, y1, y2));
    }

    double tileXToDegree(int tileX) {
        final double degreePerTile = (double) 360 / numTileX;
        final double lon = tileX * degreePerTile - 180.0;
        return lon;
    }

    int degreeToTileX(double lon) {
        final double degreePerTile = (double) 360 / numTileX;
        final int tileX = (int) (Math.floor(lon + 180.0) / degreePerTile);
        return tileX;
    }

    double tileYToDegree(int tileY) {
        double degreePerTile = (double) 180 / numTileY;
        double lat = 90 - tileY * degreePerTile;
        return lat;
    }

    int degreeToTileY(double lat) {
        final double degreePerTile = (double) 180 / numTileY;
        final int tileY = (int) (Math.floor((lat - 90.0) * -1 / degreePerTile));
        return tileY;
    }

    public Geometry computeProductGeometry(Product product) {
        try {
            final GeneralPath[] paths = ProductUtils.createGeoBoundaryPaths(product);
            final Polygon[] polygons = new Polygon[paths.length];

            for (int i = 0; i < paths.length; i++) {
                polygons[i] = convertToJtsPolygon(paths[i].getPathIterator(null));
            }
            final DouglasPeuckerSimplifier peuckerSimplifier = new DouglasPeuckerSimplifier(
                    polygons.length == 1 ? polygons[0] : getGeometryFactory().createMultiPolygon(polygons));
            return peuckerSimplifier.getResultGeometry();
        } catch (Exception e) {
            return null;
        }
    }

    private Polygon convertToJtsPolygon(PathIterator pathIterator) {
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

        GeometryFactory factory = getGeometryFactory();
        return factory.createPolygon(factory.createLinearRing(coordinates), null);
    }

    public TileIndexWritable[] getTileIndices(List<Point> tilePointIndices) {
        TileIndexWritable[] tileIndices = new TileIndexWritable[tilePointIndices.size()];
        for (int i = 0; i < tilePointIndices.size(); i++) {
            Point point = tilePointIndices.get(i);
            int macroX = point.x / macroTileSize;
            int macroY = point.y / macroTileSize;
            tileIndices[i] = new TileIndexWritable(macroX, macroY, point.x, point.y);
        }
        return tileIndices;
    }

    List<Point> getTilePointIndicesFromProduct(Product product, Geometry geometry) {
        GeoCoding geoCoding = product.getSceneGeoCoding();
        int width = product.getSceneRasterWidth();
        int height = product.getSceneRasterHeight();
        PixelPos pixelPos = new PixelPos();
        GeoPos geoPos = new GeoPos();

        PreparedGeometry pGeom = null;
        GeometryFactory geoFactory = null;
        if (geometry != null) {
            pGeom = PreparedGeometryFactory.prepare(geometry);
            geoFactory = getGeometryFactory();
        }
        Set<Point> pointSet = new HashSet<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixelPos.setLocation(x + 0.5f, y + 0.5f);
                geoCoding.getGeoPos(pixelPos, geoPos);
                if (geoPos.isValid()) {
                    if (pGeom == null || pGeom.contains(geoFactory.createPoint(new Coordinate(geoPos.lon, geoPos.lat)))) {
                        int tileX = degreeToTileX(geoPos.lon);
                        int tileY = degreeToTileY(geoPos.lat);
                        pointSet.add(new Point(tileX, tileY));
                    }
                }
            }
        }
        List<Point> pointList = new ArrayList<>(pointSet);
        Collections.sort(pointList, new Comparator<Point>() {
            @Override
            public int compare(Point p1, Point p2) {
                int cmp = Integer.compare(p1.x, p2.x);
                if (cmp == 0) {
                    cmp = Integer.compare(p1.y, p2.y);
                }
                return cmp;
            }
        });
        return pointList;
    }

    List<Point> getTilePointIndicesFromGeometry(Geometry geometry) {
        Rectangle geometryBounds = computeBounds(geometry);
        Rectangle gridRect = alignToTileGrid(geometryBounds);
        final int xStart = gridRect.x / tileSize;
        final int yStart = gridRect.y / tileSize;
        final int width = gridRect.width / tileSize;
        final int height = gridRect.height / tileSize;
        List<Point> points = new ArrayList<>(width * height);

        for (int y = yStart; y < yStart + height; y++) {
            for (int x = xStart; x < xStart + width; x++) {
                Geometry tileGeometry = getTileGeometry(x, y);
                Geometry intersection = geometry.intersection(tileGeometry);
                if (!intersection.isEmpty() && intersection.getDimension() == 2) {
                    points.add(new Point(x, y));
                }
            }
        }
        return points;
    }

    List<Point> getTilePointIndicesGlobal() {
        List<Point> points = new ArrayList<>(numTileX * numTileY);

        for (int y = 0; y < numTileY; y++) {
            for (int x = 0; x < numTileX; x++) {
                points.add(new Point(x, y));
            }
        }
        return points;
    }

    public Rectangle getTileRectangle(int tileX, int tileY) {
        return new Rectangle(tileX * tileSize, tileY * tileSize, tileSize, tileSize);
    }

    public Rectangle getMacroTileRectangle(int tileX, int tileY) {
        int pixelPerTile = tileSize * macroTileSize;
        return new Rectangle(tileX * pixelPerTile, tileY * pixelPerTile, pixelPerTile, pixelPerTile);
    }

    public CrsGeoCoding createMacroCRS(Point macroTile) {
        CrsGeoCoding geoCoding;
        try {
            Rectangle productRegion = getMacroTileRectangle(macroTile.x, macroTile.y);
            geoCoding = new CrsGeoCoding(DefaultGeographicCRS.WGS84,
                                         productRegion.width,
                                         productRegion.height,
                                         -180.0 + pixelSize * productRegion.x,
                                         90.0 - pixelSize * productRegion.y,
                                         pixelSize,
                                         pixelSize,
                                         0.0, 0.0);
        } catch (FactoryException e) {
            throw new IllegalStateException(e);
        } catch (TransformException e) {
            throw new IllegalStateException(e);
        }
        return geoCoding;
    }
}
