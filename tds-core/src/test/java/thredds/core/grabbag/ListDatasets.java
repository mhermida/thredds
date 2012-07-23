package thredds.core.grabbag;

import static org.junit.Assert.assertTrue;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import thredds.catalog.InvCatalog;
import thredds.catalog.InvCatalogImpl;
import thredds.catalog.InvDataset;
import thredds.catalog.InvDatasetScan;
import thredds.core.DataRootHandler;
import thredds.core.DataRootHandler.DataRoot;
import thredds.core.DatasetHandler;
import ucar.nc2.dataset.NetcdfDataset;
import ucar.nc2.dt.GridDataset;

@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
public class ListDatasets {


	@Test
	public void getAllDatasets() throws IOException{

		DataRootHandler dataRootHandler = DataRootHandler.getInstance();
		Map<String,InvCatalogImpl> catalogs = dataRootHandler.getStaticCatalogs();			
					
		List<String> keys = new ArrayList<String>( catalogs.keySet() );

		System.out.println("---catalogs start -----");
		for(String key : keys){			
			InvCatalogImpl cat = catalogs.get(key);
			
			System.out.println("Datasets in: "+ cat.getBaseURI() );

			List<InvDataset> datasets = cat.getDatasets();
			for(InvDataset dataset : datasets)
				listAllDatasets(dataset);

		}
		System.out.println("---catalogs end -----");
		System.out.println("---------------------");
		
		assertTrue(true);
	}

	private void listAllDatasets(InvDataset dataset) throws FileNotFoundException{

		if(dataset.hasNestedDatasets()){

			System.out.println("Dataset ID: "+dataset.getID()+". Name: "+dataset.getName() +". Has nested datasets" );

			if(dataset instanceof InvDatasetScan){
				InvDatasetScan dscan = (InvDatasetScan) dataset;		
				//System.out.println("Making catalog for: "+dscan.getPath()+"/catalog.xml"   );								
				//InvCatalog cat = dscan.makeCatalogForDirectory( dscan.getPath()+"/catalog.xml" , URI.create(dscan.getPath()+"/catalog.xml"));
				
				InvCatalog cat = DataRootHandler.getInstance().getCatalog(dscan.getPath()+"/catalog.xml" , URI.create(dscan.getPath()+"/catalog.xml")); 
						
				List<InvDataset> allDatasets= cat.getDatasets();
				
				for( InvDataset ds : allDatasets ){
					List<InvDataset> all = ds.getDatasets();
					for(InvDataset leafNode : all ){
						System.out.println( "ID: "+leafNode.getID()+" -  "+leafNode.getName() );
					}
				}				
			}			

			List<InvDataset> nestedDatasets = dataset.getDatasets();
			for(InvDataset nestedDataset : nestedDatasets ){
				listAllDatasets(nestedDataset);
			}

		}else{
			System.out.println("-->Dataset ID: "+dataset.getID()+". Name: "+dataset.getName()  );


		}

	}

}
