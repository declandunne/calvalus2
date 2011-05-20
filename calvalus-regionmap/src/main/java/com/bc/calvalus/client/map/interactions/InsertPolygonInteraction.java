package com.bc.calvalus.client.map.interactions;

import com.bc.calvalus.client.map.MapAction;
import com.bc.calvalus.client.map.MapInteraction;
import com.bc.calvalus.client.map.Region;
import com.bc.calvalus.client.map.RegionMap;
import com.google.gwt.maps.client.MapWidget;
import com.google.gwt.maps.client.event.MapClickHandler;
import com.google.gwt.maps.client.event.MapMouseMoveHandler;
import com.google.gwt.maps.client.geom.LatLng;
import com.google.gwt.maps.client.geom.Point;
import com.google.gwt.maps.client.overlay.Polygon;
import com.google.gwt.maps.client.overlay.Polyline;

/**
 * An interactor that inserts polygons into a map.
 *
 * @author Norman Fomferra
 */
public class InsertPolygonInteraction extends MapInteraction implements MapClickHandler, MapMouseMoveHandler {
    private RegionMap regionMap;
    private Polyline polyline;

    public InsertPolygonInteraction(MapAction insertAction) {
        super(insertAction);
    }

    @Override
    public void attachTo(RegionMap regionMap) {
        this.regionMap = regionMap;
        regionMap.getMapWidget().addMapClickHandler(this);
        regionMap.getMapWidget().addMapMouseMoveHandler(this);
    }

    @Override
    public void detachFrom(RegionMap regionMap) {
        regionMap.getMapWidget().removeMapClickHandler(this);
        regionMap.getMapWidget().removeMapMouseMoveHandler(this);
        this.regionMap = null;
    }

    @Override
    public void onClick(MapClickEvent event) {
        MapWidget mapWidget = event.getSender();
        LatLng latLng = event.getLatLng();
        if (latLng == null) {
            latLng = event.getOverlayLatLng();
            if (latLng == null) {
                return;
            }
        }
        if (polyline == null) {
            polyline = new Polyline(new LatLng[]{latLng, latLng});
            mapWidget.addOverlay(polyline);
        } else {
            Point point1 = mapWidget.convertLatLngToDivPixel(latLng);
            Point point2 = mapWidget.convertLatLngToDivPixel(polyline.getVertex(0));
            int dx = point2.getX() - point1.getX();
            int dy = point2.getY() - point1.getY();
            double pixelDistance = Math.sqrt(dx * dx + dy * dy);
            if (pixelDistance < 8.0) {
                Polygon polygon = convertToPolygon(polyline);
                mapWidget.addOverlay(polygon);
                mapWidget.removeOverlay(polyline);
                polyline = null;
                Region region = Region.createUserRegion(polygon);
                regionMap.getModel().getRegionProvider().getList().add(0, region);
                regionMap.getModel().getRegionSelection().setSelectedRegions(region);
                // Interaction complete, invoke the actual action.
                run(regionMap);
                // System.out.println("polygon added with " + polygon.getVertexCount() + " vertices");
            } else {
                polyline.insertVertex(polyline.getVertexCount(), latLng);
                // System.out.println("vertex added to polyline which now has " + polyline.getVertexCount() + " vertices");
            }
        }
    }

    @Override
    public void onMouseMove(MapMouseMoveEvent event) {
        if (polyline != null) {
            polyline.deleteVertex(polyline.getVertexCount() - 1);
            polyline.insertVertex(polyline.getVertexCount(), event.getLatLng());
        }
    }

    static Polygon convertToPolygon(Polyline polyline) {
        int n = polyline.getVertexCount();
        LatLng[] points = new LatLng[n];
        for (int i = 0; i < n - 1; i++) {
            points[i] = polyline.getVertex(i);
        }
        points[n - 1] = polyline.getVertex(0);
        return new Polygon(points);
    }

}
