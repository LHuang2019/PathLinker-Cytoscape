package com.dpgil.pathlinker.path_linker.internal.event;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.events.RowSetRecord;
import org.cytoscape.model.events.RowsSetEvent;
import org.cytoscape.model.events.RowsSetListener;

import com.dpgil.pathlinker.path_linker.internal.view.PathLinkerControlPanel;

/** Listener class for _loadNodeToSourceButton and _loadNodeToTargetButton in PathLinkerPanel class */
public class PathLinkerNodeSelectionListener implements RowsSetListener {

    /** field to enable/disable updating network combo box */
    private static boolean active;
    /** the PathLinker control panel associated with */
    private PathLinkerControlPanel controlPanel;
    /** the application manager associated with */
    private CyApplicationManager applicationManager;

    /**
     * Default constructor
     * @param controlPanel          the PathLinkerControlPanel
     * @param applicationManager    the CyApplicationManager
     */
    public PathLinkerNodeSelectionListener(
            PathLinkerControlPanel controlPanel, 
            CyApplicationManager applicationManager) {

        this.controlPanel = controlPanel;
        this.applicationManager = applicationManager;
        active = true;
    }

    /**
     * Setter method for active field
     * @param active true if enable updating network combo box
     *          false if disable
     */
    public static void setActive(boolean active) {
        PathLinkerNodeSelectionListener.active = active;
    }

    /**
     * Enables the buttons if user selects a node in the network view, otherwise disable
     */
    @Override
    public void handleEvent(RowsSetEvent e) {
        // update the networkCmb if active is set to true
        // and if user changes rename certain network
        if (active && e.containsColumn(CyNetwork.NAME)) {
            controlPanel.initializeNetworkCmb();
            return;
        }

        // if event is triggered by unselect/select an edge or blank screen then do nothing
        // since user may select node and then edge simultaneously
        // if event is triggered by selecting blank screen, then unselect nodes will triggered another RowsSetEvent to disable buttons
        if (applicationManager.getCurrentNetworkView() == null || 
                e.getSource() != applicationManager.getCurrentNetworkView().getModel().getDefaultNodeTable())
            return;

        // if event is triggered unselect/select node then check if any node is selected, enable buttons if true
        if (e.containsColumn(CyNetwork.SELECTED)) {
            for (RowSetRecord rowSet : e.getColumnRecords(CyNetwork.SELECTED)) {
                if (rowSet.getRow().get(CyNetwork.SELECTED, Boolean.class)) {
                    controlPanel._loadNodeToSourceButton.setEnabled(true);
                    // if the targetsSame AsSources option is selected, then don't allow the user to add more targets
                    if (!controlPanel._targetsSameAsSourcesOption.isSelected())
                        controlPanel._loadNodeToTargetButton.setEnabled(true);
                    return;
                }
            }
        }

        // disable buttons if no node is selected
        controlPanel._loadNodeToSourceButton.setEnabled(false);
        controlPanel._loadNodeToTargetButton.setEnabled(false);
    }
}
