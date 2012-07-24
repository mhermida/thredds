package thredds.core.grabbag;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import thredds.catalog.InvCatalog;
import thredds.catalog.InvCatalogImpl;
import thredds.catalog.InvCatalogRef;
import thredds.catalog.InvDataset;
import thredds.catalog.crawl.CatalogCrawler;
import thredds.catalog.crawl.CatalogCrawler.Type;
import thredds.core.DataRootHandler;

@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
public class CatalogCrawlerDemo {
	
	private CatalogCrawler cc;
	
	
	@Test
	public void listAllDatasets(){
		
		cc = new CatalogCrawler( Type.all , null, new CatalogCrawler.Listener() {
			
			public void getDataset(InvDataset dd, Object context) {
								
				List<InvDataset> datasets = dd.getDatasets();
				for( InvDataset ds : datasets ){
					System.out.println("DS ID:"+ ds.getID() +"  -- Name: "+ds.getName() );
				}
			}
			
			public boolean getCatalogRef(InvCatalogRef dd, Object context) {

				return true;
			}
		});
		
		InvCatalog cat = DataRootHandler.getInstance().getStaticCatalogs().get("catalog.xml");
		
		List<InvDataset> datasets = cat.getDatasets();
		
		//int crawlResult = cc.crawl((InvCatalogImpl)cat, null, System.out, null);
		for(InvDataset ds : datasets)
			cc.crawlDataset(ds, null, System.out, this, true);

	}

}
