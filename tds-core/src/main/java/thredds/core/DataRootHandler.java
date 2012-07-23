/*
 * Copyright 1998-2009 University orporation for Atmospheric Research/Unidata
 *
 * Portions of this software were developed by the Unidata Program at the
 * University Corporation for Atmospheric Research.
 *
 * Access and use of this software shall impose the following obligations
 * and understandings on the user. The user is granted the right, without
 * any fee or cost, to use, copy, modify, alter, enhance and distribute
 * this software, and any derivative works thereof, and its supporting
 * documentation for any purpose whatsoever, provided that this entire
 * notice appears in all copies of the software, derivative works and
 * supporting documentation.  Further, UCAR requests that the user credit
 * UCAR/Unidata in any publications that result from the use of this
 * software or in any product that includes this software. The names UCAR
 * and/or Unidata, however, may not be used in any advertising or publicity
 * to endorse or promote any products or commercial entity unless specific
 * written permission is obtained from UCAR/Unidata. The user also
 * understands that UCAR/Unidata is not obligated to provide the user with
 * any support, consulting, training or assistance of any kind with regard
 * to the use, operation and performance of this software nor to provide
 * the user with any updates, revisions, new versions or "bug fixes."
 *
 * THIS SOFTWARE IS PROVIDED BY UCAR/UNIDATA "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL UCAR/UNIDATA BE LIABLE FOR ANY SPECIAL,
 * INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING
 * FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
 * NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION
 * WITH THE ACCESS, USE OR PERFORMANCE OF THIS SOFTWARE.
 */
package thredds.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import thredds.catalog.DataRootConfig;
import thredds.catalog.InvCatalog;
import thredds.catalog.InvCatalogFactory;
import thredds.catalog.InvCatalogImpl;
import thredds.catalog.InvCatalogRef;
import thredds.catalog.InvDataset;
import thredds.catalog.InvDatasetFeatureCollection;
import thredds.catalog.InvDatasetFmrc;
import thredds.catalog.InvDatasetImpl;
import thredds.catalog.InvDatasetScan;
import thredds.catalog.InvProperty;
import thredds.catalog.InvService;
import thredds.cataloggen.ProxyDatasetHandler;
import thredds.crawlabledataset.CrawlableDataset;
import thredds.crawlabledataset.CrawlableDatasetFile;
import thredds.util.PathAliasReplacement;
import thredds.util.StartsWithPathAliasReplacement;
import ucar.nc2.time.CalendarDate;
import ucar.nc2.units.DateType;
import ucar.unidata.util.StringUtil2;

/**
 * The DataRootHandler manages all the "data roots" for a given web application
 * and provides mappings from URLs to catalog and data objects (e.g., InvCatalog
 * and CrawlableDataset).
 * <p/>
 * <p>
 * The "data roots" are read in from one or more trees of config catalogs and
 * are defined by the datasetScan and datasetRoot elements in the config
 * catalogs.
 * <p/>
 * <p>
 * Uses the singleton design pattern.
 * 
 * @author caron
 */
@Component
public final class DataRootHandler implements InitializingBean {

	@Autowired
	private TdsContext tdsContext;

	private boolean staticCache;

	private Map<String, InvCatalogImpl> staticCatalogHash; // Hash of static
	// catalogs, key
	// = path
	private Set<String> staticCatalogNames; // Hash of static catalogs, key =
	// path
	private List<ConfigListener> configListeners = new ArrayList<ConfigListener>();
	private List<PathAliasReplacement> dataRootLocationAliasExpanders = new ArrayList<PathAliasReplacement>();

	volatile private boolean isReinit = false;

	private Set<String> idHash = new HashSet<String>(); // Hash of ids, to look for duplicates
	//  PathMatcher is "effectively immutable"; use volatile for visibilty
	private volatile PathMatcher pathMatcher = new PathMatcher(); // collection of DataRoot objects

	static private DataRootHandler singleton = null;

	static private Logger log = LoggerFactory.getLogger(DataRootHandler.class);

	/**
	 * Constructor. Managed bean - dont do nuttin else !!
	 * 
	 * @param tdsContext
	 */
	// private DataRootHandler(TdsContext tdsContext) {
	// this.tdsContext = tdsContext;
	// }

	private DataRootHandler() {

	}

	public void afterPropertiesSet() {

		log.info("Initializating DataRootHandler...");
		// Registering first the AccessConfigListener
		registerConfigListener(new RestrictedAccessConfigListener());

		// Initialize any given DataRootLocationAliasExpanders that are
		// TdsConfiguredPathAliasReplacement
		dataRootLocationAliasExpanders.add(new StartsWithPathAliasReplacement(
				"content", tdsContext.getPublicDocFileDirectory()));

		this.initCatalogs();
		DataRootHandler.singleton = this;

	}

