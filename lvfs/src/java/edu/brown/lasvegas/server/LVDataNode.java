package edu.brown.lasvegas.server;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.server.datanode.DataNode;
import org.apache.hadoop.ipc.RPC;
import org.apache.hadoop.ipc.RPC.Server;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.util.ServicePlugin;
import org.apache.log4j.Logger;

import edu.brown.lasvegas.LVRackNode;
import edu.brown.lasvegas.client.LVMetadataClient;
import edu.brown.lasvegas.lvfs.data.DataEngine;
import edu.brown.lasvegas.protocol.LVDataProtocol;
import edu.brown.lasvegas.protocol.LVMetadataProtocol;

/**
 * Las-Vegas Data Node to receive load/replication/recovery requests
 * for LVFS files.
 * 
 * <p>Like {@link LVCentralNode}, this class merely forwards method
 * calls to the real implementation class, {@link DataEngine}.</p>

 * <p>
 * LVDataNode runs on each HDFS node to extend its functionality.
 * It's deployed as a plugin ({@link ServicePlugin}) for HDFS Data Node.
 * </p>
 * 
 * <p>In order to activate it, the user needs to put the following parameter
 * in Hadoop's configuration file (e.g., hdfs-default.xml).</p>
<pre>
&lt;property&gt;
  &lt;name&gt;dfs.datanode.plugins&lt;/name&gt;
  &lt;value&gt;edu.brown.lasvegas.server.LVFSDataNode&lt;/value&gt;
&lt;/property&gt;

&lt;property&gt;
  &lt;name&gt;lasvegas.server.data.node_name&lt;/name&gt;
  &lt;value&gt;node1&lt;/value&gt;
&lt;/property&gt;

&lt;property&gt;
  &lt;name&gt;lasvegas.server.data.rack_name&lt;/name&gt;
  &lt;value&gt;rack1&lt;/value&gt;
&lt;/property&gt;
</pre>
 * 
 */
public final class LVDataNode implements ServicePlugin {
    private static Logger LOG = Logger.getLogger(LVDataNode.class);

    static{
        Configuration.addDefaultResource("hdfs-default.xml");
        Configuration.addDefaultResource("hdfs-site.xml");
        Configuration.addDefaultResource("mapred-default.xml");
        Configuration.addDefaultResource("mapred-site.xml");
    }

    public LVDataNode () {
        this(new Configuration());
    }
    public LVDataNode (Configuration conf) {
        this.conf = conf;
    }
    /** Constructor to directly specify the metadata repository instance. usually for testing. */
    public LVDataNode (Configuration conf, LVMetadataProtocol metaRepo) {
        this.conf = conf;
        this.metaRepo = metaRepo;
    }
    
    /** hadoop configuration */
    private Configuration conf;
    public Configuration getConfiguration() {
        return conf;
    }

    /** HDFS Data Node containing this object as a plugin. */
    private DataNode hdfsDataNode;
    public DataNode getHdfsDataNode () {
        return hdfsDataNode;
    }
    
    /** only if connecting to remote metadata repository. */
    private LVMetadataClient metaClient;
    /** metadata repository. */
    private LVMetadataProtocol metaRepo;

    public static final String DATA_ADDRESS_KEY = "lasvegas.server.data.address";
    public static final String DATA_ADDRESS_DEFAULT = "localhost:28712";

    public static final String DATA_NODE_NAME_KEY = "lasvegas.server.data.node_name";
    public static final String DATA_NODE_NAME_DEFAULT = "default_node";

    public static final String DATA_RACK_NAME_KEY = "lasvegas.server.data.rack_name";
    public static final String DATA_RACK_NAME_DEFAULT = "default_rack";
    
    /** server object to provide data accesses on LVFS files in this node. */
    private Server dataServer;
    /** instance of the LVFS data management in this node. */
    private DataEngine dataEngine;

    @Override
    public void start(Object service) {
        hdfsDataNode = (DataNode) service;
        try {
            initialize();
        } catch (Exception exception) {
            LOG.error("error while initializing data node. will terminate", exception);
            try {
                stop();
            } catch (Exception ex2) {
                LOG.error("another error while stopping", ex2);
            }
            throw new RuntimeException("error while initializing data node", exception);
        }
    }

    private void initialize() throws IOException {
        String address = conf.get(DATA_ADDRESS_KEY, DATA_ADDRESS_DEFAULT);
        LOG.info("initializing LVFS Data Server. address=" + address);
        if (metaRepo == null) {
            metaClient = new LVMetadataClient(conf);
            metaRepo = metaClient.getChannel();
        } else {
            LOG.debug ("using metadata repository instance given to the constructor");
        }
        // String nodeName = hdfsDataNode.getMachineName();
        String nodeName = conf.get(DATA_NODE_NAME_KEY, DATA_NODE_NAME_DEFAULT);
        LVRackNode node = metaRepo.getRackNode(nodeName);
        if (node == null) {
            throw new IOException ("This node name doesn't exist: " + nodeName);
        }
        // TODO register node/rack if needed
        InetSocketAddress sockAddress = NetUtils.createSocketAddr(address);
        dataEngine = new DataEngine(metaRepo, node.getNodeId(), conf);
        dataEngine.start();
        dataServer = RPC.getServer(LVDataProtocol.class, dataEngine, sockAddress.getHostName(), sockAddress.getPort(), conf);
        LOG.info("initialized LVFS Data Server.");
        dataServer.start();
        LOG.info("started LVFS Data Server.");
    }
    
    @Override
    public void close() throws IOException {
        stop ();
        hdfsDataNode = null;
    }

    private boolean stopRequested = false;
    /**
     * Requests all modules in this node to stop.
     */
    @Override
    public void stop() {
        if (stopRequested) {
            return;
        }
        LOG.info("Stopping data node...");
        stopRequested = true;
        // stop proceeds in the _opposite_ order to initialization
        if (dataServer != null) {
            dataServer.stop();
            try {
                dataEngine.close();
            } catch (IOException ex) {
                LOG.error("error on closing data engine", ex);
            }
        }
        if (metaClient != null) {
            metaClient.release();
            metaClient = null;
        }
        LOG.info("Stopped data node.");
    }

    /**
     * Block until all modules in this node stop.
     */
    public void join() {
        LOG.info("Waiting until data node stops...");
        try {
            if (dataServer != null) {
                dataServer.join();
            }
            LOG.info("data node has been stopped.");
        } catch (InterruptedException ex) {
            LOG.warn("Interrupted while joining data node.", ex);
        }
    }
}
