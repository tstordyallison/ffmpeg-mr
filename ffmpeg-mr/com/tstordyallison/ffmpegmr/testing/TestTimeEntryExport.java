package com.tstordyallison.ffmpegmr.testing;

import java.io.IOException;
import com.tstordyallison.ffmpegmr.emr.TimeEntryCSVWriter;

public class TestTimeEntryExport {

	public static void main(String[] args) throws IOException {
		
		TimeEntryCSVWriter.outputTimings("./MinimumChunkSize-1GOP-20120404-135510.csv", "j-QNVKB1E4GUL0");
	}

}
