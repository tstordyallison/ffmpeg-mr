package com.tstordyallison.ffmpegmr.emr.gui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

import org.joda.time.DateTime;

import com.amazonaws.services.elasticmapreduce.model.DescribeJobFlowsRequest;
import com.amazonaws.services.elasticmapreduce.model.DescribeJobFlowsResult;
import com.amazonaws.services.elasticmapreduce.model.JobFlowDetail;
import com.amazonaws.services.elasticmapreduce.model.StepDetail;
import com.explodingpixels.macwidgets.MacUtils;
import com.explodingpixels.macwidgets.SourceListCategory;
import com.explodingpixels.macwidgets.SourceListItem;
import com.explodingpixels.macwidgets.SourceListModel; 
import com.explodingpixels.macwidgets.SourceListSelectionListener;
import com.tstordyallison.ffmpegmr.emr.JobController;
import com.tstordyallison.ffmpegmr.emr.JobProgressWatcher;
import com.tstordyallison.ffmpegmr.emr.JobProgressWatcher.JobProgressUpdateListener;
import com.tstordyallison.ffmpegmr.emr.LogEntry;
import com.tstordyallison.ffmpegmr.emr.LogWatcher;
import com.tstordyallison.ffmpegmr.emr.LogWatcher.LogWatcherListener;
import com.tstordyallison.ffmpegmr.emr.ProgressFraction;
import com.tstordyallison.ffmpegmr.emr.LogReader;
import com.tstordyallison.ffmpegmr.emr.TimeEntry;
import com.tstordyallison.ffmpegmr.emr.TranscodeJobDef;
import com.tstordyallison.ffmpegmr.emr.TranscodeJobDefList;
import com.tstordyallison.ffmpegmr.hadoop.TranscodeJob;

public class AppWindowController {
	
	private static Executor exec = Executors.newCachedThreadPool();
	private static AppWindowView window; 
	private static JobItem currentItem = null;
	
	private static class JobItem extends SourceListItem{
		private String jobIDAndCount;
		private TranscodeJobDef jobDef;
		private SourceListCategory category;
		
		private JobProgressWatcher progressWatcher;
		private LogWatcher logWatcher;
		
		private JobPanel panel;
		
