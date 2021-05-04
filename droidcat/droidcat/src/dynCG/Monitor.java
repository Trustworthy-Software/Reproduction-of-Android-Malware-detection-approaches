/**
 * File: src/dynCG/Monitor.java
 * -------------------------------------------------------------------------------------------
 * Date			Author      	Changes
 * -------------------------------------------------------------------------------------------
 * 10/19/15		hcai			Created; for monitoring method events in DynCG 
 * 10/26/15		hcai			added ICC monitoring with time-stamping call events
 *
*/
package dynCG;

import iacUtil.MethodEventComparator;
import iacUtil.logicClock;

import java.io.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.TreeMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/* Monitoring method events in runtime upon 
 * invocations by instrumented probes in the subject
 *
 * to faithfully reproduce the Execute-After algorithm, use two maps and a global counter
 * to track two kinds of events only: entrance (first event) and return-into (last event)
 */
public class Monitor {

	/* for DUAF/Soot to access this class */
	public static void __link() { }
	
	/* first events */
	protected static HashMap<String, Integer> F = new HashMap<String, Integer>();
	/* last events */
	protected static HashMap<String, Integer> L = new HashMap<String, Integer>();
	
	/* all events */
	protected static HashMap<Integer, String> A = new LinkedHashMap<Integer, String>();
	
	/* first message-receiving events */
	protected static HashMap<String, Integer> S = new HashMap<String, Integer>();

	/* the global counter for timp-stamping each method event */
	protected static Integer g_counter = 0;

	/*
	 * Genuine DynCG will only produce a simplified procedure call sequence that reflects the EA relations;
	 * By default this will be compiled. Otherwise if specified to produce the full call sequence including all
	 * intermediate method (enter/returned-into) events
	 */
	protected static boolean DynCGequenceOnly = true;
	
	/* output file for serializing the two event maps */
	protected static String fnEventMaps = "";

	/* a flag ensuring the initialization and termination are both executed exactly once and they are paired*/
	protected static boolean bInitialized = false;
	
	/* debug flag: e.g. for dumping event sequence to human-readable format for debugging purposes, etc. */
	protected static boolean debugOut = true;
	public static void turnDebugOut(boolean b) { debugOut = b; }
	
	/* The "DynCGequenceOnly" option will be set by EARun via this setter */
	public static void setDynCGequenceOnly(boolean b) {
		DynCGequenceOnly = b;
	}
	
	/* The name of serialization target file will be set by EARun via this setter */
	public static void setEventMapSerializeFile(String fname) {
		fnEventMaps = fname;
		
		if (dumpCallmap) {
			callmap.clear();
		}
		F.clear();
		L.clear();
		A.clear();
		synchronized (g_counter) {
			g_counter = 1;
		}
	}
	
	/** Used to avoid infinite recursion */
	private static boolean active = false;
	
	/* if dump the dynamic calling relationship, namely a map from caller to callee */
	protected static boolean dumpCallmap = true;
	protected static final String calleeTag = "after calling ";
	public static void setDumpCallmap(boolean b) {
		dumpCallmap = b;
	}
	private final static Map<String, String> callmap = new LinkedHashMap<String, String>();
	/* output file for serializing the two event maps */
	protected static String fnCallmap = "/tmp/callmap-"+System.currentTimeMillis()+".out";
	
	protected static BufferedWriter cg_writer = null; 
	
	private static final logicClock g_lgclock = new logicClock(new AtomicInteger(0), ""+android.os.Process.myUid());
	
	/* The name of serialization target file will be set by EARun via this setter */
	public static void setCallMapSerializeFile(String fname) {
		fnCallmap = fname;
	}
	
