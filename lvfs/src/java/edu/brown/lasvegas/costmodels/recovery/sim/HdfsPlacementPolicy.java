package edu.brown.lasvegas.costmodels.recovery.sim;

/**
 * Parameters for HDFS data layout.
 * It's just the replication factor. 
 */
public class HdfsPlacementPolicy {
	public HdfsPlacementPolicy(int replicationFactor) {
		this.replicationFactor = replicationFactor;
	}
	public final int replicationFactor;
}