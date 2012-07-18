package thredds.core;

import java.io.IOException;

import static org.junit.Assert.assertNotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import ucar.nc2.dt.GridDataset;

@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
public class DatasetHandlerTest {

	//private String datasetPath ="test/testData.nc";
	private String datasetPath ="testAll/2004050300_eta_211.nc";
	@Test
	public void ShouldOpenDataset() throws IOException{
		GridDataset gds =DatasetHandler.openGridDataset(datasetPath, null);
		assertNotNull(gds);
	}
}

