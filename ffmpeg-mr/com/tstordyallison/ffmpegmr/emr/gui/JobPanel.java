package com.tstordyallison.ffmpegmr.emr.gui;

import javax.swing.JPanel;
import net.miginfocom.swing.MigLayout;
import javax.swing.JLabel;

import java.awt.Color;
import java.awt.Font;
import javax.swing.JTextPane;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;

public class JobPanel extends JPanel {
	private JLabel jobID;
	private JLabel jobName;
	private JLabel inputFile;
	private JLabel outputFile;
	private JLabel jobType;
	private JTextPane logOutput;
	private JLabel lblProgress;
	private JProgressBar progressBar;
	private JScrollPane scrollPane;
	private JLabel lblPercent;

	/**
	 * Create the panel.
	 */
	public JobPanel() {
		setLayout(new MigLayout("", "[::100.00,grow][grow,left]", "[][][][][][][][grow]"));
		setBackground(new Color(0x4e4e4e));
		
		JLabel lblJobId = new JLabel("Job ID:");
		lblJobId.setForeground(Color.WHITE);
		add(lblJobId, "cell 0 0");
		
		jobID = new JLabel("");
		jobID.setForeground(Color.WHITE);
		jobID.setFont(jobID.getFont().deriveFont(jobID.getFont().getStyle() | Font.BOLD));
		add(jobID, "cell 1 0");
		
		JLabel lblSubjob = new JLabel("Job Name:");
		lblSubjob.setForeground(Color.WHITE);
		add(lblSubjob, "cell 0 1");
		
		jobName = new JLabel("");
		jobName.setForeground(Color.WHITE);
		jobName.setFont(new Font("Lucida Grande", Font.BOLD, 13));
		add(jobName, "cell 1 1");
		
		JLabel lblInputFile = new JLabel("Input File:");
		lblInputFile.setForeground(Color.WHITE);
		add(lblInputFile, "cell 0 2");
		
		inputFile = new JLabel("");
		inputFile.setForeground(Color.WHITE);
		inputFile.setFont(inputFile.getFont().deriveFont(inputFile.getFont().getStyle() | Font.BOLD));
		add(inputFile, "cell 1 2");
		
		JLabel lblOutputFile = new JLabel("Output File:");
		lblOutputFile.setForeground(Color.WHITE);
		add(lblOutputFile, "cell 0 3");
		
		outputFile = new JLabel("");
		outputFile.setForeground(Color.WHITE);
		outputFile.setFont(outputFile.getFont().deriveFont(outputFile.getFont().getStyle() | Font.BOLD));
		add(outputFile, "cell 1 3");
		
		JLabel lblJobType = new JLabel("Job Type:");
		lblJobType.setForeground(Color.WHITE);
		add(lblJobType, "cell 0 4");
		
		jobType = new JLabel("");
		jobType.setForeground(Color.WHITE);
		jobType.setFont(jobType.getFont().deriveFont(jobType.getFont().getStyle() | Font.BOLD));
		add(jobType, "cell 1 4");
		
		lblProgress = new JLabel("Map Progress:");
		lblProgress.setForeground(Color.WHITE);
		add(lblProgress, "cell 0 5");
		
		progressBar = new JProgressBar();
		add(progressBar, "flowx,cell 1 5,growx");
		
		JLabel lblLogOutput = new JLabel("Log Output:");
		lblLogOutput.setForeground(Color.WHITE);
		add(lblLogOutput, "cell 0 6");
		
		scrollPane = new JScrollPane();
		add(scrollPane, "cell 0 7 2 1,grow");
		
		logOutput = new JTextPane();
		logOutput.setFont(new Font("Monaco", Font.PLAIN, 12));
		scrollPane.setViewportView(logOutput);
		
		lblPercent = new JLabel("0.00%");
		lblPercent.setFont(lblPercent.getFont().deriveFont(lblPercent.getFont().getStyle() | Font.BOLD));
		lblPercent.setForeground(Color.WHITE);
		add(lblPercent, "cell 1 5");

	}

	public JLabel getJobID() {
		return jobID;
	}
	public JLabel getJobName() {
		return jobName;
	}
	public JLabel getInputFile() {
		return inputFile;
	}
	public JLabel getOutputFile() {
		return outputFile;
	}
	public JLabel getJobType() {
		return jobType;
	}
	public JTextPane getLogOutput() {
		return logOutput;
	}
	public JProgressBar getProgressBar() {
		return progressBar;
	}
	public JLabel getPercentLabel() {
		return lblPercent;
	}
}
