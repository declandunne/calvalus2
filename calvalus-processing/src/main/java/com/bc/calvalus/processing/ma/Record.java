package com.bc.calvalus.processing.ma;

import org.esa.beam.framework.datamodel.GeoPos;

/**
 * A match-up record.
 *
 * @author Norman
 */
public interface Record {

    Object getId();

    GeoPos getGeoPos();
}