	public boolean registerConfigListener(ConfigListener cl) {
		if (cl == null)
			return false;
		if (configListeners.contains(cl))
			return false;
		return configListeners.add(cl);
	}

	public boolean unregisterConfigListener(ConfigListener cl) {
		if (cl == null)
			return false;
		return configListeners.remove(cl);
	}

	private void initCatalogs() {
		ArrayList<String> catList = new ArrayList<String>();
		catList.add("catalog.xml"); // always first
		ThreddsConfig.getCatalogRoots(catList);

		log.info("initCatalogs(): initializing " + catList.size()
				+ " root catalogs.");
		this.initCatalogs(catList);
	}

	public synchronized void initCatalogs(List<String> configCatalogRoots) {
		// Notify listeners of start of initialization if not reinit (in which
		// case it is already done).
		if (!isReinit)
			for (ConfigListener cl : configListeners)
				cl.configStart();
		isReinit = false;

		staticCache = ThreddsConfig.getBoolean("Catalog.cache", true); // user can turn off static catalog caching
		log.info("DataRootHandler: staticCache= " + staticCache);

		this.staticCatalogNames = new HashSet<String>();
		this.staticCatalogHash = new HashMap<String, InvCatalogImpl>();

		for (String path : configCatalogRoots) {
			try {
				path = StringUtils.cleanPath(path);
				log.info("\n**************************************\nCatalog init "+ path + "\n[" + CalendarDate.present() + "]");
				initCatalog(path, true, true);
			} catch (Throwable e) {
				log.error("initCatalogs(): Error initializing catalog " + path	+ "; " + e.getMessage(), e);
			}
		}

		for (ConfigListener cl : configListeners)
			cl.configEnd();
	}

	/**
	 * Reads a catalog, finds datasetRoot, datasetScan, datasetFmrc, NcML and
	 * restricted access datasets
	 * <p/>
	 * Only called by synchronized methods.
	 * 
	 * @param path
	 *            file path of catalog, reletive to contentPath, ie catalog
	 *            fullpath = contentPath + path.
	 * @param recurse
	 *            if true, look for catRefs in this catalog
	 * @param cache
	 *            if true, always cache
	 * @throws IOException
	 *             if reading catalog fails
	 */
	private void initCatalog(String path, boolean recurse, boolean cache)
			throws IOException {
		path = StringUtils.cleanPath(path);
		// File f = this.tdsContext.getConfigFileSource().getFile(path);
		File f = this.tdsContext.getFileInContext(path);

		if (f == null) {
			log.error("initCatalog(): Catalog [" + path
					+ "] does not exist in config directory.");
			return;
		}

		// make sure we dont already have it
		if (staticCatalogNames.contains(path)) {
			log.warn("initCatalog(): Catalog [" + path
					+ "] already seen, possible loop (skip).");
			return;
		}
		staticCatalogNames.add(path);

		// read it
		InvCatalogFactory factory = this.getCatalogFactory(true); // always
		// validate the config catalogs
		InvCatalogImpl cat = readCatalog(factory, path, f.getPath());
		if (cat == null) {
			log.warn("initCatalog(): failed to read catalog <" + f.getPath()
					+ ">.");
			return;
		}

		// Notify listeners of config catalog.
		for (ConfigListener cl : configListeners)
			cl.configCatalog(cat);

		// look for datasetRoots
		for (DataRootConfig p : cat.getDatasetRoots()) {
			addRoot(p, true);
		}

		// old style - in the service elements
		for (InvService s : cat.getServices()) {
			for (InvProperty p : s.getDatasetRoots()) {
				addRoot(p.getName(), p.getValue(), true);
			}
		}

		// get the directory path, relative to the contentPath
		int pos = path.lastIndexOf("/");
		String dirPath = (pos > 0) ? path.substring(0, pos + 1) : "";

		// look for datasetScans and NcML elements and Fmrc and
		// featureCollections
		boolean needsCache = initSpecialDatasets(cat.getDatasets());

		// optionally add catalog to cache
		if (staticCache || cache || needsCache) {
			cat.setStatic(true);
			staticCatalogHash.put(path, cat);
			if (log.isDebugEnabled())
				log.debug("  add static catalog to hash=" + path);
		}

		if (recurse) {
			initFollowCatrefs(dirPath, cat.getDatasets());
		}
	}


