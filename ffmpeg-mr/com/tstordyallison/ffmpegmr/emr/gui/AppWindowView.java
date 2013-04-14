package com.tstordyallison.ffmpegmr.emr.gui;

import javax.swing.JFrame;
import com.explodingpixels.macwidgets.MacUtils;
import com.explodingpixels.macwidgets.SourceList;
import com.explodingpixels.macwidgets.SourceListControlBar;
import com.explodingpixels.macwidgets.SourceListDarkColorScheme;
import com.explodingpixels.macwidgets.SourceListModel;
import javax.swing.border.SoftBevelBorder;
import javax.swing.border.BevelBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import javax.swing.JSplitPane;
import javax.swing.JPanel;
import net.miginfocom.swing.MigLayout;
import java.awt.FlowLayout;
import java.awt.Font;


public class AppWindowView {

	private JFrame frmProgressViewer;
	private SourceList jobList;
	private JPanel outputPanel;
	private SourceListControlBar slcb;
	private JobPanel jobPanel;
	private JPanel jobContainer;
	

	/**
	 * Create the application.
	 */
	public AppWindowView() {
		initialize();
	}

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {
		frmProgressViewer = new JFrame();
		frmProgressViewer.setTitle("FFmpeg-mr: Benchmark Progress Viewer");
		frmProgressViewer.setBounds(150, 150, 1024, 800);
		frmProgressViewer.setMinimumSize(new Dimension(300, 300));
		frmProgressViewer.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frmProgressViewer.getContentPane().setLayout(new BorderLayout(0, 0));
		MacUtils.makeWindowLeopardStyle(frmProgressViewer.getRootPane());
		
		JSplitPane splitPane = new JSplitPane();
		splitPane.setResizeWeight(0.20);
		splitPane.setDividerLocation(275);
		frmProgressViewer.getContentPane().add(splitPane, BorderLayout.CENTER);
		
		jobList = new SourceList(new SourceListModel());
		jobList.setColorScheme(new SourceListDarkColorScheme());
		jobList.useIAppStyleScrollBars();
		
		Component imageListComponent = jobList.getComponent();
		imageListComponent.setMinimumSize(new Dimension(150, 0));
		imageListComponent.setMaximumSize(new Dimension(250, Integer.MAX_VALUE));
		
		JPanel leftPanel = new JPanel();
		leftPanel.setLayout(new BorderLayout(20, 0));
		leftPanel.add(imageListComponent, BorderLayout.CENTER);
	
		slcb = new SourceListControlBar();
		slcb.installDraggableWidgetOnSplitPane(splitPane);
		leftPanel.add(slcb.getComponent(), BorderLayout.SOUTH);
		
		splitPane.setLeftComponent(leftPanel);
		
		outputPanel = new JPanel();
		outputPanel.setBorder(null);
		outputPanel.setMinimumSize(new Dimension(500, 300));
		splitPane.setRightComponent(outputPanel);
		outputPanel.setLayout(new MigLayout("fill, insets 0", "[grow]", "[grow]"));
		
		jobContainer = new JPanel();
		jobContainer.setBorder(null);
		outputPanel.add(jobContainer, "cell 0 0,grow");
		jobContainer.setLayout(new BorderLayout(0, 0));
		
		jobPanel = new JobPanel();
		jobPanel.getLogOutput().setFont(new Font("Monaco", Font.PLAIN, 12));
		jobContainer.add(jobPanel);

	}
	
	public JFrame getWindow()
	{
		return frmProgressViewer;
	}
	
	public SourceList getJobList()
	{
		return jobList;
	}
	
	public SourceListControlBar getControlBar()
	{
		return slcb;
	}
	public JPanel getOutputPanel() {
		return outputPanel;
	}
	public JobPanel getJobPanel() {
		return jobPanel;
	}
	public void setJobPanel(JobPanel panel){
		jobContainer.remove(jobPanel);
		this.jobPanel = panel;
		jobContainer.add(BorderLayout.CENTER, panel);
		
		// Refresh everything.
		jobContainer.revalidate();
		jobContainer.repaint();
		frmProgressViewer.validate();
		frmProgressViewer.repaint();
	}
}
