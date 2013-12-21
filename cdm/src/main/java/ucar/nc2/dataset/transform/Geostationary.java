package ucar.nc2.dataset.transform;

import ucar.nc2.Variable;
import ucar.nc2.constants.CF;
import ucar.nc2.dataset.CoordinateTransform;
import ucar.nc2.dataset.NetcdfDataset;
import ucar.nc2.dataset.ProjectionCT;
import ucar.nc2.dataset.TransformType;
import ucar.unidata.geoloc.ProjectionImpl;

/**
 * Describe: https://cf-pcmdi.llnl.gov/trac/ticket/72
 * Accepted for CF-1.7
 *
 * grid_mapping_name = geostationary
   Map parameters:
     latitude_of_projection_origin
     longitude_of_projection_origin
     perspective_point_height
     false_easting
     false_northing
     sweep_angle_axis
     fixed_angle_axis

 Map coordinates:
  The x (abscissa) and y (ordinate) rectangular coordinates are identified by the standard_name attribute value projection_x_coordinate and projection_y_coordinate
 respectively. In the case of this projection, the projection coordinates in this projection are directly related to the scanning angle of the satellite instrument,
 and their units are radians.

 Notes:

 The algorithm for computing the mapping may be found at http://www.eumetsat.int/idcplg?IdcService=GET_FILE&dDocName=PDF_CGMS_03&RevisionSelectionMethod=LatestReleased.
 This document assumes the point of observation is directly over the equator, and that the sweep_angle_axis is y.

 Notes on using the PROJ.4 software packages for computing the mapping may be found at http://trac.osgeo.org/proj/wiki/proj%3Dgeos and
 http://remotesensing.org/geotiff/proj_list/geos.html .

 The "perspective_point_height" is the distance to the surface of the ellipsoid. Adding the earth major axis gives the distance from the centre of the earth.

 The "sweep_angle_axis" attribute indicates which axis the instrument sweeps. The value = "y" corresponds to the spin-stabilized Meteosat satellites,
 the value = "x" to the GOES-R satellite.

 The "fixed_angle_axis" attribute indicates which axis the instrument is fixed. The values are opposite to "sweep_angle_axis". Only one of those two attributes are
 mandatory.
 *
 * @author caron
 * @since 12/5/13
 */
public class Geostationary extends AbstractCoordTransBuilder {

    public String getTransformName() {
      return "Geostationary";
    }

    public TransformType getTransformType() {
      return TransformType.Projection;
    }

    public CoordinateTransform makeCoordinateTransform(NetcdfDataset ds, Variable ctv) {
      readStandardParams(ds, ctv);

      double height = readAttributeDouble( ctv, CF.PERSPECTIVE_POINT_HEIGHT, Double.NaN);
      String sweep_angle = readAttribute( ctv, CF.SWEEP_ANGLE_AXIS, null);
      String fixed_angle = readAttribute( ctv, CF.FIXED_ANGLE_AXIS, null);

      if (sweep_angle == null && fixed_angle == null)
        throw new IllegalArgumentException("Must specify "+CF.SWEEP_ANGLE_AXIS+" or "+CF.FIXED_ANGLE_AXIS);
      boolean isSweepX;
      if (sweep_angle != null)
        isSweepX =  sweep_angle.equals("x");
      else
        isSweepX =  fixed_angle.equals("y");

      ProjectionImpl proj = null; // new ucar.unidata.geoloc.projection.sat.Geostationary(lat0, lon0, height, false_easting, false_northing, isSweepX, earth);
      return new ProjectionCT(ctv.getShortName(), "FGDC", proj);
    }

}