	/**
	 * Finds datasetScan, datasetFmrc, NcML and restricted access datasets.
	 * Look for duplicate Ids (give message). Dont follow catRefs.
	 * Only called by synchronized methods.
	 *
	 * @param dsList the list of InvDatasetImpl
	 * @return true if the containing catalog should be cached
	 */
	private boolean initSpecialDatasets(List<InvDataset> dsList) {
		boolean needsCache = false;

		Iterator<InvDataset> iter = dsList.iterator();
		while (iter.hasNext()) {
			InvDatasetImpl invDataset = (InvDatasetImpl) iter.next();

			// look for duplicate ids
			String id = invDataset.getUniqueID();
			if (id != null) {
				if (idHash.contains(id)) {
					log.warn("Duplicate id on  '" + invDataset.getFullName() + "' id= '" + id + "'");
				} else {
					idHash.add(id);
				}
			}

			// Notify listeners of config datasets.
			for (ConfigListener cl : configListeners)
				cl.configDataset(invDataset);

			if (invDataset instanceof InvDatasetScan) {
				InvDatasetScan ds = (InvDatasetScan) invDataset;
				InvService service = ds.getServiceDefault();
				if (service == null) {
					log.error("InvDatasetScan " + ds.getFullName() + " has no default Service - skipping");
					continue;
				}
				if (!addRoot(ds))
					iter.remove();

			} else if (invDataset instanceof InvDatasetFmrc) {
				InvDatasetFmrc fmrc = (InvDatasetFmrc) invDataset;
				addRoot(fmrc);
				needsCache = true;

			} else if (invDataset instanceof InvDatasetFeatureCollection) {
				InvDatasetFeatureCollection fc = (InvDatasetFeatureCollection) invDataset;
				addRoot(fc);
				needsCache = true;

				// not a DatasetScan or InvDatasetFmrc or InvDatasetFeatureCollection
			} else if (invDataset.getNcmlElement() != null) {
				DatasetHandler.putNcmlDataset(invDataset.getUrlPath(), invDataset);
			}

			if (!(invDataset instanceof InvCatalogRef)) {
				// recurse
				initSpecialDatasets(invDataset.getDatasets());
			}
		}

		return needsCache;
	}	


	/**
	 * Find the longest match for this path.
	 *
	 * @param fullpath the complete path name
	 * @return best DataRoot or null if no match.
	 */
	private DataRoot findDataRoot(String fullpath) {
		if ((fullpath.length() > 0) && (fullpath.charAt(0) == '/'))
			fullpath = fullpath.substring(1);

		return (DataRoot) pathMatcher.match(fullpath);
	}	


	public DataRootMatch findDataRootMatch(String spath) {
		if (spath.startsWith("/"))
			spath = spath.substring(1);
		DataRoot dataRoot = findDataRoot(spath);
		if (dataRoot == null)
			return null;

		DataRootMatch match = new DataRootMatch();
		match.rootPath = dataRoot.path;
		match.remaining = spath.substring(match.rootPath.length());
		if (match.remaining.startsWith("/"))
			match.remaining = match.remaining.substring(1);
		match.dirLocation = dataRoot.dirLocation;
		match.dataRoot = dataRoot;
		return match;
	}


	public Map<String, InvCatalogImpl> getStaticCatalogs(){

		return staticCatalogHash;
	}

	public List<DataRoot> getDataRoots(){

		Iterator<Object> it = pathMatcher.iterator();

		List<DataRoot> dataRoots = new ArrayList<DataRoot>();
		while(it.hasNext()){
			Object o = it.next();
			if( o instanceof DataRoot ){
				DataRoot dr = (DataRoot)o;
				dataRoots.add(dr);
			}
			if( o instanceof String ){
				dataRoots.add(null);
			}			
		}

		return dataRoots;
	}


	/**
	 * Get the singleton.
	 * <p/>
	 * The setInstance() method must be called before this method is called.
	 *
	 * @return the singleton instance.
	 * @throws IllegalStateException if setInstance() has not been called.
	 */
	static public DataRootHandler getInstance() {
		if (singleton == null) {
			log.error("getInstance(): Called without setInstance() having been called.");
			throw new IllegalStateException("setInstance() must be called first.");
		}
		return singleton;
	}	

	private InvCatalogFactory getCatalogFactory(boolean validate) {
		InvCatalogFactory factory = InvCatalogFactory
				.getDefaultFactory(validate);
		if (!this.dataRootLocationAliasExpanders.isEmpty())
			factory.setDataRootLocationAliasExpanders(this.dataRootLocationAliasExpanders);
		return factory;
	}

