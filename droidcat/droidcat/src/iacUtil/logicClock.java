/**
 * File: src/distEA/distlogicClock.java
 * -------------------------------------------------------------------------------------------
 * Date			Author      	Changes
 * -------------------------------------------------------------------------------------------
 * 10/26/15		hcai			Created; for distributed inter-process timing synchronization
*/
package iacUtil;

import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;

import android.content.Intent;

/** the logic clock used for Lamport timestamping */
public class logicClock {
	private AtomicInteger lts; // lamport time stamp
	private String pid; // process id --- unique identifier of a process

	public static boolean threadAsProcess = false;
	public static boolean trackingSender = false;
	
	static {
		String tap = System.getProperty("threadAsProcess");
		if (tap!=null && tap.compareToIgnoreCase("true")==0) {
			threadAsProcess = true;
		}

		String tks = System.getProperty("trackingSender");
		if (tks!=null && tks.compareToIgnoreCase("true")==0) {
			trackingSender = true;
		}
	}
	
	public logicClock(AtomicInteger _lts, String _pid) {
		this.lts = _lts;
		this.pid = _pid;
	}
	public void initClock(int iv) {
		synchronized (lts) {
			lts.set(iv);
		}
	}
	@Override public String toString() {
		return hostId();
	}
	public String hostId() {
		if (threadAsProcess) {
			return pid + Thread.currentThread();
		}
		return pid;
	}
	public synchronized int getLTS() {
		return lts.get();
	}
	public synchronized int getTimestamp() {
		return lts.get();
	}
	public synchronized int setTimestamp(int _lts) {
		return lts.getAndSet(_lts);
	}
	public synchronized int increment() {
		return lts.getAndIncrement();
	}
	public synchronized int updateClock(int other_lts) {
		// update the local (process) clock with the remote (process) clock
		int val = Math.max(other_lts, this.getTimestamp());
		this.setTimestamp(val);
		return this.increment();
	}
	
	public void retrieveClock(Intent it) throws IOException {
		int lts = it.getIntExtra("lts_lgclock", -1);
		if (-1 == lts) {
			// no clock is received
			return;
		}
		
		this.updateClock(lts);
		
		if (trackingSender) {
			String sender = it.getStringExtra("sendername");
			if (null == sender) {
				// no sender info is received
				return;
			}
			dynCG.Monitor.onRecvSenderID(sender);
		}
		
		// remove the clock payload to avoid interfering the original ICC
		it.removeExtra("lts_lgclock");
	}

	public void packClock(Intent it) throws IOException {
		 it.putExtra("lts_lgclock", getTimestamp());
		 if (trackingSender) {
			 it.putExtra("sendername", ""+android.os.Process.myUid());
		 }
	}
}

/* vim :set ts=4 tw=4 tws=4 */
