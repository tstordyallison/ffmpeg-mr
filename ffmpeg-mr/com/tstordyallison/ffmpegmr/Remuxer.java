package com.tstordyallison.ffmpegmr;

import com.tstordyallison.ffmpegmr.util.NativeUtil;

public class Remuxer {
	
	static{
		NativeUtil.loadFFmpegMR();
	}
	
	public native static byte[] muxChunks(Iterable<Chunk> chunks);
}