	// Only called by synchronized methods
	private void initFollowCatrefs(String dirPath, List<InvDataset> datasets) throws IOException {
		for (InvDataset invDataset : datasets) {

			if ((invDataset instanceof InvCatalogRef) && !(invDataset instanceof InvDatasetScan) && !(invDataset instanceof InvDatasetFmrc)
					&& !(invDataset instanceof InvDatasetFeatureCollection)) {
				InvCatalogRef catref = (InvCatalogRef) invDataset;
				String href = catref.getXlinkHref();
				if (log.isDebugEnabled()) log.debug("  catref.getXlinkHref=" + href);

				// Check that catRef is relative
				if (!href.startsWith("http:")) {
					// Clean up relative URLs that start with "./"
					if (href.startsWith("./")) {
						href = href.substring(2);
					}

					String path;
					String contextPathPlus = this.tdsContext.getContextPath() + "/";
					if (href.startsWith(contextPathPlus)) {
						path = href.substring(contextPathPlus.length()); // absolute starting from content root
					} else if (href.startsWith("/")) {
						// Drop the catRef because it points to a non-TDS served catalog.
						log.warn("**Warning: Skipping catalogRef <xlink:href=" + href + ">. Reference is relative to the server outside the context path [" + contextPathPlus + "]. " +
								"Parent catalog info: Name=\"" + catref.getParentCatalog().getName() + "\"; Base URI=\"" + catref.getParentCatalog().getUriString() + "\"; dirPath=\"" + dirPath + "\".");
						continue;
					} else {
						path = dirPath + href;  // reletive starting from current directory
					}

					initCatalog(path, true, false);
				}

			} else if (!(invDataset instanceof InvDatasetScan) && !(invDataset instanceof InvDatasetFmrc) && !(invDataset instanceof InvDatasetFeatureCollection)) {
				// recurse through nested datasets
				initFollowCatrefs(dirPath, invDataset.getDatasets());
			}
		}
	}	

	// Only called by synchronized methods
	private boolean addRoot(InvDatasetScan dscan) {
		// check for duplicates
		String path = dscan.getPath();

		if (path == null) {
			log.error("**Error: " + dscan.getFullName() + " missing a path attribute.");
			return false;
		}

		DataRoot droot = (DataRoot) pathMatcher.get(path);
		if (droot != null) {
			if (!droot.dirLocation.equals(dscan.getScanLocation())) {
				log.error("**Error: already have dataRoot =<" + path + ">  mapped to directory= <" + droot.dirLocation + ">" +
						" wanted to map to fmrc=<" + dscan.getScanLocation() + "> in catalog " + dscan.getParentCatalog().getUriString());
			}

			return false;
		}

		// Check whether InvDatasetScan is valid before adding.
		if (!dscan.isValid()) {
			log.error(dscan.getInvalidMessage() + "\n... Dropping this datasetScan [" + path + "].");
			return false;
		}

		// add it
		droot = new DataRoot(dscan);
		pathMatcher.put(path, droot);

		log.debug(" added rootPath=<" + path + ">  for directory= <" + dscan.getScanLocation() + ">");
		return true;
	}

	// Only called by synchronized methods
	private boolean addRoot(InvDatasetFeatureCollection fc) {
		// check for duplicates
		String path = fc.getPath();

		if (path == null) {
			log.error(fc.getFullName() + " missing a path attribute.");
			return false;
		}

		DataRoot droot = (DataRoot) pathMatcher.get(path);
		if (droot != null) {
			log.error("**Error: already have dataRoot =<" + path + ">  mapped to directory= <" + droot.dirLocation + ">" +
					" wanted to use by FeatureCollection Dataset =<" + fc.getFullName() + ">");
			return false;
		}

		// add it
		droot = new DataRoot(fc);

		if (droot.dirLocation != null) {
			File file = new File(droot.dirLocation);
			if (!file.exists()) {
				log.error("**Error: DatasetFmrc =" + droot.path + " directory= <" + droot.dirLocation + "> does not exist");
				return false;
			}
		}

		pathMatcher.put(path, droot);

		log.debug(" added rootPath=<" + path + ">  for feature collection= <" + fc.getFullName() + ">");
		return true;
	}	

