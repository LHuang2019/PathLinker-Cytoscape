package com.dpgil.pathlinker.path_linker.internal;

import com.dpgil.pathlinker.path_linker.internal.Algorithms.Path;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import javax.swing.GroupLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.GroupLayout.Alignment;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;

import org.cytoscape.application.swing.CytoPanelComponent;
import org.cytoscape.application.swing.CytoPanelName;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyTableUtil;

/**
 * // -------------------------------------------------------------------------
 * /** Panel that displays the results for the PathLinker CytoScape plugin
 *
 * @author Daniel Gil
 * @version Nov 23, 2015
 */
@SuppressWarnings("serial")
public class PathLinkerResultPanel extends JPanel implements CytoPanelComponent {
	/** The k shortest paths generated from the network **/
	private final ArrayList<Path> _results;
	/** The current network associated with the result panel **/
	private final CyNetwork _currentNetwork;
	/** The tab title of the result panel **/
	private String _title;
	private JButton _discardBtn;
	private JButton _exportdBtn;
	private JTable _resultTable;
	private JScrollPane _resultScrollPane;

	/**
	 * Constructor for the result frame class
	 * @param title the title of the result panel
	 * @param currentNetwork the current network associated with the result panel
	 * @param results the results from pathlinker
	 */
	public PathLinkerResultPanel(String title, CyNetwork currentNetwork, ArrayList<Path> results)
	{
		this._title = title;
		this._currentNetwork = currentNetwork;
		this._results = results;
		initializePanel();
	}

	/** 
	 * Listener for the export button
	 * 
	 * Listener is fired when user clicks on the export button is been clicked
	 */
	class ExportButtonListener implements ActionListener {
		// calls exportResultTable method if no error
		@Override
		public void actionPerformed(ActionEvent e) {
			try {
				exportResultTable();
			} catch (IOException e1) {
				JOptionPane.showMessageDialog(null, "Unable to export result due to unexpected error");
			}
		}
	}

	/** 
	 * Listener for the discard button 
	 *
	 * Listener is fired when user clicks on the discard button is been clicked
	 */
	class DiscardButtonListener implements ActionListener {

		// Discard the entire currently selected result panel tab if user chooses yes
		@Override
		public void actionPerformed(ActionEvent e) {

			int choice = JOptionPane.showConfirmDialog(null, "Discarded results will be permanently removed. Continue?");
			if (choice != 0) return; // quit if they say no or cancel

			Container btnParent = _discardBtn.getParent();
			Container panelParent = btnParent.getParent();
			panelParent.remove(btnParent);
		}
	}

