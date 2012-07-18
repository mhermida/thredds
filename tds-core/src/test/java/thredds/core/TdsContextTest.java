/**
 * 
 */
package thredds.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author marcos
 *
 */
@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
public class TdsContextTest {

	/*
	 * thredds directory within the context directory. It contains all the config files
	 */
	public static final String TDS_CONTEXT_DIRECTORY = "thredds";

	/*
	 * thredds public directory.
	 */	
	public static final String TDS_PUBLIC_DIRECTORY = TDS_CONTEXT_DIRECTORY+"/public";

	@Autowired
	TdsContext tdsContext;

	@Test
	public void tdsContextDirectoryName(){
		assertEquals("thredds", TDS_CONTEXT_DIRECTORY );
	}

	@Test
	public void tdsPublicDirectoryName(){
		assertTrue(TDS_PUBLIC_DIRECTORY.endsWith("/public"));
	}	

	//Check context directory
	@Test
	public void getTdsProperties(){

		assertEquals( "context", tdsContext.getContextRootPath() );

	}

	@Test
	public void createTdsContextDirectories() throws IOException{

		//Path tdsContextPath =  Paths.get( tdsContext.getContextRootPath()+"/thredds");		
		//assertTrue(Files.exists(tdsContextPath));
		Path tdsCatalog = Paths.get(tdsContext.getContextRootPath()+"/thredds/catalog.xml");
		assertTrue(Files.exists(tdsCatalog));			

		//cleanContext();
	}	

	private void cleanContext() throws IOException{

		Path start =  Paths.get( tdsContext.getContextRootPath());

		if(Files.exists(start)){

			Files.walkFileTree(start, new SimpleFileVisitor<Path>(){ 
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException{
					Files.deleteIfExists(file);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException e)throws IOException
				{
					if (e == null) {					
						Files.deleteIfExists(dir);
						return FileVisitResult.CONTINUE;
					} else {
						// directory iteration failed
						throw e;
					}
				}						
			});
		}
	}
}
