/*
 * Copyright 1998-2009 University Corporation for Atmospheric Research/Unidata
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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Set;

import thredds.catalog.InvDatasetFeatureCollection;
import thredds.catalog.InvDatasetFmrc;
import thredds.catalog.InvDatasetImpl;
import thredds.cataloggen.config.DatasetSource;
import ucar.nc2.NetcdfFile;
import ucar.nc2.dataset.NetcdfDataset;
import ucar.nc2.dt.GridDataset;
import ucar.nc2.ncml.NcMLReader;
import ucar.nc2.util.cache.FileFactory;


/**
 * CDM Datasets.
 * 1) if dataset with ncml, open that
 * 2) if datasetScan with ncml, wrap
 */
public class DatasetHandler {
	static private final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DatasetHandler.class);

	// InvDataset (not DatasetScan, DatasetFmrc) that have an NcML element in it. key is the request Path
	static private HashMap<String, InvDatasetImpl> ncmlDatasetHash = new HashMap<String, InvDatasetImpl>();

	/**
	 * Open a file as a GridDataset, using getNetcdfFile(), so that it gets wrapped in NcML if needed.
	 * @param req the request
	 * @param res the response
	 * @param reqPath the request path
	 * @param enhanceMode optional enhance mode or null
	 * @return GridDataset
	 * @throws IOException on read error
	 */
	static public GridDataset openGridDataset(  String reqPath, Set<NetcdfDataset.Enhance> enhanceMode) throws IOException {

		// first look for a grid feature collection
		DataRootHandler.DataRootMatch match = DataRootHandler.getInstance().findDataRootMatch(reqPath);
		if ((match != null) && (match.dataRoot.featCollection != null)) {
			InvDatasetFeatureCollection featCollection = match.dataRoot.featCollection;
			if (log.isDebugEnabled()) log.debug("  -- DatasetHandler found InvDatasetFeatureCollection= " + featCollection);
			GridDataset gds = featCollection.getGridDataset(match.remaining);
			if (gds == null) throw new FileNotFoundException(reqPath);
			return gds;
		}

		// fetch it as a NetcdfFile; this deals with possible NcML
		NetcdfFile ncfile = getNetcdfFile(reqPath);
		if (ncfile == null) return null;

		NetcdfDataset ncd = null;
		try {
			// Convert to NetcdfDataset
			ncd = NetcdfDataset.wrap( ncfile, enhanceMode );
			return new ucar.nc2.dt.grid.GridDataset(ncd);

		} catch ( Throwable t ) {
			if ( ncd == null )
				ncfile.close();
			else
				ncd.close();

			if ( t instanceof IOException)
				throw (IOException) t;

			String msg = ncd == null ? "Problem wrapping NetcdfFile in NetcdfDataset"
					: "Problem creating GridDataset from NetcdfDataset";
			log.error( "openGridDataset(): " + msg, t);
			throw new IOException( msg + t.getMessage());
		}
	} 

	// return null means request has been handled, and calling routine should exit without further processing
	static public NetcdfFile getNetcdfFile(String reqPath) throws IOException {
		if (log.isDebugEnabled()) log.debug("DatasetHandler wants " + reqPath);
		//if (debugResourceControl) System.out.println("getNetcdfFile = " + ServletUtil.getRequest(req));

		if (reqPath == null)
			return null;

		if (reqPath.startsWith("/"))
			reqPath = reqPath.substring(1);

		// see if its under resource control
		if (!resourceControlOk( reqPath))
			return null;

		// look for a dataset (non scan, non fmrc) that has an ncml element
		InvDatasetImpl ds = ncmlDatasetHash.get(reqPath);
		if (ds != null) {
			if (log.isDebugEnabled()) log.debug("  -- DatasetHandler found NcmlDataset= " + ds);
			//String cacheName = ds.getUniqueID(); // LOOK use reqPath !!

			NetcdfFile ncfile = NetcdfDataset.acquireFile(new NcmlFileFactory(ds), null, reqPath, -1, null, null);
			if (ncfile == null) throw new FileNotFoundException(reqPath);
			return ncfile;
		}

		// look for a match
		DataRootHandler.DataRootMatch match = DataRootHandler.getInstance().findDataRootMatch(reqPath);

		// look for an fmrc dataset
		if ((match != null) && (match.dataRoot.fmrc != null)) {
			InvDatasetFmrc fmrc = match.dataRoot.fmrc;
			if (log.isDebugEnabled()) log.debug("  -- DatasetHandler found InvDatasetFmrc= " + fmrc);
			NetcdfFile ncfile = fmrc.getDataset(match.remaining);
			if (ncfile == null) throw new FileNotFoundException(reqPath);
			return ncfile;
		}

		// look for an feature collection dataset
		if ((match != null) && (match.dataRoot.featCollection != null)) {
			InvDatasetFeatureCollection featCollection = match.dataRoot.featCollection;
			if (log.isDebugEnabled()) log.debug("  -- DatasetHandler found InvDatasetFeatureCollection= " + featCollection);
			NetcdfFile ncfile = featCollection.getNetcdfDataset(match.remaining);
			if (ncfile == null) throw new FileNotFoundException(reqPath);
			return ncfile;
		}

		// might be a pluggable DatasetSource
		NetcdfFile ncfile = null;
//		for (DatasetSource datasetSource : sourceList) {
//			if (datasetSource.isMine(req)) {
//				ncfile = datasetSource.getNetcdfFile(req, res);
//				if (ncfile == null) return null;
//			}
//		}

		// common case - its a file
		if (ncfile == null) {
			boolean cache = true; // hack in a "no cache" option
			if ((match != null) && (match.dataRoot != null)) {
				cache = match.dataRoot.cache;
			}

			// otherwise, must have a datasetRoot in the path
			File file = DataRootHandler.getInstance().getCrawlableDatasetAsFile(reqPath);
			if (file == null) {
				throw new FileNotFoundException(reqPath);
			}

			if (cache)
				ncfile = NetcdfDataset.acquireFile(file.getPath(), null);
			else
				ncfile = NetcdfDataset.openFile(file.getPath(), null);
			if (ncfile == null) throw new FileNotFoundException(reqPath);
		}

		// wrap with ncml if needed : for DatasetScan only
		org.jdom.Element netcdfElem = DataRootHandler.getInstance().getNcML(reqPath);
		if (netcdfElem != null) {
			NetcdfDataset ncd = NetcdfDataset.wrap(ncfile, null); // do not enhance !!
			new NcMLReader().readNetcdf(reqPath, ncd, ncd, netcdfElem, null);
			if (log.isDebugEnabled()) log.debug("  -- DatasetHandler found DataRoot NcML = " + ds);
			return ncd;
		}

		return ncfile;
	}

	/**
	 * Check if this is making a request for a restricted dataset, and if so, if its allowed.
	 *
	 * @param req the request
	 * @param res the response
	 * @param reqPath  the request path; if null, use req.getPathInfo()
	 *
	 * @return true if ok to proceed. If false, the appropriate error or redirect message has been sent, the caller only needs to return.
	 * @throws IOException on read error
	 */
	static public boolean resourceControlOk( String reqPath) throws IOException {
		//	    if (null == reqPath)
		//	      reqPath = req.getPathInfo();

		//	    if (reqPath.startsWith("/"))
		//	      reqPath = reqPath.substring(1);

		// see if its under resource control
		//	    String rc = findResourceControl(reqPath);
		//	    if (rc != null) {
		//	      if (debugResourceControl) System.out.println("DatasetHandler request has resource control =" + rc + "\n"
		//	              + ServletUtil.showRequestHeaders(req) + ServletUtil.showSecurity(req, rc));

		//	      try {
		//	        if (!RestrictedDatasetServlet.authorize(req, res, rc)) {
		//	          return false;
		//	        }
		//	      } catch (ServletException e) {
		//	        throw new IOException(e.getMessage());
		//	      }
		//
		//	      if (debugResourceControl) System.out.println("ResourceControl granted = " + rc);
		//	    }

		return true;
	}	

	/**
	 * This tracks Dataset elements that have embedded NcML
	 *
	 * @param path the req.getPathInfo() of the dataset.
	 * @param ds   the dataset
	 */
	static void putNcmlDataset(String path, InvDatasetImpl ds) {
		if (log.isDebugEnabled()) log.debug("putNcmlDataset " + path + " for " + ds.getName());
		ncmlDatasetHash.put(path, ds);
	}

	// used only for the case of Dataset (not DatasetScan) that have an NcML element inside.
	// This makes the NcML dataset the target of the server.
	static private class NcmlFileFactory implements FileFactory {
		private InvDatasetImpl ds;

		NcmlFileFactory(InvDatasetImpl ds) {
			this.ds = ds;
		}

		public NetcdfFile open(String cacheName, int buffer_size, ucar.nc2.util.CancelTask cancelTask, Object spiObject) throws IOException {
			org.jdom.Element netcdfElem = ds.getNcmlElement();
			return NcMLReader.readNcML(cacheName, netcdfElem, cancelTask);
		}
	}	  

}