		public JobItem(String displayName, String jobIDAndCount, TranscodeJobDef jobDef, SourceListCategory category) {
			super(displayName);
			this.jobIDAndCount = jobIDAndCount;
			this.jobDef = jobDef;
			this.category = category;
			
			this.progressWatcher = new JobProgressWatcher(this.jobIDAndCount, false);
			this.logWatcher = new LogWatcher(this.jobIDAndCount, new DateTime(0), false);
			
			// Build our jobPanel
			panel = new JobPanel();
			panel.getJobID().setText(getJobIDAndCount());
			panel.getJobName().setText(jobDef.getJobName());
			panel.getInputFile().setText(jobDef.getInputType().toString());
			panel.getOutputFile().setText(jobDef.getOutputType().toString());
			panel.getJobType().setText(jobDef.getProcessingType().toString());
			
			// Set the progress bar if we have some.
			if(getTotalProgress() != null){
				panel.getProgressBar().setIndeterminate(false);
				panel.getProgressBar().setValue((int)getTotalProgress().getProgressPercentDouble());
				panel.getPercentLabel().setText(String.format("%2.2f%%", getTotalProgress().getProgressPercentDouble()));
			}
			else{
				panel.getProgressBar().setIndeterminate(true);
				panel.getPercentLabel().setText(String.format("%2.2f%%", 0.0f));
			}
			
			// Set the log text if we already have some.
			final String log = getCurrentLogText();
			if(!log.isEmpty()){
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						try {
							panel.getLogOutput().getDocument().insertString(0, log, null);
						} catch (BadLocationException e) {
						}
					}
				});
			}
			
			// Setup the progress and log updates.
			getProgressWatcher().registerProgress(new JobProgressUpdateListener() {
				@Override
				public void streamProgressChanged(JobProgressWatcher sender, int jobCounter, int stream, int newValue, int total) {
					SwingUtilities.invokeLater(new Runnable() {
						@Override
						public void run() {
							panel.getProgressBar().setIndeterminate(false);
							panel.getProgressBar().setValue((int)getTotalProgress().getProgressPercentDouble());
							panel.getPercentLabel().setText(String.format("%2.2f%%", getTotalProgress().getProgressPercentDouble()));
						}
					});
				}
				
				@Override
				public void newStream(JobProgressWatcher sender, int jobCounter, int stream) {
				}
			});
			
			getLogWatcher().registerProgress(new LogWatcherListener() {
				@Override
				public void logEntry(LogWatcher sender, final String text) {
					SwingUtilities.invokeLater(new Runnable() {
						
						@Override
						public void run() {
							try {
								Document doc = panel.getLogOutput().getDocument();
								doc.insertString(doc.getLength(), text + "\n", null);
								panel.getLogOutput().setCaretPosition(doc.getLength());
							} catch (BadLocationException e) {
							}
						}
					});
					
				}
			});
		}
		
		public JobProgressWatcher getProgressWatcher() {
			return progressWatcher;
		}

		public void startUpdates(){
			if(!isMapComplete())
				this.progressWatcher.startMonitoring();
			this.logWatcher.startMonitoring();
		}
		
		public void stopUpdates(){
			this.progressWatcher.endMonitoring();
			this.logWatcher.endMonitoring();
		}

		public TranscodeJobDef getJobDef() {
			return jobDef;
		}
		
		public String getJobIDAndCount(){
			return jobIDAndCount;
		}
		
		public SourceListCategory getCategory(){
			return category;
		}
		
		public Map<Integer, ProgressFraction> getCurrentProgress(){
			if(progressWatcher.getCurrentProgress().keySet().size() == 1) // It should!
			{
				TimeEntry key = progressWatcher.getCurrentProgress().keySet().iterator().next();
				return progressWatcher.getCurrentProgress().get(key);
			}
			return null;
		}
		
		public ProgressFraction getTotalProgress(){
			Map<Integer, ProgressFraction> streamsProgress  = getCurrentProgress();
			if(streamsProgress != null){
				int total = 0;
				int current = 0;
				for(ProgressFraction prog : streamsProgress.values()){
					total += prog.getTotalValue();
					current += prog.getCurrentValue();
				}
				return new ProgressFraction(current, total);
			}
			return null;
		}
		
		public boolean isMapComplete(){
			ProgressFraction prog = getTotalProgress();
			if(prog != null)
				return prog.getCurrentValue() == prog.getTotalValue();
			else
				return false;
		}

		public LogWatcher getLogWatcher() {
			return logWatcher;
		}
		
		public String getCurrentLogText() {
			StringBuilder builder = new StringBuilder();
			for(LogEntry entry : getLogWatcher().getCurrentLog())
				builder.append(entry.toString() + "\n");
			return builder.toString();
		}
	
		public JobPanel getJobPanel(){
			return panel;
		}
	}
	
	
	/**
	 * Launch the application.
	 */
	public static void main(final String[] args) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				try {
					// Set look and feel.
					UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
					
					// Create a window.
					window = new AppWindowView();
					
					// Register for the add a new job button.
					Icon plus = new ImageIcon(MacUtils.class.getResource("/com/explodingpixels/macwidgets/images/plus.png"));
					window.getControlBar().createAndAddButton(plus, new ActionListener() {
						@Override
						public void actionPerformed(ActionEvent e) {
							String jobFlowID = JOptionPane.showInputDialog("Enter the jobFlowID that you wish to view.");
							if(jobFlowID != null && !jobFlowID.isEmpty())
								addJobFlow(window.getJobList().getModel(), jobFlowID);
						}
					});
					
					// Register for the remove benchmark button.
					Icon minus = new ImageIcon(MacUtils.class.getResource("/com/explodingpixels/macwidgets/images/minus.png"));			
					window.getControlBar().createAndAddButton(minus, new ActionListener() {
						
						@Override
						public void actionPerformed(ActionEvent e) {
							final JobItem outputItem = (JobItem)window.getJobList().getSelectedItem();
							if(outputItem != null){	
								if(JOptionPane.showConfirmDialog(window.getWindow(), "Are you sure you want to remove this job flow?") == JOptionPane.YES_OPTION){
									outputItem.stopUpdates();
									window.getJobList().getModel().removeCategory(outputItem.getCategory());
									window.setJobPanel(new JobPanel());
								}
								
							}
						}
					});
	
					// Add all the jobFlowIDs that are in the history stored by Amazon.
					exec.execute(new Runnable() {
						@Override
						public void run() {
							for(int i = 0; i < args.length; i++)
								addJobFlow(window.getJobList().getModel(), args[i]);

							for(JobFlowDetail detail : getCurrentJobFlowIDHistory())
								addJobFlow(window.getJobList().getModel(), detail);
						}
					});
					
					// Register for the item selection events.
					window.getJobList().addSourceListSelectionListener(new SourceListSelectionListener() {
						@Override
						public void sourceListItemSelected(SourceListItem item) {
							updateJob(item);
						}
					});
					
					// Show the window!
					window.getWindow().setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	
	private static List<JobFlowDetail> getCurrentJobFlowIDHistory(){
		DescribeJobFlowsResult checker = JobController.getEmr().describeJobFlows(new DescribeJobFlowsRequest());
		List<JobFlowDetail> jobFlows  = checker.getJobFlows();
		Collections.reverse(jobFlows);
        return jobFlows;
	}
	
	private static void addJobFlow(SourceListModel model, String jobFlowID){
		DescribeJobFlowsResult checker = JobController.getEmr().describeJobFlows(new DescribeJobFlowsRequest().withJobFlowIds(jobFlowID));
		List<JobFlowDetail> list = checker.getJobFlows();
		for(JobFlowDetail detail : list)
			addJobFlow(model, detail);
	}
	
	private static void addJobFlow(final SourceListModel model, JobFlowDetail jobFlow){
		// Add the top level category.
		final SourceListCategory category = new SourceListCategory(jobFlow.getName().replace("ffmpeg-mr: ", "") + " (" + jobFlow.getJobFlowId() + ")");
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				model.addCategory(category);
			}
		});
		
		// Process each of the TranscodeJobDef files and add them to the UI.
		for(StepDetail step : jobFlow.getSteps()){
			if(step.getStepConfig().getHadoopJarStep() != null)
				if(step.getStepConfig().getHadoopJarStep().getMainClass() != null)
					if(step.getStepConfig().getHadoopJarStep().getMainClass().equals("com.tstordyallison.ffmpegmr.hadoop.TranscodeJob")){
						// This is a transcode job.
						List<String> args = step.getStepConfig().getHadoopJarStep().getArgs();
						String uri = args.get(0);
						String jobID = args.get(1);
						
						// Get the job info from S3.
						TranscodeJobDefList job = getTranscodeJobDefList(uri);
						if(job != null){
							// Add the jobs to the UI.
							int counter = 1;
							for(TranscodeJobDef transJob : job.getJobs()){
								// Build the JobItem.
								String displayName = transJob.getJobName();
								if(!displayName.startsWith(Integer.toString(counter) + ".")){
									displayName = Integer.toString(counter) + ". " + displayName;
								}
								
								final JobItem item = new JobItem(displayName, jobID + "-" + counter, transJob, category);
								
								// Add it to the GUI.
								SwingUtilities.invokeLater(new Runnable() {
									public void run() {
										model.addItemToCategory(item, category);
									}
								});

								counter += 1;
							}	
						}
				}
		}
	}
	
	private static Map<String, TranscodeJobDefList> transcodeJobDefListCache = Collections.synchronizedMap(new HashMap<String, TranscodeJobDefList>());
	private static TranscodeJobDefList getTranscodeJobDefList(String uri){
		if(!transcodeJobDefListCache.containsKey(uri)){
			try {
				TranscodeJobDefList newTrans = TranscodeJobDefList.fromJSON(TranscodeJob.getConfig(), uri);
				transcodeJobDefListCache.put(uri, newTrans);
			} catch (Exception e) {
				return null;
			}
		}
		return transcodeJobDefListCache.get(uri);
	}
	
	private static void updateJob(SourceListItem item){
		if(item instanceof JobItem){
			// Stop the updates on the previous item.
			final JobItem prevItem = currentItem;
			exec.execute(new Runnable() {
				@Override
				public void run() {
					// Deregister the current panel.
					if(prevItem != null){
						prevItem.stopUpdates();
					}
				}
			});

			// Set the new panel, and tell it to start updating.
			final JobItem jobItem = (JobItem)item;
			
			// Start updates.
			exec.execute(new Runnable() {
				@Override
				public void run() {
					jobItem.startUpdates();
				}
			});
			
			// Set the panel.
			window.setJobPanel(jobItem.getJobPanel());
			
			// Set this as the new current item.
			currentItem = jobItem;
		}
	}
}
