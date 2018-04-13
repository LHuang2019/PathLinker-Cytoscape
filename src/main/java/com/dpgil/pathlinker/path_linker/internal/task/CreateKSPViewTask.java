package com.dpgil.pathlinker.path_linker.internal.task;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import org.cytoscape.app.CyAppAdapter;
import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.task.AbstractNetworkTask;
import org.cytoscape.view.layout.CyLayoutAlgorithm;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.View;
import org.cytoscape.view.presentation.property.BasicVisualLexicon;
import org.cytoscape.view.presentation.property.NodeShapeVisualProperty;
import org.cytoscape.work.ObservableTask;
import org.cytoscape.work.SynchronousTaskManager;
import org.cytoscape.work.TaskIterator;
import org.cytoscape.work.TaskMonitor;

import com.dpgil.pathlinker.path_linker.internal.event.PathLinkerNodeSelectionListener;
import com.dpgil.pathlinker.path_linker.internal.model.PathLinkerModel;
import com.dpgil.pathlinker.path_linker.internal.util.Algorithms.PathWay;
import com.dpgil.pathlinker.path_linker.internal.view.PathLinkerControlPanel;

/**
 * Class which creates a task that creates the KSP subgraph and subgraph view based
 *      on the input network and model 
 */
public class CreateKSPViewTask extends AbstractNetworkTask implements ObservableTask {

    /** the current PathLinker control panel */
    private PathLinkerControlPanel controlPanel;    
    /** the network to be used to create subgraph and subgraphview */
    private CyNetwork network;
    /** the model to access the information necessary for creating the subgraph/view */
    private PathLinkerModel model;
    /** adapter to create necessary tasks to create the sub network */
    private CyAppAdapter adapter;
    /** application manager to access network and network view */
    private CyApplicationManager applicationManager;

    /** the sub graph created */
    private CyNetwork kspSubgraph;
    /** the sub graph view created */
    private CyNetworkView kspSubgraphView;
    /** The name of the path rank column correspond to the ksp subnetwork */
    private String pathRankColumnName;

    /**
     * Default constructor
     * @param controlPanel the PathLinkerControlPanel
     * @param network the network
     * @param model the model
     * @param adapter the adapter
     * @param applicationManager the application manager
     */
    public CreateKSPViewTask(
            PathLinkerControlPanel controlPanel,
            CyNetwork network,
            PathLinkerModel model,
            CyAppAdapter adapter,
            CyApplicationManager applicationManager) {
        super(network);

        this.controlPanel = controlPanel;
        this.network = network;
        this.model = model;
        this.adapter = adapter;
        this.applicationManager = applicationManager;
    }

    /**
     * Used to access the ksp subgraph/view and path rank column name created by the task
     * @return the subgraph/view and column name created
     */
    @SuppressWarnings("unchecked")
    @Override
    public <R> R getResults(Class<? extends R> type) {
        if (type.equals(CyNetwork.class))
            return (R) kspSubgraph;

        if (type.equals(CyNetworkView.class))
            return (R) kspSubgraphView;

        if (type.equals(String.class))
            return (R) pathRankColumnName;

        return null;
    }

    /**
     * Runs the task to create the ksp subgraph, subgraphview, and path rank
     */
    @Override
    public void run(TaskMonitor taskMonitor) {

        taskMonitor.setTitle("Constructing KSP subgraph and KSP subgraph view");
        taskMonitor.setStatusMessage("Constructing KSP subgraph and KSP subgraph view. Please wait...");

        // disable the action to update the network combo box while creating the new network
        PathLinkerNodeSelectionListener.setActive(false);

        // increment the index use for creating the network, path rank column, and result panel
        controlPanel.nameIndex++;

        // generates a subgraph of the nodes and edges involved in the resulting paths and displays it to the user
        createKSPSubgraphAndView();

        // enables the action to update the network combo box after creating the new network
        PathLinkerNodeSelectionListener.setActive(true);

        // manually updates the network combo box after creating the new network
        controlPanel.initializeNetworkCmb();

        // update the table path rank attribute
        updatePathRankAttribute(model.getResult());

        // update the ksp subgraph name
        updateNetworkName();

        taskMonitor.setStatusMessage("Constructing KSP subgraph and KSP subgraph view completed");
    }

