/**
 * File: src/eventTracker/Monitor.java
 * -------------------------------------------------------------------------------------------
 * Date			Author      	Changes
 * -------------------------------------------------------------------------------------------
 * 2/04/17		hcai			created; for monitoring all events that trigger event-handling callbacks
*/
package eventTracker;

/**
 * runtime monitors of ICC intent resolution
 */
public class Monitor {
	/** Used to avoid infinite recursion */
	private static boolean active = false;
	
	public synchronized static void onEvent (String eventName, String eventType) {
		if (active) return;
		active = true;
		try { onEvent_impl(eventName, eventType); }
		finally { active = false; }
	}
	private synchronized static void onEvent_impl(String eventName, String eventType) {
		try {
            //System.out.println("from iacMonitor:" + itn);
            //android.util.Log.w("event-monitor", itn.toString());
			android.util.Log.e("event-monitor", "[ Event ] [" + eventType + "] [" + eventName + "]");
            //dumpIntent();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public synchronized static void dumpEvent(Object e) {
		String s = "";
		//s += "============= Start ===========\n";
		//try {s += "\tAction="+itn.getAction()+"\n";} catch (Exception e) {}
		//s += "============= End ==========="+"\n";
		android.util.Log.println(0, "event-monitor", s);
		android.util.Log.e("event-monitor", s);
	}
}

/* vim :set ts=4 tw=4 sws=4 */

