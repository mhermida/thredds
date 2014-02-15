package ucar.nc2.grib.collection;

import thredds.featurecollection.FeatureCollectionConfig;
import ucar.unidata.io.RandomAccessFile;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Read Grib2Partition From ncx2 Index,
 * Data file never opened.
 *
 * @author John
 * @since 12/7/13
 */
public class Grib2PartitionBuilderFromIndex extends Grib2CollectionBuilderFromIndex {

    // read in the index, open raf and leave open in the GribCollection
  static public PartitionCollection createTimePartitionFromIndex(String name, File directory, FeatureCollectionConfig config, org.slf4j.Logger logger) throws IOException {
    File idxFile = ucar.nc2.grib.collection.GribCollection.getIndexFile(name, directory);
    RandomAccessFile raf = new RandomAccessFile(idxFile.getPath(), "r");
    return createTimePartitionFromIndex(name, directory, raf, config, logger);
  }

  // read in the index, index raf already open
  static public PartitionCollection createTimePartitionFromIndex(String name, File directory, RandomAccessFile raf,
           FeatureCollectionConfig config, org.slf4j.Logger logger) throws IOException {

    Grib2PartitionBuilderFromIndex builder = new Grib2PartitionBuilderFromIndex(name, directory, config, logger);
    if (builder.readIndex(raf))
      return builder.pc;

    throw new IOException("Reading index failed");
  }

  //////////////////////////////////////////////////////////////////////////////////

  //private final PartitionManager tpc; // defines the partition
  private PartitionCollection pc;  // build this object

  private Grib2PartitionBuilderFromIndex(String name, File directory, FeatureCollectionConfig config, org.slf4j.Logger logger) {
    super(null, directory, config, logger);
    this.pc = new Grib2Partition(name, directory, config, logger);
    this.gc = pc;
  }

  @Override
  public String getMagicStart() {
    return Grib2PartitionBuilder.MAGIC_START;
  }

  ///////////////////////////////////////////////////////////////////////////
  // reading ncx

  /*
  extend GribCollection {
    repeated Partition partitions = 100;
    required bool isPartitionOfPartitions = 101;
    repeated uint32 run2part = 102;       // masterRuntime index to partition index
  }
   */
  @Override
  protected boolean readExtensions(GribCollectionProto.GribCollection proto) {
    pc.isPartitionOfPartitions = proto.getExtension(PartitionCollectionProto.isPartitionOfPartitions);

    List<Integer> list = proto.getExtension(PartitionCollectionProto.run2Part);
    pc.run2part = new int[list.size()];
    int count = 0;
    for (int partno : list)
      pc.run2part[count++] = partno;

    List<ucar.nc2.grib.collection.PartitionCollectionProto.Partition> partList = proto.getExtension(PartitionCollectionProto.partitions);
    for (ucar.nc2.grib.collection.PartitionCollectionProto.Partition partProto : partList)
      makePartition(partProto);

    return partList.size() > 0;
  }

  /*
  extend Variable {
    repeated PartitionVariable partition = 100;
    repeated Parameter vparams = 101;    // not used yet
  }
   */
  @Override
  protected GribCollection.VariableIndex readVariableExtensions(GribCollection.GroupGC group, GribCollectionProto.Variable proto, GribCollection.VariableIndex vi) {
    List<PartitionCollectionProto.PartitionVariable> pvList = proto.getExtension(PartitionCollectionProto.partition);

    PartitionCollection.VariableIndexPartitioned vip = pc.makeVariableIndexPartitioned(group, vi, pvList.size());
    /* vip.density = vi.density;   // ??
    vip.missing = vi.missing;
    vip.ndups = vi.ndups;
    vip.nrecords = vi.nrecords;  */

    for (PartitionCollectionProto.PartitionVariable pv : pvList) {
      vip.addPartition(pv.getPartno(), pv.getGroupno(), pv.getVarno(), pv.getFlag(), pv.getNdups(),
              pv.getNrecords(), pv.getMissing(), pv.getDensity());
    }

    return vip;
  }

  /*
message Partition {
  required string name = 1;       // name is used in TDS - eg the subdirectory when generated by TimePartitionCollections
  required string filename = 2;   // the gribCollection.ncx2 file
  required string directory = 3;   // top directory
  optional uint64 lastModified = 4;
}
   */
  private PartitionCollection.Partition makePartition(PartitionCollectionProto.Partition proto) {

    return pc.addPartition(proto.getName(), proto.getFilename(),
            proto.getLastModified(), proto.getDirectory());
  }

}