    /**
     * Creates a new sub-network and sub-network view for the subgraph generated by the KSP
     */
    public void createKSPSubgraphAndView() {
        // creates task iterator and execute it to generate a sub-network from the original network
        // the bypass values and other styles from the original network will be pass down to the sub-network
        TaskIterator subNetworkTask = adapter.get_NewNetworkSelectedNodesAndEdgesTaskFactory().createTaskIterator(network);

        // creates synchronous task manager to execute the task on creating the subnetwork
        SynchronousTaskManager<?> synTaskMan = adapter.getCyServiceRegistrar().getService(SynchronousTaskManager.class);
        synTaskMan.execute(subNetworkTask);

        // assign the new sub network and network views
        kspSubgraph = applicationManager.getCurrentNetworkView().getModel();
        kspSubgraphView = applicationManager.getCurrentNetworkView();

        // use a visual bypass to color the sources and targets for the sub-network view
        Color targetColor = new Color(255, 223, 0);

        for (CyNode source : model.getSubgraphSources()) {
            View<CyNode> currView = kspSubgraphView.getNodeView(source);
            currView.setLockedValue(BasicVisualLexicon.NODE_SHAPE, NodeShapeVisualProperty.DIAMOND);
            currView.setLockedValue(BasicVisualLexicon.NODE_FILL_COLOR, Color.CYAN);
        }
        for (CyNode target : model.getSubgraphTargets()) {
            View<CyNode> currView = kspSubgraphView.getNodeView(target);
            currView.setLockedValue(BasicVisualLexicon.NODE_SHAPE, NodeShapeVisualProperty.RECTANGLE);
            currView.setLockedValue(BasicVisualLexicon.NODE_FILL_COLOR, targetColor);
        }

        kspSubgraphView.updateView();

        // apply layout according to the k value
        applyLayout();
    }

    /**
     * Applies hierarchical layout to the sub-network If k <= 2000, otherwise the users default layout will be applied
     */
    private void applyLayout() {
        // Applying the hierarchical layout is quick for a small number of nodes and edges. 
        // Applying the hierarchical layout took ~2 sec for k=1000, ~10 sec for k=2000, and ~5 min for k=5000. 
        // The user can apply the layout after generating the network, so to keep running time down, set the max to k=2000
        boolean hierarchical = model.getOutputK() <= 2000;

        // set node layout by applying the default or hierarchical layout algorithm
        CyLayoutAlgorithm algo = hierarchical ? adapter.getCyLayoutAlgorithmManager().getLayout("hierarchical")
                : adapter.getCyLayoutAlgorithmManager().getDefaultLayout();
        TaskIterator iter = algo.createTaskIterator(kspSubgraphView, algo.createLayoutContext(),
                CyLayoutAlgorithm.ALL_NODE_VIEWS, null);

        // creates synchronous task manager to execute the task on applying the layout
        SynchronousTaskManager<?> synTaskMan = adapter.getCyServiceRegistrar().getService(SynchronousTaskManager.class);
        synTaskMan.execute(iter);

        if (!hierarchical) // ends if default layout
            return;

        // reflect nodes about the x-axis because the default hierarchical
        // layout renders the nodes upside down
        // Update: only reflect nodes if k < 200. For k >= 200, the hierarchical layout is right-side up
        if (model.getOutputK() < 200) {
            double maxY = Integer.MIN_VALUE;
            double minY = Integer.MAX_VALUE;

            // finds the midpoint x coordinate
            for (CyNode node : kspSubgraph.getNodeList()) {
                View<CyNode> nodeView = kspSubgraphView.getNodeView(node);
                double yCoord = nodeView.getVisualProperty(BasicVisualLexicon.NODE_Y_LOCATION);

                if (yCoord > maxY)
                    maxY = yCoord;

                if (yCoord < minY)
                    minY = yCoord;
            }

            double midY = (maxY + minY) / 2;

            // reflects each node about the midpoint x axis
            for (CyNode node : kspSubgraph.getNodeList()) {
                View<CyNode> nodeView = kspSubgraphView.getNodeView(node);
                double yCoord = nodeView.getVisualProperty(BasicVisualLexicon.NODE_Y_LOCATION);

                double newY = -1 * yCoord + 2 * midY;
                nodeView.setVisualProperty(BasicVisualLexicon.NODE_Y_LOCATION, newY);
            }
        }

        kspSubgraphView.updateView();
    }