	// Only called by synchronized methods
	private boolean addRoot(InvDatasetFmrc fmrc) {
		// check for duplicates
		String path = fmrc.getPath();

		if (path == null) {
			log.error(fmrc.getFullName() + " missing a path attribute.");
			return false;
		}

		DataRoot droot = (DataRoot) pathMatcher.get(path);
		if (droot != null) {
			log.error("**Error: already have dataRoot =<" + path + ">  mapped to directory= <" + droot.dirLocation + ">" +
					" wanted to use by FMRC Dataset =<" + fmrc.getFullName() + ">");
			return false;
		}

		// add it
		droot = new DataRoot(fmrc);

		if (droot.dirLocation != null) {
			File file = new File(droot.dirLocation);
			if (!file.exists()) {
				log.error("**Error: DatasetFmrc =" + droot.path + " directory= <" + droot.dirLocation + "> does not exist");
				return false;
			}
		}

		pathMatcher.put(path, droot);

		log.debug(" added rootPath=<" + path + ">  for fmrc= <" + fmrc.getFullName() + ">");
		return true;
	}	

	/**
	 * Does the actual work of reading a catalog.
	 * 
	 * @param factory
	 *            use this InvCatalogFactory
	 * @param path
	 *            reletive path starting from content root
	 * @param catalogFullPath
	 *            absolute location on disk
	 * @return the InvCatalogImpl, or null if failure
	 */
	private InvCatalogImpl readCatalog(InvCatalogFactory factory, String path,
			String catalogFullPath) {
		URI uri;
		try {
			uri = new URI("file:"
					+ StringUtil2.escape(catalogFullPath, "/:-_.")); // LOOK
			// needed
			// ?
		} catch (URISyntaxException e) {
			log.error("readCatalog(): URISyntaxException="
					+ e.getMessage());
			return null;
		}

		// read the catalog
		log.info("readCatalog(): full path=" + catalogFullPath
				+ "; path=" + path);
		InvCatalogImpl cat = null;
		FileInputStream ios = null;
		try {
			ios = new FileInputStream(catalogFullPath);
			cat = factory.readXML(ios, uri);

			StringBuilder sbuff = new StringBuilder();
			if (!cat.check(sbuff)) {
				log.error("   invalid catalog -- "
						+ sbuff.toString());
				return null;
			}
			log.info("   valid catalog -- " + sbuff.toString());

		} catch (Throwable t) {
			String msg = (cat == null) ? "null catalog" : cat.getLog();
			log.error("  Exception on catalog=" + catalogFullPath
					+ " " + t.getMessage() + "\n log=" + msg, t);
			return null;

		} finally {
			if (ios != null) {
				try {
					ios.close();
				} catch (IOException e) {
					log.error("  error closing" + catalogFullPath);
				}
			}
		}

		return cat;
	}

	// Only called by synchronized methods
	private boolean addRoot(String path, String dirLocation, boolean wantErr) {
		// check for duplicates
		DataRoot droot = (DataRoot) pathMatcher.get(path);
		if (droot != null) {
			if (wantErr)
				log.error("**Error: already have dataRoot =<" + path + ">  mapped to directory= <" + droot.dirLocation + ">" +
						" wanted to map to <" + dirLocation + ">");

			return false;
		}

		File file = new File(dirLocation);
		if (!file.exists()) {
			log.error("**Error: Data Root =" + path + " directory= <" + dirLocation + "> does not exist");
			return false;
		}

		// add it
		droot = new DataRoot(path, dirLocation, true);
		pathMatcher.put(path, droot);

		log.debug(" added rootPath=<" + path + ">  for directory= <" + dirLocation + ">");
		return true;
	}	

	// Only called by synchronized methods
	private boolean addRoot(DataRootConfig config, boolean wantErr) {
		String path = config.getName();
		String location = config.getValue();
		// check for duplicates
		DataRoot droot = (DataRoot) pathMatcher.get(path);
		if (droot != null) {
			if (wantErr)
				log.error("**Error: already have dataRoot =<" + path + ">  mapped to directory= <" + droot.dirLocation + ">" +
						" wanted to map to <" + location + ">");

			return false;
		}

		File file = new File(location);
		if (!file.exists()) {
			log.error("**Error: Data Root =" + path + " directory= <" + location + "> does not exist");
			return false;
		}

		// add it
		droot = new DataRoot(path, location, config.isCache());
		pathMatcher.put(path, droot);

		log.debug(" added rootPath=<" + path + ">  for directory= <" + location + ">");
		return true;
	}


	////////////////////////////////////////////////////////////////////////////////////////

	public class DataRootMatch {
		String rootPath;     // this is the matching part of the URL
		String remaining;   // this is the part of the URL that didnt match
		String dirLocation;   // this is the directory that should be substituted for the rootPath
		DataRoot dataRoot;  // this is the directory that should be substituted for the rootPath
	}