	/**
	 * Listener for the paths in result table
	 * When user selects a path in the table, set the related nodes and edges to be selected in the network view
	 * Multiple nodes can be select at once
	 * 
	 * Listener is fired when user selects a column inside the result table
	 */
	class ResultTableListener implements ListSelectionListener {
		@Override
		public void valueChanged(ListSelectionEvent e) {
			ListSelectionModel lsm = (ListSelectionModel)e.getSource();

			List<CyNode> selectedNodes = CyTableUtil.getNodesInState(_currentNetwork, "selected", true);
			List<CyEdge> selectedEdges = CyTableUtil.getEdgesInState(_currentNetwork, "selected", true);

			// clear the original selected nodes and edges from the view
			for (CyNode node : selectedNodes) 
				_currentNetwork.getRow(node).set(CyNetwork.SELECTED, false);
			for (CyEdge edge : selectedEdges) 
				_currentNetwork.getRow(edge).set(CyNetwork.SELECTED, false);

			// return if nothing is selected
			if (lsm.isSelectionEmpty()) return;

			// find the index of first and last nodes
			// user can select multiple indexes at once by dragging cursor or ctrl + clicking
			// therefore checking the nodes in between to make sure all user selected nodes are shown on the network view
			int minIndex = lsm.getMinSelectionIndex();
			int maxIndex = lsm.getMaxSelectionIndex();

			// check the nodes in between the indexes
			for (int i = minIndex; i <= maxIndex; i++) {
				if (lsm.isSelectedIndex(i)) {
					Path currPath = _results.get(i);
					// excluding supersource and supertarget
					for (int j = 1; j < currPath.size() - 2; j++) {
						CyNode node1 = currPath.get(j);
						CyNode node2 = currPath.get(j + 1);

						_currentNetwork.getRow(node1).set(CyNetwork.SELECTED, true);
						_currentNetwork.getRow(node2).set(CyNetwork.SELECTED, true);

						// add all of the directed edges from node1 to node2
						List<CyEdge> edges = _currentNetwork.getConnectingEdgeList(node1, node2, CyEdge.Type.DIRECTED);
						for (CyEdge edge : edges) {
							if (edge.getSource().equals(node1) && edge.getTarget().equals(node2)) { // verifies the edges direction
								_currentNetwork.getRow(edge).set(CyNetwork.SELECTED, true);
							}
						}
						// also add all of the undirected edges from node1 to node2
						edges = _currentNetwork.getConnectingEdgeList(node1, node2, CyEdge.Type.UNDIRECTED);
						for (CyEdge edge : edges) _currentNetwork.getRow(edge).set(CyNetwork.SELECTED, true);
					}
				}
			}
		}
	}

	/**
	 * Sets up all the components in the panel 
	 */
	private void initializePanel() {
	    
	    // set control panel layout to group layout
        final GroupLayout mainLayout = new GroupLayout(this);
        setLayout(mainLayout);

        mainLayout.setAutoCreateContainerGaps(false);
        mainLayout.setAutoCreateGaps(true);

		setUpExportBtn();
		setUpDiscardBtn();
		setupTable();
		
        mainLayout.setHorizontalGroup(mainLayout.createParallelGroup(Alignment.LEADING, true)
                .addGroup(mainLayout.createSequentialGroup()
                        .addComponent(_exportdBtn)
                        .addComponent(_discardBtn))
                .addGroup(mainLayout.createParallelGroup(Alignment.LEADING, true)
                        .addComponent(_resultScrollPane))
                );
        mainLayout.setVerticalGroup(mainLayout.createSequentialGroup()
                .addGroup(mainLayout.createParallelGroup(Alignment.LEADING, true)
                        .addComponent(_exportdBtn)
                        .addComponent(_discardBtn))
                .addGroup(mainLayout.createSequentialGroup()
                        .addComponent(_resultScrollPane))
                );	
	}

	/**
	 * Sets up the export button
	 */
	private void setUpExportBtn()
	{
		_exportdBtn = new JButton("Export");
		_exportdBtn.addActionListener(new ExportButtonListener());
	}

	/**
	 * Sets up the delete button
	 */
	private void setUpDiscardBtn()
	{
		_discardBtn = new JButton("Discard");
		_discardBtn.addActionListener(new DiscardButtonListener());
	}

