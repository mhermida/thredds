package thredds.server.ncSubset.view;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;

import thredds.mock.web.MockTdsContextLoader;
import thredds.server.ncSubset.controller.SupportedFormat;
import thredds.server.ncSubset.exception.DateUnitException;
import thredds.server.ncSubset.exception.OutOfBoundariesException;
import thredds.server.ncSubset.util.NcssRequestUtils;
import thredds.servlet.DatasetHandlerAdapter;
import thredds.test.context.junit4.SpringJUnit4ParameterizedClassRunner;
import thredds.test.context.junit4.SpringJUnit4ParameterizedClassRunner.Parameters;
import ucar.nc2.dt.GridDataset;
import ucar.nc2.dt.grid.GridAsPointDataset;
import ucar.nc2.time.CalendarDate;
import ucar.nc2.time.CalendarDateRange;
import ucar.unidata.geoloc.LatLonPoint;

@RunWith(SpringJUnit4ParameterizedClassRunner.class)
@ContextConfiguration(locations = { "/WEB-INF/applicationContext-tdsConfig.xml" }, loader = MockTdsContextLoader.class)
public class StationCollectionStreamTest {
	
	private PointDataStream pointDataStream;
	private SupportedFormat supportedFormat;	
	private String pathInfo;
	private LatLonPoint point;
	
	private GridDataset gridDataset;
	private CalendarDateRange range;
	private List<String> vars;
	private List<Double> vertCoords;	
	
	@Parameters
	public static List<Object[]> getTestParameters(){
		
		return Arrays.asList(new Object[][]{  
				{SupportedFormat.NETCDF, PointDataWritersParameters.getVars().get(0) , PointDataWritersParameters.getPathInfo().get(0), PointDataWritersParameters.getPoints().get(2) }				
		});				
	}
	
	public StationCollectionStreamTest(SupportedFormat supportedFormat,  List<String> vars ,  String pathInfo, LatLonPoint point){
		this.supportedFormat = supportedFormat;
		this.vars = vars;
		this.pathInfo = pathInfo;
		this.point = point;
	}

	@Before
	public void setUp() throws IOException, OutOfBoundariesException, Exception{
		
		gridDataset = DatasetHandlerAdapter.openGridDataset(pathInfo);
		GridAsPointDataset gridAsPointDataset = NcssRequestUtils.buildGridAsPointDataset(gridDataset, vars);
		pointDataStream = PointDataStream.createPointDataStream(supportedFormat, new ByteArrayOutputStream());	
		List<CalendarDate> dates = gridAsPointDataset.getDates();
		Random rand = new Random();
		int randInt =     rand.nextInt( dates.size());
		int randIntNext = rand.nextInt(dates.size());
		int start = Math.min(randInt, randIntNext);
		int end = Math.max(randInt, randIntNext);
		range = CalendarDateRange.of( dates.get(start), dates.get(end));
		
		vertCoords = new ArrayList<Double>();
		
	}
	
	@Test
	public void shouldStreamStationCollection() throws OutOfBoundariesException, DateUnitException{
		
		assertTrue( pointDataStream.stream(gridDataset, point, range, vars, vertCoords) );
		
	}
	

}