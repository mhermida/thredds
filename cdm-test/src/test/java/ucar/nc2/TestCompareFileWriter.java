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
package ucar.nc2;

import junit.framework.*;
import java.io.*;
import java.util.*;

import ucar.unidata.test.util.TestDir;

/** test FileWriting, then reading back and comparing to original. */

public class TestCompareFileWriter extends TestCase {

  public TestCompareFileWriter( String name) {
    super(name);
  }

  public ArrayList files;
  public void testCompare() throws IOException {
    doOne(TestDir.cdmUnitTestDir +"formats/gini/SUPER-NATIONAL_8km_WV_20051128_2200.gini", TestDir.temporaryLocalDataDir +"SUPER-NATIONAL_8km_WV_20051128_2100.gini");
  }

  public void utestCompareAll() throws IOException {
    readAllDir(TestDir.cdmUnitTestDir +"formats/gini/");
  }

  void readAllDir(String dirName) throws IOException {
    System.out.println("---------------Reading directory "+dirName);
    File allDir = new File( dirName);
    File[] allFiles = allDir.listFiles();

    for (int i = 0; i < allFiles.length; i++) {
      File f = allFiles[i];
      if (f.isDirectory()) continue;

      String path = f.getAbsolutePath();
      doOne(path, TestDir.temporaryLocalDataDir +"/"+f.getName());
    }

    for (int i = 0; i < allFiles.length; i++) {
      File f = allFiles[i];
      if (f.isDirectory())
        readAllDir(allFiles[i].getAbsolutePath());
    }

  }

  private void doOne(String datasetIn, String filenameOut) throws IOException {
    File fin = new File(datasetIn);
    File fout = new File(filenameOut);
    System.out.printf("Write %s %n   to %s (%s %s)%n", fin.getAbsolutePath(), fout.getAbsolutePath(), fout.exists(), fout.getParentFile().exists());
    File tempDir = new File(TestDir.temporaryLocalDataDir);
    System.out.printf("Temp dir %s (%s)%n", tempDir.getAbsolutePath(), tempDir.exists());

    NetcdfFile ncfileIn = ucar.nc2.dataset.NetcdfDataset.openFile(datasetIn, null);
    NetcdfFile ncfileOut = FileWriter.writeToFile( ncfileIn, filenameOut);
    ucar.unidata.test.util.CompareNetcdf.compareFiles(ncfileIn, ncfileOut);

    ncfileIn.close();
    ncfileOut.close();
  }

}
