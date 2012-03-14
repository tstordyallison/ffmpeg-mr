package com.tstordyallison.ffmpegmr.util;

public class ThreadCatcher extends ThreadGroup
{

	public ThreadCatcher() {
		super("Auto shutdown threads.");
	}

	@Override
	public void uncaughtException(Thread t, Throwable e) {
		// Print the stack trace and exit.
		e.printStackTrace();
		System.exit(1);
	}
}