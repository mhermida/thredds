package thredds.server.ncSubset.controller;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import thredds.server.ncSubset.exception.NcssException;
import thredds.server.ncSubset.exception.OutOfBoundariesException;
import thredds.server.ncSubset.exception.VariableNotContainedInDatasetException;
import thredds.server.ncSubset.exception.UnsupportedOperationException;
import thredds.server.ncSubset.params.PointDataRequestParamsBean;
import thredds.server.ncSubset.params.RequestParamsBean;
import thredds.server.ncSubset.util.NcssRequestUtils;
import thredds.server.ncSubset.view.PointDataStream;
import ucar.nc2.dataset.CoordinateAxis1D;
import ucar.nc2.dt.GridCoordSystem;
import ucar.nc2.dt.GridDataset;
import ucar.nc2.dt.GridDatatype;
import ucar.nc2.time.CalendarDate;
import ucar.unidata.geoloc.LatLonPoint;
import ucar.unidata.geoloc.ProjectionPoint;

@Controller
@RequestMapping(value="/ncss/grid/**")
class PointDataController extends AbstratNcssDataRequestController{ 

	static private final Logger log = LoggerFactory.getLogger(PointDataController.class);

	
	@RequestMapping(value = "**", params = { "latitude", "longitude", "var" })
	void getPointData(@Valid PointDataRequestParamsBean params, BindingResult  validationResult, HttpServletResponse response ) throws ParseException, NcssException, IOException{

		if( validationResult.hasErrors() ){
			
			handleValidationErrorsResponse(response, HttpServletResponse.SC_BAD_REQUEST, validationResult );
			
		}else{
			
			//Checking request format...			
			SupportedFormat sf = getSupportedFormat(params, SupportedOperation.POINT_REQUEST  );			
			
			LatLonPoint point = params.getLatLonPoint(); //Check if the point is within boundaries!!
					
			checkRequestedVars(gridDataset,  params);
			Map<String, List<String>> groupVars= groupVarsByVertLevels(gridDataset, params);

			if( !isPointWithinBoundaries(params.getLatLonPoint(), groupVars ) ){			
			throw  new OutOfBoundariesException("Requested Lat/Lon Point (+" + point + ") is not contained in the Data. "+
					"Data Bounding Box = " + getGridDataset().getBoundingBox().toString2());
			}			
						
			List<CalendarDate> wantedDates = getRequestedDates( gridDataset, params);
	
			response.setContentType(sf.getResponseContentType() );
			PointDataStream pds = PointDataStream.createPointDataStream(  sf, response.getOutputStream() );
			
			boolean allWritten=false;
			
			
			setResponseHeaders(response, pds.getHttpHeaders(getGridDataset()) );
			
			allWritten = pds.stream( getGridDataset(), point, wantedDates, groupVars, params.getVertCoord());
										
			if(allWritten){				

				response.flushBuffer();
				response.getOutputStream().close();
				response.setStatus(HttpServletResponse.SC_OK);

			}else{
				//Something went wrong...
				response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			}
		}	
	}
	
	protected void checkRequestedVars(GridDataset gds, RequestParamsBean params) throws VariableNotContainedInDatasetException, UnsupportedOperationException{
	//Check vars
	//if var = all--> all variables requested
	if(params.getVar().get(0).equals("all")){
		params.setVar(NcssRequestUtils.getAllVarsAsList(getGridDataset()));					
	}		
	
	//Check not only all vars are contained in the grid, also they have the same vertical coords
	Iterator<String> it = params.getVar().iterator();
	String varName = it.next();
	GridDatatype grid = gds.findGridByShortName(varName);
	if(grid == null) 
		throw new VariableNotContainedInDatasetException("Variable: "+varName+" is not contained in the requested dataset");
	
	CoordinateAxis1D vertAxis = grid.getCoordinateSystem().getVerticalAxis();
	CoordinateAxis1D newVertAxis = null;
	boolean sameVertCoord = true;
	
	while(sameVertCoord && it.hasNext()){
		varName = it.next();
		grid = gds.findGridByShortName(varName);
		if(grid == null) 
			throw new VariableNotContainedInDatasetException("Variable: "+varName+" is not contained in the requested dataset");
		
		newVertAxis = grid.getCoordinateSystem().getVerticalAxis();
		
		if( vertAxis != null ){
			if( vertAxis.equals(newVertAxis)){
				vertAxis = newVertAxis;
			}else{
				sameVertCoord = false;
			}
		}else{
			if(newVertAxis != null) sameVertCoord = false;
		}	
	}
	
	if(!sameVertCoord)
		throw new UnsupportedOperationException("The variables requested: "+ params.getVar()  +" have different vertical levels. Only Grid as point requests on variables with same vertical levels are supported.");
		
	}
	
