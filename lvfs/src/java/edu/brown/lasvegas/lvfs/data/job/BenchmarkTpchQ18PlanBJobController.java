package edu.brown.lasvegas.lvfs.data.job;

import java.io.IOException;
import java.util.ArrayList;
import java.util.SortedMap;
import java.util.TreeMap;

import edu.brown.lasvegas.CompressionType;
import edu.brown.lasvegas.LVColumn;
import edu.brown.lasvegas.LVReplicaGroup;
import edu.brown.lasvegas.LVReplicaPartition;
import edu.brown.lasvegas.LVTask;
import edu.brown.lasvegas.ReplicaPartitionStatus;
import edu.brown.lasvegas.TaskStatus;
import edu.brown.lasvegas.TaskType;
import edu.brown.lasvegas.lvfs.data.task.BenchmarkTpchQ18TaskParameters;
import edu.brown.lasvegas.lvfs.data.task.RepartitionTaskParameters;
import edu.brown.lasvegas.protocol.LVMetadataProtocol;

/**
 * This job is a slower query plan for TPC-H's Q18.
 * This doesn't assume co-partitioned orders/lineitem table.
 * Like Q18's Plan-B, this job requires multiple steps as follows.
 * 
 * 1. Access lineitem table in each node, re-partition l_orderkey/l_quantity by partkey and save the result
 * in each node.
 * 2. Again at each node, for each part partition in the node,
 * collect the re-partitioned results from all other nodes, create the
 * co-partitioned lineitem table. (equivalent to Shuffle in Hadoop)
 * 3. Then do the same as {@link BenchmarkTpchQ18PlanAJobController}.
 * 
 * The difference from Q17 is that this is a TOP-X query.
 * So, at each node we return TOP-X results and then we merge
 * the ranking at the central node, outputting the final result.
 * We also join with customer table then.
 */
public class BenchmarkTpchQ18PlanBJobController extends BenchmarkTpchQ18JobController {
    public BenchmarkTpchQ18PlanBJobController (LVMetadataProtocol metaRepo) throws IOException {
        super (metaRepo);
    }
    public BenchmarkTpchQ18PlanBJobController (LVMetadataProtocol metaRepo, long stopMaxWaitMilliseconds, long taskJoinIntervalMilliseconds, long taskJoinIntervalOnErrorMilliseconds) throws IOException {
        super(metaRepo, stopMaxWaitMilliseconds, taskJoinIntervalMilliseconds, taskJoinIntervalOnErrorMilliseconds);
    }

    private LVReplicaGroup ordersGroup;
    private LVColumn l_orderkey, l_quantity;
    private SortedMap<Integer, ArrayList<Integer>> lineitemNodeMap;
    private SortedMap<Integer, ArrayList<Integer>> ordersNodeMap;
    @Override
    protected void initDerivedTpchQ18() throws IOException {
        // lineitem and part are not co-partitioned, so create node map individually
        lineitemNodeMap = createNodeMap (lineitemPartitions, "lineitem");
        ordersNodeMap = createNodeMap (ordersPartitions, "part");
        ordersGroup = metaRepo.getReplicaGroup(ordersScheme.getGroupId());
        assert (ordersGroup != null);
        l_orderkey = metaRepo.getColumnByName(lineitemTable.getTableId(), "l_orderkey");
        assert (l_orderkey != null);
        l_quantity = metaRepo.getColumnByName(lineitemTable.getTableId(), "l_quantity");
        assert (l_quantity != null);
    }

    //TODO this function should be shared
    private SortedMap<Integer, ArrayList<Integer>> createNodeMap (LVReplicaPartition[] partitions, String label) {
        SortedMap<Integer, ArrayList<Integer>> nodeMap = new TreeMap<Integer, ArrayList<Integer>>(); // key=nodeId
        for (LVReplicaPartition partition : partitions) {
            if (partition.getStatus() == ReplicaPartitionStatus.EMPTY) {
                LOG.info("this " + label + " partition will produce no result. skipped:" + partition);
                continue;
            }
            LOG.info("existing " + label + " partition: " + partition);
            ArrayList<Integer> partitionIds = nodeMap.get(partition.getNodeId());
            if (partitionIds == null) {
            	partitionIds = new ArrayList<Integer>();
            	nodeMap.put (partition.getNodeId(), partitionIds);
            }
            partitionIds.add(partition.getPartitionId());
        }
        return nodeMap;
    }
    