	/* initialize the two maps and the global counter upon the program start event */		
	public synchronized static void initialize() throws Exception{
		if (dumpCallmap) {
			callmap.clear();
			File hcm = new File(fnCallmap+".sync");
			//cg_writer = new BufferedWriter(new FileWriter(fnCallmap+".sync",hcm.exists()));
		}
		F.clear();
		L.clear();
		A.clear();
		synchronized (g_counter) {
			g_counter = 1;
		
			//System.out.println("In Monitor::initialize()");
			if (!DynCGequenceOnly) {
				A.put(g_counter, "program start");
				g_counter++;
			}
		}
		bInitialized = true;
		g_lgclock.initClock(1);
		
		if (logicClock.trackingSender) {
			// just record the ID of local process 
			S.put(""+android.os.Process.myUid(), Integer.MAX_VALUE);
		}
		
		// we are now only care about apps running on the same device
		//intentTracker.Monitor.installClock(g_lgclock);
	}
	
	public synchronized static void enter(String methodname) throws Exception {
		if (!bInitialized) {
			initialize();
		}
		if (active) return;
		active = true;
		try { enter_impl(methodname);}
		finally { active = false; }
	}
	public synchronized static void enter_impl(String methodname) {
		try {
			synchronized (g_counter) {
				g_counter = g_lgclock.getLTS();
				Integer curTS = (Integer) F.get(methodname);
				if (null == curTS) {
					curTS = 0;
					F.put(methodname, g_counter);
				}
				L.put(methodname, g_counter);
	
				if (!DynCGequenceOnly) {
					A.put(g_counter, methodname+":e");
				}
				g_counter ++;
				g_lgclock.increment();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public synchronized static void returnInto(String methodname, String calleeName){
		if (active) return;
		active = true;
		try { returnInto_impl(methodname, calleeName); }
		finally { active = false; }
	}
	/* the callee could be either an actual method called or a trap */
	public synchronized static void returnInto_impl(String methodname, String calleeName){
		//android.util.Log.e("hcai-cg-monitor", methodname + " -> " + calleeName);
		if (dumpCallmap) {
			int idx = calleeName.indexOf(calleeTag);
			if (-1 != idx)  {
				String ceName = calleeName.substring(idx+calleeTag.length()).trim();
				callmap.put(methodname, ceName);
				/*
				try {
					cg_writer.write(methodname + " -> " + ceName + "\n");
				}
				catch (IOException e) {
					e.printStackTrace(System.err);
				}
				*/
				android.util.Log.e("hcai-cg-monitor", methodname + " -> " + ceName);
			}
		}
		
		try {
			synchronized (g_counter) {
				Integer curTS = (Integer) L.get(methodname);
				if (null == curTS) {
					curTS = 0;
				}
				g_counter = g_lgclock.getLTS();
				L.put(methodname, g_counter);
				if (!DynCGequenceOnly) {
					A.put(g_counter, methodname+":i");
				}
	
				g_counter ++;
				g_lgclock.increment();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public synchronized static void libCall(String methodname, String calleeName){
		try { libCall_impl(methodname, calleeName); }
		finally { }
	}
	public synchronized static void libCall_impl(String methodname, String calleeName) {
		try {
			enter(calleeName);
			returnInto(methodname, calleeName);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	/* 
	 * dump the Execute-After sequence that is converted from the two event maps 
	 * upon program termination event 
	 * this is, however, not required but useful for debugging 
	 *
	 */
	public synchronized static void terminate(String where) throws Exception {
		if (bInitialized) {
			bInitialized = false;
		}
		else {
			return;
		}

		synchronized (g_counter) {
			g_counter = g_lgclock.getLTS();
			A.put(g_counter, "program end");
		}
		if (debugOut) {
			dumpEvents();
		}
		
		/** need permission to write files in android environment */
		/*
		serializeEvents();
		
		if (dumpCallmap) {
			dumpCallmap();
		}
		*/
	}
	
	protected synchronized static void dumpCallmap() {
		if ( !fnCallmap.isEmpty() ) {
			File fObj = new File(fnCallmap);
			try {
				BufferedWriter writer = new BufferedWriter(new FileWriter(fObj));
				
				for (Map.Entry<String,String> en : callmap.entrySet()) {
					writer.write(en.getKey() + " -> " +en.getValue() + "\n");
				}
				
				writer.flush();
				writer.close();
			}
			catch (Exception e) {
				e.printStackTrace();
			}
			finally {
				// we won't allow the confusion of overwriting the file with the call maps from multiple executions 
				fnCallmap = "";
			}
		}
	}

	public static void onRecvSenderID(String senderid) {
		/** for each process, only record the first message receiving event for each unique sender */
		Integer curval = S.get(senderid);
		if (null == curval) {
			if (debugOut && senderid.compareToIgnoreCase(""+android.os.Process.myUid())==0) {
				System.out.println("WARNING: receive a message from the local process being sent to itself !!");
			}
			S.put(senderid, g_lgclock.getLTS());
			return;
		}
	}
	
	protected synchronized static void dumpEvents() {
		MethodEventComparator mecF = new MethodEventComparator(F);
		MethodEventComparator mecL = new MethodEventComparator(L);
		TreeMap<String, Integer> sortedF = new TreeMap<String, Integer> ( mecF );
		TreeMap<String, Integer> sortedL = new TreeMap<String, Integer> ( mecL );
		sortedF.putAll(F);
		sortedL.putAll(L);
		
		System.out.println("\n\n[ First events ]\n" + sortedF );
		System.out.println("\n[ Last events ]\n" + sortedL );

		/* put two maps into one but reversed map for producing the EA sequence */
		HashMap<Integer, String> FL = new HashMap<Integer, String>();

		//ArrayList<String> allMethods = new ArrayList<String>(F.keySet());
		//allMethods.addAll( L.keySet() );
		for( Map.Entry<String, Integer> entry : F.entrySet() ) {
			FL.put( entry.getValue(), entry.getKey() );
		}
		for( Map.Entry<String, Integer> entry : L.entrySet() ) {
			FL.put( entry.getValue(), entry.getKey() );
		}

		System.out.println("\n[ Whole Execute-After Sequence ]\n");
		TreeMap<Integer, String> sortedFL = new TreeMap<Integer, String> ( FL );
		for ( Integer ts : sortedFL.keySet() ) {
			String m = (String) sortedFL.get( ts );
			if ( F.containsValue( ts ) ) {
				System.out.println(m + ":f");
				if ( L.containsValue( ts ) ) {
					// according to the entry event monitor as it is designed, if two method have equal time stamps,
					// they must be the same method
					System.out.println(m + ":l");
				}
			}
			else if ( L.containsValue( ts ) ) {
				System.out.println(m + ":l");
			}
			else {
				System.out.println(m + ":?");
			}
		}

		if (!DynCGequenceOnly) {
			System.out.println("\n[ Full Sequence of Method Entry and Returned-into Events]\n");
			TreeMap<Integer, String> treeA = new TreeMap<Integer, String> ( A );
			System.out.println(treeA);
			
			// DEBUG ONLY
			for (Integer ts : treeA.keySet()) {
				System.out.println(ts+"\t"+treeA.get(ts));
			}
		}
	}
	
	/**
	 * since the static member will be hidden when inherited by descendants, there is no point of declaring it as
	 * public/protected to let it be inheritable; Simply speaking, it is associated with no memory object, thus there is
	 * no way to implement polymorphism, which relies on a memory block where the virtual table can reside for 
	 * implementing the polymorphism
	 */
	/*protected*/ private static void serializeEvents() {
		/* serialize for later deserialization in the post-processing phase when impact set is to be computed*/
		if ( !fnEventMaps.isEmpty() ) {
			FileOutputStream fos;
			try {
				fos = new FileOutputStream(fnEventMaps);
				ObjectOutputStream oos = new ObjectOutputStream(fos);
				// TreeMap is not serializable as is HashMap
				oos.writeObject(F);
				oos.writeObject(L);
				if (logicClock.trackingSender) {
					oos.writeObject(S);
				}
				oos.flush();
				oos.close();
				fos.flush();
				fos.close();
			}
			catch (Exception e) {
				e.printStackTrace();
			}
			finally {
				// we won't allow the confusion of overwriting the file with the event maps from multiple executions 
				fnEventMaps = "";
			}
		}
	}
	
	/** give the full DynCG trace length */
	public synchronized static int getFullTraceLength() {
		synchronized (g_counter) {
			return g_counter;
		}
	}
}

/* vim :set ts=4 tw=4 tws=4 */

