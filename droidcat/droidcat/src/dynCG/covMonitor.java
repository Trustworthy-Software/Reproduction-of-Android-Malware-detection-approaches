/**
 * File: src/dynCG/covMonitor.java
 * -------------------------------------------------------------------------------------------
 * Date			Author      	Changes
 * -------------------------------------------------------------------------------------------
 * 06/18/16		hcai		Created; for monitoring user code statement coverage
 * 06/19/16		hcai		reached the working version
 *
*/
package dynCG;

import java.util.HashSet;
import java.util.Set;

public class covMonitor {
	// record the IDs of covered statements
	protected static Set<Integer> covS = new HashSet<Integer>();
	
	/** Used to avoid infinite recursion */
	private static boolean active = false;
	
	private static int curN = 0;
	
	// for reckoning the coverage status of the given statement
	public synchronized static void sprobe(int sid, int sloc) throws Exception {
		if (active) return;
		active = true;
		try { sprobe_impl(sid,sloc);}
		finally { active = false; }
	}
	public synchronized static void sprobe_impl(int sid, int sloc) {
		try {
			if (covS.add(sid)) {
				//android.util.Log.e("hcai-cov-monitor", "unique #statements covered " + covS.size());
				if (curN==0 || (covS.size()*1.0/sloc - curN*1.0/sloc >= 0.01)) {
					//android.util.Log.e("hcai-cov-monitor", "unique #statements covered " + covS.size());
					android.util.Log.e("hcai-cov-monitor", "coverage reached to " + (covS.size()*1.0/sloc*100) + "%");
					curN = covS.size();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

/* vim :set ts=4 tw=4 tws=4 */

