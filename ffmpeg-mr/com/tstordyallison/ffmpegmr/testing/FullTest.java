package com.tstordyallison.ffmpegmr.testing;

import java.io.IOException;

import com.tstordyallison.ffmpegmr.util.Printer;

public class FullTest {

	public static void main(String[] args)  throws IOException, InterruptedException, InstantiationException, IllegalAccessException{
		
		Printer.ENABLED = true;
		
		ChunkTest.chunkWithTimer("/Users/tom/Documents/FYP/Test.mp4", "file:///Users/tom/Documents/FYP/Test.mp4.seq");
		TransTest.runMapper("file:///Users/tom/Documents/FYP/Test.mp4.seq", "file:///Users/tom/Documents/FYP/Test.mp4.mapped.seq");
		
		
		ChunkTest.chunkWithTimer("/Users/tom/Documents/FYP/Test.mkv", "file:///Users/tom/Documents/FYP/Test.mkv.seq");
		TransTest.runMapper("file:///Users/tom/Documents/FYP/Test.mkv.seq", "file:///Users/tom/Documents/FYP/Test.mkv.mapped.seq");
		
		
		ChunkTest.chunkWithTimer("/Users/tom/Documents/FYP/Test.wmv", "file:///Users/tom/Documents/FYP/Test.wmv.seq");
		TransTest.runMapper("file:///Users/tom/Documents/FYP/Test.wmv.seq", "file:///Users/tom/Documents/FYP/Test.wmv.mapped.seq");
		
		
		ChunkTest.chunkWithTimer("/Users/tom/Documents/FYP/Test.avi", "file:///Users/tom/Documents/FYP/Test.avi.seq");
		TransTest.runMapper("file:///Users/tom/Documents/FYP/Test.avi.seq", "file:///Users/tom/Documents/FYP/Test.avi.mapped.seq");
	}
}
