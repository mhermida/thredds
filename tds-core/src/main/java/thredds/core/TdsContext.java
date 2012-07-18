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
	 * thredds directory within the context directory. It contains all the config files
	 */
	private static final String TDS_CONTEXT_DIRECTORY = "thredds";

	/*""
	 * thredds public directory.
	 */	
	private static final String TDS_PUBLIC_DIRECTORY = TDS_CONTEXT_DIRECTORY+"/public";	

	@Value("${tds.context.root.path}")
	private String contextRootPathStr;

	@Value("${tds.context.startup.path}")
	private String contextStartupPathStr;	

	@Value("${tds.config.file}")
	private String configFileName;
	
	private Path contextRootPath;
	private Path contextStartup;
	
	public void afterPropertiesSet(){

		contextRootPath = Paths.get(contextRootPathStr+"/"+TDS_CONTEXT_DIRECTORY);
		contextStartup = Paths.get(contextStartupPathStr);	

		if(!Files.exists(Paths.get( contextRootPathStr))){

			try{	
				Files.createDirectories(contextRootPath);
			}catch(IOException ioe){
				//Unable to create context directory
				log.error("Unable to create context directory: "+contextRootPath.toAbsolutePath().toString());
			}

			try{
				copyStartupContent(contextStartup, contextRootPath );
			}catch(IOException ioe){
				//Unable to copy startup content in context directory
				log.error("Unable to copy startup content in context directory: "+contextRootPath.toAbsolutePath().toString());		    	
			}
		}

		String configFilePathStr = contextRootPath+"/"+configFileName;
		try {			
			ThreddsConfig.init(configFilePathStr);
			
		} catch (FileNotFoundException e) {
			log.error("Config file: "+configFilePathStr+" not found");		
		}
	}

	public String getContextRootPath(){
		return contextRootPathStr;
	}

	public String getContextDirectory(){
		return  Paths.get(contextRootPathStr+"/"+TDS_CONTEXT_DIRECTORY).toAbsolutePath().toString();
	}
	
	public String getPublicDocFileDirectory(){
		
		//Path publicPath = Paths.get( TDS_PUBLIC_DIRECTORY );
		//Path contextPath = Paths.get( getContextDirectory() );				
		//return Paths.get( TDS_PUBLIC_DIRECTORY ).toAbsolutePath().normalize().toString();
		return Paths.get(getContextRootPath()+"/"+TDS_PUBLIC_DIRECTORY).toAbsolutePath().toString();
	}
	
	public File getFileInContext(String relPath) throws FileNotFoundException{
		Path filePath = Paths.get(relPath);
		filePath = contextRootPath.resolve(filePath);
		if( !Files.exists(filePath) )
			throw new FileNotFoundException();
		
		return filePath.toAbsolutePath().toFile();
	}
	
	public String getContextPath(){
		return TDS_CONTEXT_DIRECTORY;
	}
	
	private void copyStartupContent(final Path sourceDir, final Path destDir) throws IOException{

		Files.walkFileTree( Paths.get(contextStartupPathStr), new SimpleFileVisitor<Path>(){ 

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
