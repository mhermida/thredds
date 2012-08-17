package thredds.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TdsContext implements InitializingBean{

	static private Logger log = LoggerFactory.getLogger(TdsContext.class);

	/*
	 * thredds directory within the content directory. It contains all the configuration files:
	 *  - threddsConfig.xml
	 *    
	 */
	private static final String TDS_CONTENT_DIRECTORY = "thredds";

	/*
	 * thredds public directory.
	 */	
	private static final String TDS_PUBLIC_DIRECTORY = TDS_CONTENT_DIRECTORY+"/public";	

	@Value("${tds.content.root.path}")
	private String contentRootPathStr;

	@Value("${tds.content.startup.path}")
	private String contentStartupPathStr;	

	@Value("${tds.config.file}")
	private String configFileName;
	
	private Path contentRootPath;
	private Path contentStartup;
	
	public void afterPropertiesSet(){
		
		createcontent();

	}

	public String getContentRootPath(){
		return contentRootPathStr;
	}

	public String getContentDirectory(){
		return  Paths.get(contentRootPathStr+"/"+TDS_CONTENT_DIRECTORY).toAbsolutePath().toString();
	}
	
	public String getPublicDocFileDirectory(){
		
		return Paths.get(getContentRootPath()+"/"+TDS_PUBLIC_DIRECTORY).toAbsolutePath().toString();
	}
	
	public File getFileInContent(String relPath) throws FileNotFoundException{
		Path filePath = Paths.get(relPath);
		filePath = contentRootPath.resolve(filePath);
		if( !Files.exists(filePath) )
			throw new FileNotFoundException();
		
		return filePath.toAbsolutePath().toFile();
	}
	
	public String getContentPath(){
		return TDS_CONTENT_DIRECTORY;
	}
	

	
	private void createcontent(){
		
		contentRootPath = Paths.get(contentRootPathStr+"/"+TDS_CONTENT_DIRECTORY);
		contentStartup = Paths.get(contentStartupPathStr);	

		if(!Files.exists(Paths.get( contentRootPathStr))){

			try{	
				Files.createDirectories(contentRootPath);
			}catch(IOException ioe){
				//Unable to create content directory
				log.error("Unable to create content directory: "+contentRootPath.toAbsolutePath().toString());
			}

			try{
				copyStartupContent(contentStartup, contentRootPath );
			}catch(IOException ioe){
				//Unable to copy startup content in content directory
				log.error("Unable to copy startup content in content directory: "+contentRootPath.toAbsolutePath().toString());		    	
			}
		}

		String configFilePathStr = contentRootPath+"/"+configFileName;
		try {			
			ThreddsConfig.init(configFilePathStr);
			
		} catch (FileNotFoundException e) {
			log.error("Config file: "+configFilePathStr+" not found");		
		}		
		
	}
	
	private void copyStartupContent(final Path sourceDir, final Path destDir) throws IOException{

		Files.walkFileTree( Paths.get(contentStartupPathStr), new SimpleFileVisitor<Path>(){ 

			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException{

				Path targetPath = destDir.resolve( sourceDir.relativize(dir) );

				if(!Files.exists(targetPath)){
					Files.createDirectory(targetPath);
				}

				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException{

				Files.copy(file, destDir.resolve( sourceDir.relativize(file) ));
				return FileVisitResult.CONTINUE;
			}
		});		
	}

}
