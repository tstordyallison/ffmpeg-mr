package com.tstordyallison.ffmpegmr.hadoop;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.WritableComparator;

public class FudgeLongComparator extends WritableComparator {
	
	private static short fudgeFactor = 25; // 50 Milliseconds fudge factor (distance ts can be apart).
	
    public FudgeLongComparator() {
      super(LongWritable.class);
    }

    public int compare(byte[] b1, int s1, int l1, byte[] b2, int s2, int l2) {
      long thisValue = readLong(b1, s1);
      long thatValue = readLong(b2, s2);
      
      if(Math.abs(thisValue-thatValue) < fudgeFactor*2)
    	 return 0;
	  else
		 return (thisValue<thatValue ? -1 : 1);
    }
}
