package com.tstordyallison.ffmpegmr.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IOUtils;

import com.tstordyallison.ffmpegmr.emr.Logger;

public class FileUtils {
	
	public final static long GIBIBYTE = 1073741824;
	
	public static boolean PRINT_INFO = true;
	
	public static String humanReadableByteCount(long bytes, boolean si) {
	    int unit = si ? 1000 : 1024;
	    if (bytes < unit) return bytes + " B";
	    int exp = (int) (Math.log(bytes) / Math.log(unit));
	    String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp-1) + (si ? "" : "i");
	    return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
	}

	
	  /**
	   * Copies from one stream to another.
	   * @param in InputStrem to read from
	   * @param out OutputStream to write to
	   * @param buffSize the size of the buffer 
	   * @param close whether or not close the InputStream and 
	   * OutputStream at the end. The streams are closed in the finally clause.  
	   */
	  public static void copyBytes(InputStream in, OutputStream out, int buffSize, boolean close, long total, Configuration conf) throws IOException {
		if(PRINT_INFO)
			Logger.println(conf, "Copy progress: 0%");
		long bytesCounter = 0;
		int percentage = 0;
		PrintStream ps = out instanceof PrintStream ? (PrintStream) out : null;
		byte buf[] = new byte[buffSize];
		try {
			int bytesRead = in.read(buf);
			while (bytesRead >= 0) {
				bytesCounter += bytesRead;
				out.write(buf, 0, bytesRead);
				
				int newPercentage = (int)(((double)bytesCounter/total) * 100);
				if(percentage != newPercentage)
				{
					percentage = newPercentage;
				    if(percentage % 5 == 0)
						if(PRINT_INFO)
							Logger.println(conf, String.format("Copy progress: %d%%", percentage));
				}
				
				
				if ((ps != null) && ps.checkError()) {
					throw new IOException("Unable to write to output stream.");
				}
				bytesRead = in.read(buf);
			}
		} finally {
			if (close) {
				out.close();
				in.close();
			}
		}
	  }
	  
	  /**
	   * Copies from one stream to another.
	   * @param in InputStrem to read from
	   * @param out OutputStream to write to
	   * @param conf the Configuration object
	   * @param close whether or not close the InputStream and 
	   * OutputStream at the end. The streams are closed in the finally clause.
	   */
	  public static void copyBytes(InputStream in, OutputStream out, Configuration conf, boolean close, long total)
	    throws IOException {
	    copyBytes(in, out, conf.getInt("io.file.buffer.size", 4096),  close, total, conf);
	  }

	public static boolean copy(Path src, Path dst, boolean deleteSource, boolean overwrite, Configuration conf) throws IOException {
		FileSystem srcFS = FileSystem.get(src.toUri(), conf);
		FileSystem dstFS = FileSystem.get(dst.toUri(), conf);
		return copy(srcFS, src, dstFS, dst, deleteSource, overwrite, conf);
	}
	  
	  /** Copy files between FileSystems. */
	public static boolean copy(FileSystem srcFS, Path src, FileSystem dstFS, Path dst, boolean deleteSource, boolean overwrite, Configuration conf) throws IOException {
		if (srcFS.getFileStatus(src).isDir()) {
			if (!dstFS.mkdirs(dst)) {
				return false;
			}
			FileStatus contents[] = srcFS.listStatus(src);
			for (int i = 0; i < contents.length; i++) {
				copy(srcFS, contents[i].getPath(), dstFS, new Path(dst, contents[i].getPath().getName()), deleteSource, overwrite, conf);
			}
		} else if (srcFS.isFile(src)) {
			InputStream in = null;
			OutputStream out = null;
			try {
				in = srcFS.open(src);
				out = dstFS.create(dst, overwrite);
				long len = srcFS.getFileStatus(src).getLen();
				if(PRINT_INFO)
					Logger.println(conf, "Copying " + src.toUri().toString() + " to " + dst.toUri().toString() + " (" + humanReadableByteCount(len, false) + ")...");
				copyBytes(in, out, conf, true, len);
			} catch (IOException e) {
				IOUtils.closeStream(out); 
				IOUtils.closeStream(in);
				throw e;
			}
		} else {
			throw new IOException(src.toString() + ": No such file or directory");
		}
		if (deleteSource) {
			return srcFS.delete(src, true);
		} else {
			return true;
		}

	}
}