	public class DataRoot {
		String path;         // match this path
		String dirLocation;  // to this directory
		InvDatasetScan scan; // the InvDatasetScan that created this (may be null)
		InvDatasetFmrc fmrc; // the InvDatasetFmrc that created this (may be null)
		InvDatasetFeatureCollection featCollection; // the InvDatasetFeatureCollection that created this (may be null)
		boolean cache = true;

		// Use this to access CrawlableDataset in dirLocation.
		// I.e., used by datasets that reference a <datasetRoot>
		InvDatasetScan datasetRootProxy;

		DataRoot(InvDatasetFeatureCollection featCollection) {
			this.path = featCollection.getPath();
			this.featCollection = featCollection;
			this.dirLocation = featCollection.getTopDirectoryLocation();
			log.info(" DataRoot adding featureCollection {}\n", featCollection.getConfig());
		}

		DataRoot(InvDatasetFmrc fmrc) {
			this.path = fmrc.getPath();
			this.fmrc = fmrc;

			InvDatasetFmrc.InventoryParams params = fmrc.getFmrcInventoryParams();
			if (null != params)
				dirLocation = params.location;
		}

		DataRoot(InvDatasetScan scan) {
			this.path = scan.getPath();
			this.scan = scan;
			this.dirLocation = scan.getScanLocation();
			this.datasetRootProxy = null;
		}

		DataRoot(String path, String dirLocation, boolean cache) {
			this.path = path;
			this.dirLocation = dirLocation;
			this.cache = cache;
			this.scan = null;

			makeProxy();
		}

		void makeProxy() {
			/*   public InvDatasetScan( InvDatasetImpl parent, String name, String path, String scanLocation,
	                         String configClassName, Object configObj, CrawlableDatasetFilter filter,
	                         CrawlableDatasetLabeler identifier, CrawlableDatasetLabeler namer,
	                         boolean addDatasetSize,
	                         CrawlableDatasetSorter sorter, Map proxyDatasetHandlers,
	                         List childEnhancerList, CatalogRefExpander catalogRefExpander ) */
			this.datasetRootProxy = new InvDatasetScan(null, "", this.path, this.dirLocation,
					null, null, null, null, null, false, null, null, null, null);
		}


		// used by PathMatcher
		public String toString() {
			return path;
		}

		// debug
		public String toString2() {
			return path + "," + dirLocation;
		}

		/**
		 * Instances which have same path are equal.
		 */
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			DataRoot root = (DataRoot) o;
			return path.equals(root.path);
		}

