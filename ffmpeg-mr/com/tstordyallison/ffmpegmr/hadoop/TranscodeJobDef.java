package com.tstordyallison.ffmpegmr.hadoop;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.PropertiesCredentials;
import com.tstordyallison.ffmpegmr.emr.JobLoader;

public class TranscodeJobDef {
	
	public static enum InputType {RawFile, Demuxed}
	public static enum OutputType {RawFile, ReducerSegments}
	
	private String inputUri = "";
	private InputType inputType = InputType.Demuxed;
	
	private String outputUri = "";
	private OutputType outputType = OutputType.ReducerSegments;
	
	private float videoResScale = 1;
	private float videoCrf = 21;
	private int videoBitrate = 512000;
	private int audioBitrate = 64000;
	
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
}
