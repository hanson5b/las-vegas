package edu.brown.lasvegas.costmodels.recovery.sim;

/**
 * Specifies the hardware configuration to simulate.
 * Immutable, thus all properties are public final.
 */
public class ExperimentalConfiguration {
	
	/**
	 * Instantiates a new experimental configuration.
	 */
	public ExperimentalConfiguration() {
		this (100, 100, 100, 100,
				4.3d * 30 * 24 * 60, 10.2d * 365 * 24 * 60,
				0.05d * 60, 3.0d * 60, 0.1d * 60,
				3650.0d * 24 * 60);
		// numbers are from: http://www.cs.cornell.edu/projects/ladis2009/talks/dean-keynote-ladis2009.pdf
		// and OSDI'10
	}

	/**
	 * Instantiates a new experimental configuration.
	 *
	 * @param racks the racks
	 * @param nodesPerRack the nodes per rack
	 * @param gigabytesPerNode the gigabytes per node
	 * @param tables the tables
	 * @param nodeMeanTimeToFail the node mean time to fail
	 * @param rackMeanTimeToFail the rack mean time to fail
	 * @param localDisk local disk throughput
	 * @param backboneNetwork the backbone network gb/min
	 * @param localNetwork local network gb/min
	 * @param maxSimulationPeriod the max simulation period
	 */
	public ExperimentalConfiguration(int racks, int nodesPerRack, int gigabytesPerNode, int tables,
			double nodeMeanTimeToFail, double rackMeanTimeToFail,
			double localDisk, double backboneNetwork, double localNetwork,
			double maxSimulationPeriod) {
		this.racks = racks;
		this.nodesPerRack = nodesPerRack;
		this.nodes = racks * nodesPerRack;
		this.gigabytesPerNode = gigabytesPerNode;
		this.tables = tables;
		this.gigabytesTotal = gigabytesPerNode * nodes;
		this.nodeMeanTimeToFail = nodeMeanTimeToFail;
		this.rackMeanTimeToFail = rackMeanTimeToFail;
		this.localDisk = localDisk;
		this.backboneNetwork = backboneNetwork;
		this.localNetwork = localNetwork;
		this.maxSimulationPeriod = maxSimulationPeriod;
	}
	
	/** number of racks in the system. */
	public final int racks;
	/** number of machines in each rack. */
	public final int nodesPerRack;
	/** total number of machines . */
	public final int nodes;
	/** amount of original data to store per node _without replication_. */
	public final int gigabytesPerNode;
	/** number of tables (objects) stored in the system. */
	public final int tables;
	/** total amount of original data to store _without replication_. */
	public final int gigabytesTotal;
	
	/** MTTF of a node in minutes. */
	public final double nodeMeanTimeToFail;
	
	/** MTTF of a rack in minutes. */
	public final double rackMeanTimeToFail;
	
	/** disk throughput to recover a node GB/min. */
	public final double localDisk;
	/** maximum total network throughput (inter-rack I/O) GB/min. */
	public final double backboneNetwork;
	/** local network throughput GB/min. */
	public final double localNetwork;
	
	/** maximum length of simulation in minutes. */
	public final double maxSimulationPeriod;
	
	/**
	 * Racks use IDs 0 to racks-1. Nodes use IDs racks to racks + nodes.
	 */
	public int rackIdFromNodeId (int nodeId) {
		assert (nodeId >= racks);
		assert (nodeId < racks + nodes);
		return (nodeId - racks) / nodesPerRack;
	}
	public int firstNodeIdFromRackId (int rackId) {
		assert (rackId >= 0);
		assert (rackId < racks);
		return racks + (rackId * nodesPerRack);
	}
	
	public double getNetworkRate (int concurrentTasks) {
		if (concurrentTasks <= 0) {
			return localNetwork;
		}
		double maxRate = backboneNetwork / concurrentTasks;
		return Math.min(maxRate, localNetwork);
	}
	
	public double getCombinedRate (double networkRate) {
		// 1/combined_rate = 1/network_rate + 1/disk_rate
		double inverse = (1.0d / networkRate) + (1.0d / localDisk);
		return 1.0d / inverse;
	}
}