    /**
     * Creates a path rank attribute to the network edge tables
     * that rank each edge in the newly generated paths according to its weight
     * @param paths 
     *          the sorted paths of the network generated from the algorithm
     */
    public void updatePathRankAttribute(ArrayList<PathWay> paths) {
        // Use nameIndex to create a new attribute "path rank n"
        // in the network edge table, where n is an unique number
        while (network.getDefaultEdgeTable().getColumn("path rank " + controlPanel.nameIndex) != null)
            controlPanel.nameIndex++;

        pathRankColumnName = "path rank " + controlPanel.nameIndex;
        network.getDefaultEdgeTable().createColumn(pathRankColumnName, Integer.class, false);

        for (int i = 0; i < paths.size(); i++) {
            PathWay currPath = paths.get(i);

            // excluding supersource and supertarget
            for (int j = 1; j < currPath.size() - 2; j++) {
                CyNode node1 = currPath.get(j);
                CyNode node2 = currPath.get(j + 1);

                // add all of the directed edges from node1 to node2
                List<CyEdge> edges = network.getConnectingEdgeList(node1, node2, CyEdge.Type.DIRECTED);
                for (CyEdge edge : edges)
                {
                    if (network.getRow(edge).get(pathRankColumnName, Integer.class) == null &&
                            ((edge.getSource().equals(node1) && edge.getTarget().equals(node2)) ||
                            (model.treatNetworkAsUndirected && edge.getSource().equals(node2) && edge.getTarget().equals(node1)))) // verifies the edges direction
                        network.getRow(edge).set(pathRankColumnName, i + 1);
                }
                // also add all of the undirected edges from node1 to node2
                edges = network.getConnectingEdgeList(node1, node2, CyEdge.Type.UNDIRECTED);
                for (CyEdge edge : edges) 
                    if (network.getRow(edge).get(pathRankColumnName, Integer.class) == null)
                        network.getRow(edge).set(pathRankColumnName,  i + 1);
            }
        }

        // add the newly created column into the maps
        controlPanel._pathRankToSuidMap.put(pathRankColumnName, kspSubgraph.getSUID());
        controlPanel._suidToPathRankMap.put(kspSubgraph.getSUID(), pathRankColumnName);
    }

    /**
     * Assign appropriate network name to the new sub-network created using nameIndex field
     */
    public void updateNetworkName() {
        // Create the new name to the sub-network
        String subgraphName = "PathLinker-subnetwork-" + model.getOutputK() + "-paths-" + controlPanel.nameIndex;

        int count = 1;
        boolean condition = false;
        List<CyNetwork> networkList = new ArrayList<CyNetwork>();
        networkList.addAll(adapter.getCyNetworkManager().getNetworkSet());

        // check if network network already exist
        for (CyNetwork network : networkList) {
            if (network.getRow(network).get(CyNetwork.NAME, String.class).trim().equals(subgraphName)) {
                condition = true;
                break;
            }
        }

        // if network name already exist, create alternative name
        // check if alternative name also exists
        outerLoop:
            while (condition) {
                for (CyNetwork network : networkList) {
                    if (network.getRow(network).get(CyNetwork.NAME, String.class).trim().
                            equals(subgraphName + " (" + count + ")")) {
                        count++;
                        continue outerLoop;
                    }
                }

                subgraphName += (" (" + count + ")");
                condition = false;
            }

        // apply the name to the network
        kspSubgraph.getRow(kspSubgraph).set(CyNetwork.NAME, subgraphName);
    }
}
