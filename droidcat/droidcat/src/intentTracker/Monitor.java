/**
 * File: src/intentTracker/Monitor.java
 * -------------------------------------------------------------------------------------------
 * Date			Author      	Changes
 * -------------------------------------------------------------------------------------------
 * 09/22/15		hcai			Created; for monitoring implicit intent target resolution
 * 10/14/15		hcai			separate monitors for events of sending an intent from those for receiving
 * 								an intent
 * 3/30/16		hcai			monitor caller and callsite for each ICC API call additionally to ease trace analysis
*/
package intentTracker;

import iacUtil.logicClock;
import android.content.Intent;

/**
 * runtime monitors of ICC intent resolution
 */
public class Monitor {
	/** Used to avoid infinite recursion */
	private static boolean active = false;
	
	private static logicClock g_lgclock = null;
	public static void installClock (logicClock lgclock) {
		g_lgclock = lgclock;
	}
	
	public synchronized static void onSendIntent(Intent itn, String caller, String callsite) {
		if (active) return;
		active = true;
		try { onSendIntent_impl(itn,caller,callsite); }
		finally { active = false; }
	}
	private synchronized static void onSendIntent_impl(Intent itn, String caller, String callsite) {
		try {
            //System.out.println("from iacMonitor:" + itn);
            //android.util.Log.w("hcai-intent-monitor", itn.toString());
			android.util.Log.e("hcai-intent-monitor", "[ Intent sent ]");
			android.util.Log.e("hcai-intent-monitor", "caller=" + caller);
			android.util.Log.e("hcai-intent-monitor", "callsite=" + callsite);
            dumpIntent(itn);
            if (g_lgclock != null) {
            	g_lgclock.packClock(itn);
            }
            //itn.resolveActivity(android.app.Fragment.getActivity().getPackageManager());
            // piggyback sender information for precise determination of the Intent being internal/external at runtime
            /** just use the caller in the log immediately preceding this Intent instead */
            //itn.putExtra("sender", caller);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public synchronized static void onRecvIntent(Intent itn, String caller, String callsite) {
		if (active) return;
		active = true;
		try { onRecvIntent_impl(itn,caller,callsite); }
		finally { active = false; }
	}
	private synchronized static void onRecvIntent_impl(Intent itn, String caller, String callsite) {
		try {
            //System.out.println("from iacMonitor:" + itn);
            //android.util.Log.w("hcai-intent-monitor", itn.toString());
			android.util.Log.e("hcai-intent-monitor", "[ Intent received ]");
			android.util.Log.e("hcai-intent-monitor", "caller=" + caller);
			android.util.Log.e("hcai-intent-monitor", "callsite=" + callsite);
            if (g_lgclock != null) {
            	g_lgclock.retrieveClock(itn);
            }
            dumpIntent(itn);
            //itn.resolveActivity(android.app.Fragment.getActivity().getPackageManager());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public synchronized static void dumpIntent(Intent itn) {
		/*
		android.util.Log.e("hcai-intent-monitor", "============= Start ===========");
		android.util.Log.e("hcai-intent-monitor", "\tAction="+itn.getAction());
		android.util.Log.e("hcai-intent-monitor", "\tCategories="+itn.getCategories().size());
		for (String cat : itn.getCategories()) {
			android.util.Log.e("hcai-intent-monitor", "\t\t"+cat);
		}
		android.util.Log.e("hcai-intent-monitor", "\tPackageName="+itn.getPackage());
		android.util.Log.e("hcai-intent-monitor", "\tDataString=" + itn.getDataString());
		android.util.Log.e("hcai-intent-monitor", "\tDataURI=" + itn.getData());
		android.util.Log.e("hcai-intent-monitor", "\tScheme=" + itn.getScheme());
		android.util.Log.e("hcai-intent-monitor", "\tFlags=" + itn.getFlags());
		android.util.Log.e("hcai-intent-monitor", "\tType=" + itn.getType());
		android.util.Log.e("hcai-intent-monitor", "\tExtras=" + itn.getExtras());
		android.util.Log.e("hcai-intent-monitor", "\tComponent=" + itn.getComponent());
		android.util.Log.e("hcai-intent-monitor", "============= End ===========");
		*/
		
		String s = "";
		//s += "============= Start ===========\n";
		try {s += "\tAction="+itn.getAction()+"\n";} catch (Exception e) {}
		try {s += "\tCategories="+itn.getCategories().size()+"\n";} catch (Exception e) {}
		try {
			for (String cat : itn.getCategories()) {
				s += "\t\t"+cat+"\n";
			}
		} catch (Exception e) {}
		try {s += "\tPackageName="+itn.getPackage()+"\n";} catch (Exception e) {} 
		try {s += "\tDataString=" + itn.getDataString()+"\n";} catch (Exception e) {}
		try {s += "\tDataURI=" + itn.getData()+"\n";} catch (Exception e) {}
		try {s += "\tScheme=" + itn.getScheme()+"\n";} catch (Exception e) {}
		try {s += "\tFlags=" + itn.getFlags()+"\n";} catch (Exception e) {}
		try {s += "\tType=" + itn.getType()+"\n";} catch (Exception e) {}
		try {s += "\tExtras=" + itn.getExtras()+"\n";} catch (Exception e) {}
		try {s += "\tComponent=" + itn.getComponent()+"\n";} catch (Exception e) {}
		//s += "============= End ==========="+"\n";
		
		android.util.Log.println(0, "hcai-intent-monitor", s);
		android.util.Log.e("hcai-intent-monitor", s);
	}
}

/* vim :set ts=4 tw=4 sws=4 */

