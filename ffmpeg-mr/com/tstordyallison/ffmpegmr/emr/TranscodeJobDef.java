package com.tstordyallison.ffmpegmr.emr;

import com.tstordyallison.ffmpegmr.WriterThread;

public class TranscodeJobDef {
	
	public static enum InputType {RawFile, RawFileCopy, Demuxed}
	public static enum ProcessingType {MapReduce, FFmpeg}
	public static enum OutputType {RawFile, ReducerSegments}
	
	private String jobName = "Unknown Job";
	
	private String inputUri = "";
	private InputType inputType = InputType.Demuxed;
	
	private ProcessingType processingType = ProcessingType.MapReduce;
	
	private String outputUri = "";
	private OutputType outputType = OutputType.ReducerSegments;
	
	private float videoResScale = 1;
	private float videoCrf = 21;
	private int videoBitrate = 512000;
	private int audioBitrate = 128000;
	private int videoThreads = -1;
	
	private boolean overwrite = false;
	private int demuxChunkSize = WriterThread.BLOCK_SIZE;
	
	@SuppressWarnings("unused")
	private TranscodeJobDef()
	{
		// For GSON.
	}
	
	public TranscodeJobDef(String inputUri, String outputUri)
	{
		this.inputUri = inputUri;
		this.outputUri = outputUri;
	}
	
	public String getJobName() {
		return jobName;
	}
	public void setJobName(String jobName) {
		this.jobName = jobName;
	}
	public String getInputUri() {
		return inputUri;
	}
	public void setInputUri(String inputUri) {
		this.inputUri = inputUri;
	}
	public InputType getInputType() {
		return inputType;
	}
	public void setInputType(InputType inputType) {
		this.inputType = inputType;
	}
	public String getOutputUri() {
		return outputUri;
	}
	public void setOutputUri(String outputUri) {
		this.outputUri = outputUri;
	}
	public OutputType getOutputType() {
		return outputType;
	}
	public void setOutputType(OutputType outputType) {
		this.outputType = outputType;
	}
	public float getVideoResScale() {
		return videoResScale;
	}
	public void setVideoResScale(float videoResScale) {
		this.videoResScale = videoResScale;
	}
	public float getVideoCrf() {
		return videoCrf;
	}
	public void setVideoCrf(float videoCrf) {
		this.videoCrf = videoCrf;
	}
	public int getVideoBitrate() {
		return videoBitrate;
	}
	public void setVideoBitrate(int videoBitrate) {
		this.videoBitrate = videoBitrate;
	}
	public int getAudioBitrate() {
		return audioBitrate;
	}
	public void setAudioBitrate(int audioBitrate) {
		this.audioBitrate = audioBitrate;
	}
	public boolean isOverwrite() {
		return overwrite;
	}
	public void setOverwrite(boolean overwrite) {
		this.overwrite = overwrite;
	}
	public int getVideoThreads() {
		return videoThreads;
	}
	public void setVideoThreads(int videoThreads) {
		this.videoThreads = videoThreads;
	}
	public int getDemuxChunkSize() {
		return demuxChunkSize;
	}
	public void setDemuxChunkSize(int demuxChunkSize) {
		this.demuxChunkSize = demuxChunkSize;
	}
	public ProcessingType getProcessingType() {
		return processingType;
	}

	public void setProcessingType(ProcessingType processingType) {
		this.processingType = processingType;
	}

	@Override
	public String toString() {
		return "TranscodeJobDef ["
				+ (jobName != null ? "\n\t\tjobName=" + jobName + ", " : "")
				+ (inputUri != null ? "\n\t\tinputUri=" + inputUri + ", " : "")
				+ (inputType != null ? "\n\t\tinputType=" + inputType + ", " : "")
				+ (outputUri != null ? "\n\t\toutputUri=" + outputUri + ", " : "")
				+ (outputType != null ? "\n\t\toutputType=" + outputType + ", " : "")
				+ "\n\t\tvideoResScale=" + videoResScale + ", \n\t\tvideoCrf=" + videoCrf
				+ ", \n\t\tvideoBitrate=" + videoBitrate + ", \n\t\taudioBitrate=" + audioBitrate 
				+ ", \n\t\tvideoThreads=" + videoThreads + ", \n\t\toverwrite=" + overwrite 
				+ ", \n\t\tdemuxChunkSize=" + demuxChunkSize + "]";
	}
	
}