    @Override
    protected void runDerived() throws IOException {
        LOG.info("going to run TPCH Q18 with repartitioning. QuantityThreshold=" + param.getQuantityThreshold());

        // 1. repartition lineitem at each node.
        SortedMap<Integer, String> summaryFileMap = repartitionLineitem(0.0d, 0.45d);
        
        // 2. at each node for each part partition, collect the repartitioned lineitem files
        // and then run Q18.
        SortedMap<Integer, LVTask> taskMap = collectAndRunQuery (summaryFileMap, 0.45d, 0.9d);
        
        // 3. create the final ranking and join the top 100 with customer.
        collectResultRanking(taskMap);
        LOG.info("all tasks including repartitioning seem done!");
    }
    
    private SortedMap<Integer, String> repartitionLineitem (double baseProgress, double completedProgress) throws IOException {
        SortedMap<Integer, LVTask> taskMap = new TreeMap<Integer, LVTask>();
        for (Integer nodeId : lineitemNodeMap.keySet()) {
            ArrayList<Integer> lineitemPartitionIds = lineitemNodeMap.get(nodeId);
        	RepartitionTaskParameters taskParam = new RepartitionTaskParameters();
        	taskParam.setBasePartitionIds(asIntArray(lineitemPartitionIds));
        	taskParam.setOutputCacheSize(1 << 12); // doesn't matter. so far.
        	taskParam.setOutputColumnIds(new int[]{l_orderkey.getColumnId(), l_quantity.getColumnId()});
        	taskParam.setOutputCompressions(new CompressionType[]{CompressionType.NONE, CompressionType.NONE});
        	taskParam.setPartitioningColumnId(l_orderkey.getColumnId());
        	taskParam.setPartitionRanges(ordersGroup.getRanges());
        	taskParam.setReadCacheSize(1 << 16); // this is important. maybe 1 << 20?

            int taskId = metaRepo.createNewTaskIdOnlyReturn(jobId, nodeId, TaskType.REPARTITION, taskParam.writeToBytes());
            LVTask task = metaRepo.updateTask(taskId, TaskStatus.START_REQUESTED, null, null, null);
            LOG.info("launched new task to repartition for TPCH Q18: " + task);
            assert (!taskMap.containsKey(taskId));
            taskMap.put(taskId, task);
        }
        joinTasks(taskMap, baseProgress, completedProgress);

        SortedMap<Integer, String> summaryFileMap = new TreeMap<Integer, String>();
        for (LVTask task : taskMap.values()) {
        	int nodeId = task.getNodeId();
        	assert (!summaryFileMap.containsKey(nodeId));
        	assert (task.getOutputFilePaths() != null);
        	assert (task.getOutputFilePaths().length == 1);
        	String summaryFilePath = task.getOutputFilePaths()[0];
        	summaryFileMap.put(nodeId, summaryFilePath);
        }
        return summaryFileMap;
    }
    
    private SortedMap<Integer, LVTask> collectAndRunQuery (SortedMap<Integer, String> summaryFileMap, double baseProgress, double completedProgress) throws IOException {
        SortedMap<Integer, LVTask> taskMap = new TreeMap<Integer, LVTask>();
        for (Integer nodeId : ordersNodeMap.keySet()) {
            ArrayList<Integer> ordersPartitionIds = ordersNodeMap.get(nodeId);
            
            BenchmarkTpchQ18TaskParameters taskParam = new BenchmarkTpchQ18TaskParameters();
            taskParam.setQuantityThreshold(param.getQuantityThreshold());
            taskParam.setLineitemTableId(lineitemTable.getTableId());
            taskParam.setOrdersTableId(ordersTable.getTableId());
            taskParam.setLineitemPartitionIds(new int[0]);
            taskParam.setOrdersPartitionIds(asIntArray(ordersPartitionIds));
            taskParam.setRepartitionSummaryFileMap(summaryFileMap);

            int taskId = metaRepo.createNewTaskIdOnlyReturn(jobId, nodeId, TaskType.BENCHMARK_TPCH_Q18_PLANB, taskParam.writeToBytes());
            LVTask task = metaRepo.updateTask(taskId, TaskStatus.START_REQUESTED, null, null, null);
            LOG.info("launched new task to run TPCH Q18: " + task);
            assert (!taskMap.containsKey(taskId));
            taskMap.put(taskId, task);
        }
        joinTasks(taskMap, baseProgress, completedProgress);
        return taskMap;
    }
}