	/**
	 * Initializes a JPanel given the results from the plugin
	 * @param results the results from the plugin
	 */
	private void setupTable()
	{
		Object[] columnNames = new Object[] { "Path index", "Path score", "Path" };
		Object[][] rowData = new Object[_results.size()][columnNames.length];

		// A decimal formatter to round path score up to 6 decimal places
		DecimalFormat df = new DecimalFormat("#.######");
		df.setRoundingMode(RoundingMode.HALF_UP);

		for (int i = 0; i < _results.size(); i++)
		{
			rowData[i][0] = i + 1;
			rowData[i][1] = df.format(_results.get(i).weight);
			rowData[i][2] = pathAsString(_results.get(i));
		}

		// overrides the default table model to make all cells non editable
		class NonEditableModel
		extends DefaultTableModel
		{
			NonEditableModel(Object[][] data, Object[] colNames)
			{
				super(data, colNames);
			}

			@Override
			public boolean isCellEditable(int row, int column)
			{
				return false;
			}
		}

		// populates the table with the path data
		_resultTable = new JTable(new NonEditableModel(rowData, columnNames));

		// fixes column widths
		TableColumn index = _resultTable.getColumnModel().getColumn(0);
		index.setMaxWidth(100);

		TableColumn score = _resultTable.getColumnModel().getColumn(1);
		score.setMinWidth(85);
		score.setMaxWidth(100);

		TableColumn path = _resultTable.getColumnModel().getColumn(2);
		path.setMinWidth(200);
		path.setPreferredWidth(275);

		// disable resize for horizontal scrollbar
		_resultTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		_resultTable.getSelectionModel().addListSelectionListener(new ResultTableListener());

		// scrollable panel
		_resultScrollPane = new JScrollPane(_resultTable, 
				JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		_resultScrollPane.setMinimumSize(_resultScrollPane.getPreferredSize());
	}

	/**
	 * The method is triggered by exportButtonListener
	 * Creates a dialogue for user to save the result tables
	 * @throws IOException
	 */
	private void exportResultTable() throws IOException {
		// override approveSelection method to warn if user overwrites a existing file when saving
		JFileChooser fc = new JFileChooser() {
			@Override
			public void approveSelection(){
				File f = getSelectedFile();
				if (f.exists() && getDialogType() == SAVE_DIALOG) {
					int result = JOptionPane.showConfirmDialog(this,
							"The file exists, overwrite?","Existing file",JOptionPane.YES_NO_CANCEL_OPTION);
					switch(result) {
					case JOptionPane.YES_OPTION:
						super.approveSelection();
						return;
					case JOptionPane.NO_OPTION:
						return;
					case JOptionPane.CLOSED_OPTION:
						return;
					case JOptionPane.CANCEL_OPTION:
						cancelSelection();
						return;
					}
				}
				super.approveSelection();
			}
		};

		fc.setDialogTitle("Export PathLinker results"); // title of the dialogue
		fc.setSelectedFile(new File(getTitle().replace(' ', '-') + ".tsv")); // set default file name and extension
		int userSelection = fc.showSaveDialog(null);

		if (userSelection == JFileChooser.APPROVE_OPTION) {
			File fileToSave = fc.getSelectedFile();
			BufferedWriter writer = new BufferedWriter(new FileWriter(fileToSave.getAbsolutePath()));

			for(int i = 0; i < _resultTable.getColumnCount(); i++) {
				writer.write(_resultTable.getColumnName(i));

				if (i < _resultTable.getColumnCount() - 1) writer.write("\t");
			}

			for (int i = 0; i < _resultTable.getRowCount(); i++) {
				writer.newLine();
				for(int j = 0; j < _resultTable.getColumnCount(); j++) {
					writer.write((_resultTable.getValueAt(i, j).toString()));

					if (j < _resultTable.getColumnCount() - 1) writer.write("\t");
				}
			}

			writer.close();
			JOptionPane.showMessageDialog(null, "Export successful");
		}
	}

	/**
	 * Converts a path to a string concatenating the node names A path in the
	 * network involving A -> B -> C would return A|B|C
	 *
	 * @param p
	 *            the path to convert to a string
	 * @return the concatenation of the node names
	 */
	private String pathAsString(Path p)
	{
		// builds the path string without supersource/supertarget [1,len-1]
		StringBuilder currPath = new StringBuilder();
		for (int i = 1; i < p.size() - 1; i++)
			currPath.append(p.nodeIdMap.get(p.get(i)) + "|");

		currPath.setLength(currPath.length() - 1);

		return currPath.toString();
	}

	@Override
	public Component getComponent() {
		return this;
	}

	@Override
	public CytoPanelName getCytoPanelName() {
		return CytoPanelName.EAST;
	}

	@Override
	public Icon getIcon() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getTitle() {
		return "PathLinker Result " + _title;
	}
}