		public int hashCode() {
			return path.hashCode();
		}
	}

	/**
	 * Return the java.io.File represented by the CrawlableDataset to which the
	 * given path maps. Null is returned if the dataset does not exist, the
	 * matching InvDatasetScan or DataRoot filters out the requested
	 * CrawlableDataset, the CrawlableDataset does not represent a File
	 * (i.e., it is not a CrawlableDatasetFile), or an I/O error occurs whil
	 * locating the requested dataset.
	 *
	 * @param path the request path.
	 * @return the requested java.io.File or null.
	 * @throws IllegalStateException if the request is not for a descendant of (or the same as) the matching DatasetRoot collection location.
	 */
	public File getCrawlableDatasetAsFile(String path) {
		if (path.length() > 0) {
			if (path.startsWith("/"))
				path = path.substring(1);
		}

		// hack in the fmrc for fileServer
		DataRootMatch match = findDataRootMatch(path);
		if (match == null)
			return null;
		if (match.dataRoot.fmrc != null)
			return match.dataRoot.fmrc.getFile(match.remaining);
		if (match.dataRoot.featCollection != null)
			return match.dataRoot.featCollection.getFile(match.remaining);


		CrawlableDataset crDs;
		try {
			crDs = getCrawlableDataset(path);
		} catch (IOException e) {
			return null;
		}
		if (crDs == null) return null;
		File retFile = null;
		if (crDs instanceof CrawlableDatasetFile)
			retFile = ((CrawlableDatasetFile) crDs).getFile();

		return retFile;
	}	

	/**
	 * Try to match the given path with all available data roots. If match, then see if there is an NcML document associated
	 * with the path.
	 *
	 * @param path the reletive path, ie req.getServletPath() + req.getPathInfo()
	 * @return the NcML (as a JDom element) assocated assocated with this path, or null if no dataroot matches, or no associated NcML.
	 */
	public org.jdom.Element getNcML(String path) {
		if (path.startsWith("/"))
			path = path.substring(1);

		DataRoot dataRoot = findDataRoot(path);
		if (dataRoot == null) {
			if (log.isDebugEnabled()) log.debug("_getNcML no InvDatasetScan for =" + path);
			return null;
		}

		InvDatasetScan dscan = dataRoot.scan;
		if (dscan == null) dscan = dataRoot.datasetRootProxy;
		if (dscan == null) return null;
		return dscan.getNcmlElement();
	}	


	/**
	 * Return the CrawlableDataset to which the given path maps, null if the
	 * dataset does not exist or the matching InvDatasetScan filters out the
	 * requested CrawlableDataset.
	 * <p/>
	 * Use this method to check that a data request is requesting an allowed dataset.
	 *
	 * @param path the request path.
	 * @return the requested CrawlableDataset or null if the requested dataset is not allowed by the matching InvDatasetScan.
	 * @throws IOException if an I/O error occurs while locating the requested dataset.
	 */
	public CrawlableDataset getCrawlableDataset(String path)
			throws IOException {
		if (path.length() > 0) {
			if (path.startsWith("/"))
				path = path.substring(1);
		}

		DataRoot reqDataRoot = findDataRoot(path);
		if (reqDataRoot == null)
			return null;

		if (reqDataRoot.scan != null)
			return reqDataRoot.scan.requestCrawlableDataset(path);

		if (reqDataRoot.fmrc != null)
			return null; // if fmrc exists, bail out and deal with it in caller

		if (reqDataRoot.featCollection != null)
			return null; // if featCollection exists, bail out and deal with it in caller

		// must be a data root
		if (reqDataRoot.dirLocation != null) {
			if (reqDataRoot.datasetRootProxy == null)
				reqDataRoot.makeProxy();
			return reqDataRoot.datasetRootProxy.requestCrawlableDataset(path);
		}

		return null;
	}


	public InvCatalog getCatalog(String path, URI baseURI) throws FileNotFoundException {

		if (path == null)
			return null;

		String workPath = path;
		if (workPath.startsWith("/"))
			workPath = workPath.substring(1);

		// Check for static catalog.
		boolean reread = false;
		InvCatalogImpl catalog = staticCatalogHash.get(workPath);
		if (catalog != null) {  // see if its stale
			DateType expiresDateType = catalog.getExpires();
			if ((expiresDateType != null) && expiresDateType.getDate().getTime() < System.currentTimeMillis())
				reread = true;

		} else if (!staticCache) {
			reread = staticCatalogNames.contains(workPath); // see if we know if its a static catalog
		}

		// its a static catalog that needs to be read
		if (reread) {
			//File catFile = this.tdsContext.getConfigFileSource().getFile(workPath);
			File catFile = this.tdsContext.getFileInContext(workPath);
			if (catFile != null) {
				String catalogFullPath = catFile.getPath();
				log.info("**********\nReading catalog {} at {}\n", catalogFullPath, CalendarDate.present());

				InvCatalogFactory factory = getCatalogFactory(true);
				InvCatalogImpl reReadCat = readCatalog(factory, workPath, catalogFullPath);

				if (reReadCat != null) {
					catalog = reReadCat;
					if (staticCache) { // a static catalog has been updated
						synchronized (this) {
							reReadCat.setStatic(true);
							staticCatalogHash.put(workPath, reReadCat);
						}
					}
				}

			} else {
				log.error("Static catalog does not exist that we expected = " + workPath);
			}
		}

		// if ((catalog != null) && catalog.getBaseURI() == null) { for some reason you have to keep setting - is someone setting to null ?
		if (catalog != null) {
			// this is the first time we actually know an absolute, external path for the catalog, so we set it here
			// LOOK however, this causes a possible thread safety problem
			catalog.setBaseURI(baseURI);
		}

		// Check for dynamic catalog.
		if (catalog == null)
			catalog = makeDynamicCatalog(workPath, baseURI);

		// Check for proxy dataset resolver catalog.
		if (catalog == null && this.isProxyDatasetResolver(workPath))
			catalog = (InvCatalogImpl) this.getProxyDatasetResolverCatalog(workPath, baseURI);

		return catalog;		
	}


	public boolean isProxyDataset(String path) {
		ProxyDatasetHandler pdh = this.getMatchingProxyDataset(path);
		return pdh != null;
	}

	public boolean isProxyDatasetResolver(String path) {
		ProxyDatasetHandler pdh = this.getMatchingProxyDataset(path);
		if (pdh == null)
			return false;

		return pdh.isProxyDatasetResolver();
	}


	private ProxyDatasetHandler getMatchingProxyDataset(String path) {
		InvDatasetScan scan = this.getMatchingScan(path);
		if (null == scan) return null;

		int index = path.lastIndexOf("/");
		String proxyName = path.substring(index + 1);

		Map pdhMap = scan.getProxyDatasetHandlers();
		if (pdhMap == null) return null;

		return (ProxyDatasetHandler) pdhMap.get(proxyName);
	}

	private InvDatasetScan getMatchingScan(String path) {
		DataRoot reqDataRoot = findDataRoot(path);
		if (reqDataRoot == null)
			return null;

		InvDatasetScan scan = null;
		if (reqDataRoot.scan != null)
			scan = reqDataRoot.scan;
		else if (reqDataRoot.fmrc != null)  // TODO refactor UGLY FMRC HACK
			scan = reqDataRoot.fmrc.getRawFileScan();
		else if (reqDataRoot.featCollection != null)  // TODO refactor UGLY FMRC HACK
			scan = reqDataRoot.featCollection.getRawFileScan();

		return scan;
	}

	public InvCatalog getProxyDatasetResolverCatalog(String path, URI baseURI) {
		if (!isProxyDatasetResolver(path))
			throw new IllegalArgumentException("Not a proxy dataset resolver path <" + path + ">.");

		InvDatasetScan scan = this.getMatchingScan(path);

		// Call the matching InvDatasetScan to make the proxy dataset resolver catalog.
		//noinspection UnnecessaryLocalVariable
		InvCatalogImpl cat = scan.makeProxyDsResolverCatalog(path, baseURI);

		return cat;
	}		  



	private InvCatalogImpl makeDynamicCatalog(String path, URI baseURI) {
		String workPath = path;

		// Make sure this is a dynamic catalog request.
		if (!path.endsWith("/catalog.xml"))
			return null;

		// strip off the filename
		int pos = workPath.lastIndexOf("/");
		if (pos >= 0)
			workPath = workPath.substring(0, pos);

		// now look through the InvDatasetScans for a maximal match
		DataRootMatch match = findDataRootMatch(workPath);
		if (match == null)
			return null;

		// look for the fmrc
		if (match.dataRoot.fmrc != null) {
			return match.dataRoot.fmrc.makeCatalog(match.remaining, path, baseURI);
		}

		// look for the feature Collection
		if (match.dataRoot.featCollection != null) {
			return match.dataRoot.featCollection.makeCatalog(match.remaining, path, baseURI);
		}

		// Check that path is allowed, ie not filtered out
		try {
			if (getCrawlableDataset(workPath) == null)
				return null;
		} catch (IOException e) {
			log.error("makeDynamicCatalog(): I/O error on request <" + path + ">: " + e.getMessage(), e);
			return null;
		}

		// at this point, its gotta be a DatasetScan, not a DatasetRoot
		if (match.dataRoot.scan == null) {
			log.warn("makeDynamicCatalog(): No InvDatasetScan for =" + workPath + " request path= " + path);
			return null;
		}

		InvDatasetScan dscan = match.dataRoot.scan;
		if (log.isDebugEnabled())
			log.debug("makeDynamicCatalog(): Calling makeCatalogForDirectory( " + baseURI + ", " + path + ").");
		InvCatalogImpl cat = dscan.makeCatalogForDirectory(path, baseURI);

		if (null == cat) {
			log.error("makeDynamicCatalog(): makeCatalogForDirectory failed = " + workPath);
		}

		return cat;
	}	


	/**
	 * To receive notice of TDS configuration events, implement this interface
	 * and use the DataRootHandler.registerConfigListener() method to register
	 * an instance with a DataRootHandler instance.
	 * <p/>
	 * Configuration events include start and end of configuration, inclusion of
	 * a catalog in configuration, and finding a dataset in configuration.
	 * <p/>
	 * Concurrency issues:<br>
	 * 1) As this is a servlet framework, requests that configuration be
	 * reinitialized may occur concurrently with requests for the information
	 * the listener is keeping. Be careful not to allow access to the
	 * information during configuration.<br>
	 * 2) The longer the configuration process, the more time there is to have a
	 * concurrency issue come up. So, try to keep responses to these events
	 * fairly light weight. Or build new configuration information and
	 * synchronize and switch only when the already built config information is
	 * being switched with the existing config information.
	 */
	public interface ConfigListener {
		/**
		 * Recieve notification that configuration has started.
		 */
		public void configStart();

		/**
		 * Recieve notification that configuration has completed.
		 */
		public void configEnd();

		/**
		 * Recieve notification on the inclusion of a configuration catalog.
		 * 
		 * @param catalog
		 *            the catalog being included in configuration.
		 */
		public void configCatalog(InvCatalog catalog);

		/**
		 * Recieve notification that configuration has found a dataset.
		 * 
		 * @param dataset
		 *            the dataset found during configuration.
		 */
		public void configDataset(InvDataset dataset);
	}

}