	private boolean isPointWithinBoundaries(LatLonPoint point, Map<String, List<String>> groupVars){	
		//LatLonRect bbox = gds.getBoundingBox();
		boolean isInData = true;
		List<String> keys = new ArrayList<String>(groupVars.keySet());
		
		int[] xy = new int[2];
		Iterator<String> it = keys.iterator();

		while( it.hasNext() && isInData ){
			String key = it.next();
			GridDatatype grid = gridDataset.findGridDatatype(groupVars.get(key).get(0));
			GridCoordSystem coordSys = grid.getCoordinateSystem();
			ProjectionPoint p = coordSys.getProjection().latLonToProj(point);
			xy = coordSys.findXYindexFromCoord(p.getX(), p.getY(), null);
			
			if(xy[0] < 0 || xy[1] < 0  ){
				isInData = false;
			}
		}
		
		return isInData;
	}
	
	private Map<String, List<String>> groupVarsByVertLevels(GridDataset gds, PointDataRequestParamsBean params) throws VariableNotContainedInDatasetException{
		String no_vert_levels ="no_vert_level";
		List<String> vars = params.getVar();
		Map<String, List<String>> varsGroupsByLevels = new HashMap<String, List<String>>();
		
		for(String var :vars ){
			GridDatatype grid =gds.findGridDatatype(var);
			
			//Variables should have been checked before...  
			if(grid == null ){
				throw new VariableNotContainedInDatasetException("Variable: "+var+" is not contained in the requested dataset");
			}			
			
			CoordinateAxis1D axis = grid.getCoordinateSystem().getVerticalAxis();
			String axisKey = null;
			if(axis == null){
				axisKey = no_vert_levels;
			}else{
				axisKey = axis.getShortName();
			 }
			 			 
			 if( varsGroupsByLevels.containsKey(axisKey) ){
				 varsGroupsByLevels.get(axisKey).add(var);
			}else{
				List<String> varListForVerlLevel = new ArrayList<String>();
				varListForVerlLevel.add(var);
				varsGroupsByLevels.put(axisKey, varListForVerlLevel);
			} 			 			 
		}
		
		return varsGroupsByLevels;
	}
	
	

	//Exception handlers
	@ExceptionHandler
	@ResponseStatus(value=HttpStatus.BAD_REQUEST)
	public @ResponseBody String handle(NcssException ncsse ){
		return "NetCDF Subset Service exception handled : "+ncsse.getMessage();
	}
	
	
	@ExceptionHandler
	@ResponseStatus(value=HttpStatus.INTERNAL_SERVER_ERROR)
	public @ResponseBody String handle(ParseException pe){
		return "Parse exception handled: "+pe.getMessage();
	}
	
	
	@ExceptionHandler
	@ResponseStatus(value=HttpStatus.INTERNAL_SERVER_ERROR)
	public @ResponseBody String handle(IOException ioe){
		return "IO exception handled: "+ioe.getMessage();
	}	
	
	@ExceptionHandler
	@ResponseStatus(value=HttpStatus.BAD_REQUEST)
	public @ResponseBody String handleValidationException(MethodArgumentNotValidException ve){
		return "Bad request: "+ve.getMessage();
	}	

}
