/**
 * File: src/dynCG/traceStat.java
 * -------------------------------------------------------------------------------------------
 * Date			Author      Changes
 * -------------------------------------------------------------------------------------------
 * 12/10/15		hcai		created; for parsing traces and calculating statistics 
 * 01/05/16		hcai		the first basic, working version
 * 01/13/16		hcai		added call site tracking for each ICC instance
 * 02/02/16		hcai		added calibration of the types of ICCs that are internal, implicit
 * 02/04/16		hcai		extended to support app-pair traces
 * 02/05/16		hcai		improved the classification of iccs into external vs internal
 * 02/15/16		hcai		added call edges from ICC sender to ICC receiver to the dynamic call graph (to make the 
 * 							src-sink reachability analysis more complete
 * 03/30/16		hcai		parse the caller info newly added to Intent tracing and use it to improve ICC categorization 
 * 05/05/16		hcai		fixed a bug in parsing the caller and callstmt from Intent traces
 * 05/09/16		hcai		fix the method-level taint flow reachability 
*/
package dynCG;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jgrapht.*;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.alg.*;
import org.jgrapht.traverse.*;

import soot.Scene;
import soot.SootClass;

import android.content.Intent;
import dynCG.callGraph.CGEdge;
import dynCG.callGraph.CGNode;
import iacUtil.iccAPICom;

public class traceStat {
	
	private String appPackname=""; // package name set in the Manifest file
	private String appPacknameOther=""; // package name set in the Manifest file for the other APK
	
	private Set<String> clsNames = new HashSet<String>();
	private Set<String> clsNamesOther = new HashSet<String>();
	
	traceStat (String _traceFn, String packname) {
		appPackname = packname;
		this.traceFn = _traceFn;
	}
	
	private String traceFn; // name of trace file
	public traceStat (String _traceFn) {
		this.traceFn = _traceFn;
	}
	
	public traceStat () {
		traceFn = null;
	}
	static int counter = 0;
	public void setPackagenameOther (String packname) {
		
		counter ++; System.out.println("setPackagaenameOther is called " + counter + " times.");
		
		
		this.appPacknameOther = packname; 
	}
	public void setPackagename (String packname) { this.appPackname = packname; }
	public void setClassNames (Set<String> clsnames) { this.clsNames = clsnames; }
	public void setClassNamesOther (Set<String> clsnames) { this.clsNamesOther = clsnames; }
	public void setTracefile (String tfname) { this.traceFn = tfname; }
	
	public static class ICCIntent { //extends Intent {
		public static final String INTENT_SENT_DELIMIT = "[ Intent sent ]";
		public static final String INTENT_RECV_DELIMIT = "[ Intent received ]";
		public static final String[] fdnames = {
			"Action", "Categories", "PackageName", "DataString", "DataURI", "Scheme", "Flags", "Type", "Extras", "Component"};
		public int ts;
		/*
		String action = null;
		String packagename = null;
		String data = null;
		String uri = null;
		String scheme = null;
		String flags = null;
		String type = null;
		String extras = null;
		String component = null;
		*/
		protected boolean bExternal = true;
		protected boolean bIncoming = false;
		public void setExternal (boolean _bv) { bExternal = _bv; }
		public void setIncoming (boolean _bv) { bIncoming = _bv; }
		// mapping from intent field name to field value
		protected Map<String, String> fields = new HashMap<String, String>();
		
		//protected String callsite; // the call site that sends or receives this Intent
		protected CGEdge callsite; // the call site that sends or receives this Intent
		
		protected String caller;
		protected String callstmt;

		ICCIntent() {
			for (String fdname : fdnames) {
				fields.put(fdname, "null");
			}
			ts = -1;
			callsite = null;
			caller = "";
			callstmt = "";
		}
		
		public String toString() {
			String ret = fields.toString() + "\n";
			ret += "ts: " + this.ts + "\n";
			ret += "External ICC: " + bExternal + "\n";
			ret += "Incoming ICC: " + bIncoming + "\n";
			ret += "Explicit ICC: " + isExplicit() + "\n";
			ret += "HasExtras: " + hasExtras() + "\n";
			ret += "call site: " + callsite + "\n";
			ret += "caller: " + caller + ", callStmt: " + callstmt;
			return ret;
		}
		
		// instantiate from a list of field values in the trace
		ICCIntent (List<String> infolines) {
			this();
			for (int i=0; i<infolines.size(); ++i) {
				String line = infolines.get(i).trim();
				for (String fdname : fdnames) {
					String prefix = fdname + "=";
					if (line.startsWith(prefix)) {
						String fdval = line.substring(line.indexOf(prefix) + prefix.length());
						if (fdname.compareTo("Categories")==0 && fdval.compareTo("null")!=0) {
							String _fdval = "";
							for (int j = 0; j < Integer.valueOf(fdval); ++j) {
								line = infolines.get(++i).trim();
								_fdval += line; 
								if (j>0) _fdval += ";";
							}
							fdval = _fdval;
						}
						fields.put(fdname, fdval);
						break;
					}
				}
			}
		}
		
		public void setTS (int _ts) { this.ts = _ts; }
		public int getTS () { return this.ts; }
		
		/*
		public String getCallsite() { return callsite; }
		public void setCallsite (String stmt) { callsite = stmt; }
		*/
		public CGEdge getCallsite() { return callsite; }
		public void setCallsite (CGEdge edge) { callsite = edge; }
		
		public boolean isExplicit () {
			return fields.get("Component").compareTo("null")!=0;
		}
		
		public boolean hasExtras () {
			return fields.get("Extras").compareTo("null")!=0;
		}
		
		public boolean hasData() {
			return (fields.get("DataString").compareTo("null")!=0) || (fields.get("DataURI").compareTo("null")!=0);
		}
		
		public boolean isExternal() {
			return bExternal;
		}
		public boolean isIncoming() {
			return bIncoming;
		}
		
		public String getFields (String fdname) {
			return fields.get(fdname);
		}
	}
	
	private callGraph cg = new callGraph();
	private List<ICCIntent> allIntents = new ArrayList<ICCIntent>();
	private List<Set<ICCIntent>> allInterAppIntents = new ArrayList<Set<ICCIntent>>();
	
	public callGraph getCG () { return cg; }
	public List<ICCIntent> getAllICCs () { return allIntents; }
	public List<Set<ICCIntent>> getInterAppICCs () { return allInterAppIntents; }
	
	private callTree ct = new callTree();
	public callTree getCT() { return ct; }
	public boolean useCallTree = false;
	
	protected ICCIntent readIntentBlock(BufferedReader br) throws IOException {
		// read the caller and callstmt first
		br.mark(1000);
		String caller = br.readLine();
		String callstmt = br.readLine().trim();
		if (!caller.startsWith("caller=") || !callstmt.startsWith("callsite=")) {
			br.reset();
			caller="";
			callstmt="";
		}
		else {
			caller=caller.replaceFirst("caller=","");
			callstmt=callstmt.replaceFirst("callsite=", "");
		}

		List<String> infolines = new ArrayList<String>();
		/*
		int i = 1;
		int total = ICCIntent.fdnames.length;
		String line = null;
		while (i <= total) {
			line = br.readLine();
			if (line == null) break;
			line = line.trim();
			infolines.add(line);
			if (line.startsWith("Categories") && !line.endsWith("=null")) {
				total += Integer.valueOf(line.substring(line.indexOf('=')+1));
			}
			i++;
		}
		*/
		br.mark(2000);
		String line = br.readLine();
		
		while (line != null) {
			line = line.trim();
			boolean stop = true;;
			for (String fdname : ICCIntent.fdnames) {
				if (line.startsWith(fdname)) {
					infolines.add(line);
					if (fdname.equalsIgnoreCase("Categories") && !line.endsWith("=null")) {
						int ninnerlns = Integer.valueOf(line.substring(line.indexOf('=')+1));
						for (int k=0; k < ninnerlns; ++k) {
							infolines.add(br.readLine().trim());
						}
					}
					stop = false;
					break;
				}
			}
			if (stop) break;
			br.mark(2000);
			line = br.readLine();
		}
		
		// not enough lines read for an expected intent block
		if (null == line) {
			throw new IOException("unexpected end reached before reading an Intent object block");
		}
		br.reset();
		
		/*
		boolean yes = false;
		for (String l: infolines)
		if (l.contains("Component")) {
			yes = true;
		}
		if (!yes) 
			System.out.println("stop here");
		*/
		if (infolines.size()<3) return null;
		
		ICCIntent ret = new ICCIntent (infolines);
		ret.caller = caller;
		ret.callstmt = callstmt;
		return ret;
	}
	
	public static boolean isInList(String s, Set<String> strlst) {
		for (String str : strlst) {
			if (s.contains(str) || str.contains(s)) return true;
		}
		return false;
	}
	
	protected int parseTrace (String fnTrace) {
		try {
			BufferedReader br = new BufferedReader (new FileReader(fnTrace));
			String line = br.readLine();
			int ts = 0; // time stamp, for ordering all the method and ICC calls
			while (line != null) {
				line = line.trim();
				// try to retrieve a block of intent info
				boolean boutICC = line.contains(ICCIntent.INTENT_SENT_DELIMIT);
				boolean binICC = line.contains(ICCIntent.INTENT_RECV_DELIMIT);
				if (boutICC || binICC) {
					ICCIntent itn = null;
					try {
						itn = readIntentBlock(br);
					}
					catch (Exception e) { itn = null; }
					if (itn==null) {
						line = br.readLine();
						continue;
					}
					itn.setIncoming(binICC);
					
					// look ahead one more line to find the receiver component
					line = br.readLine();
					if (line==null) continue;
					line = line.trim();
					if (line.contains(callGraph.CALL_DELIMIT)) {
						CGEdge ne = cg.addCall(line,ts);
						if (useCallTree) ct.addCall(line, ts);
						ts ++;
						
						if (binICC) {
							if (iccAPICom.is_IntentReceivingAPI(ne.getTarget().getMethodName())) {
								//itn.setCallsite(ne.toString());
								itn.setCallsite(ne);
							}
							
							String comp = itn.getFields("Component");
							if (comp.compareTo("null")!=0) {
								//String recvCls = line.substring(line.indexOf('<')+1, line.indexOf(": "));
								String recvCls = ne.getSource().getSootClassName();
								if (!this.appPackname.isEmpty()) {
									recvCls = this.appPackname;
								}
								// in case of single APK trace
								if (this.appPacknameOther.isEmpty()) {
									if (comp.contains(recvCls) || isInList(recvCls, clsNames)) {
										itn.setExternal(false);
									}
								}
								else {
									// in case of app-pair trace
									// leave for discretion through src-target matching later
								}
							}
						}
						else { // outgoing ICC
							if (iccAPICom.is_IntentSendingAPI(ne.getTarget().getMethodName())) {
								//itn.setCallsite(ne.getTarget().getSootMethodName());
								itn.setCallsite(ne);
							}
						}
					}
					
					itn.setTS(ts);
					ts ++;
					allIntents.add(itn);
					
					line = br.readLine();
					continue;
				}
				
				// try to retrieve a call line
				if (line.contains(callGraph.CALL_DELIMIT)) {
					cg.addCall(line,ts);
					if (useCallTree) ct.addCall(line, ts);
					ts ++;
				}
				
				// others
				line = br.readLine();
			}
		} catch (FileNotFoundException e) {
			System.err.println("DID NOT find the given file " + fnTrace);
			e.printStackTrace();
			return -1;
		} catch (IOException e) {
			System.err.println("ERROR in reading trace from given file " + fnTrace);
			e.printStackTrace();
			return -1;
		}
		
		return 0;
	}
	
	public void dumpInternals() {
		System.out.println("=== " + allIntents.size() + " Intents === ");
		for (int k = 0; k < allIntents.size(); k++) {
			System.out.println(allIntents.get(k));
		}
		System.out.println("=== " + allInterAppIntents.size() + " Inter-App Intents === ");
		for (int k = 0; k < allInterAppIntents.size(); k++) {
			System.out.println(allInterAppIntents.get(k));
		}
		System.out.println(this.cg);
		this.cg.listEdgeByFrequency();
		this.cg.listCallers();
		this.cg.listCallees();
	}
	
	public void stat() {
		if (this.traceFn == null) return;
		parseTrace (this.traceFn);
		
		/** now, look at all ICCs to find out whether each of the implicit ICCs is indeed internal --- it is internal
		 * if a paired ICC can be found in the same trace
		 */
		calibrateICCTypes();
		addICCToCG();
		
		//this.cg.sanityCheck();
		
		System.out.println(this.getCG());
		if (this.useCallTree) {
			System.out.println(this.getCT());
		}
	}

	private void calibrateICCTypes() {
		/*
		for (ICCIntent out : allIntents) {
			if (out.isIncoming()) continue;
			//if (out.isExplicit()) continue;
			for (ICCIntent in : allIntents) {
				if (!in.isIncoming()) continue;
				//if (in.isExplicit()) continue;
		 */
		Set<ICCIntent> coveredInICCs = new HashSet<ICCIntent>();
		Set<ICCIntent> coveredOutICCs = new HashSet<ICCIntent>();
		for (ICCIntent iit : getAllICCs()) {
			if (iit.isIncoming()) {
				coveredInICCs.add(iit);
			}
			else {
				coveredOutICCs.add(iit);
			}
		}
		for (ICCIntent in : coveredInICCs) {
			for (ICCIntent out : coveredOutICCs) {
				if (in.getFields("Action").compareToIgnoreCase(out.getFields("Action"))==0 && 
					in.getFields("Categories").compareToIgnoreCase(out.getFields("Categories"))==0 && 
					in.getFields("DataString").compareToIgnoreCase(out.getFields("DataString"))==0) {
					
					// single-app trace
					if (this.appPacknameOther.isEmpty()) {
						in.setExternal(false);
						out.setExternal(false);
						if (!clsNames.isEmpty()) { 
							if ((isInList(in.caller,clsNames) && !isInList(out.caller,clsNames)) ||
								 (!isInList(in.caller,clsNames) && isInList(out.caller, clsNames)) ) {
								in.setExternal(true);
								out.setExternal(true);
							}
						}
					}
					else {
						// app-pair trace
						/*
						if (out.getCallsite()!=null && in.getCallsite()!=null) 
						{
							String senderCls = out.getCallsite().getSource().getSootClassName();
							String recverCls = in.getCallsite().getSource().getSootClassName();
						*/
						{
							// use the caller info for each Intent to further calibrate
							String senderCls = out.caller.substring(out.caller.indexOf('<')+1, out.caller.indexOf(':'));
							String recverCls = in.caller.substring(in.caller.indexOf('<')+1, in.caller.indexOf(':'));
							if (senderCls.equalsIgnoreCase(recverCls)) {
								in.setExternal(false);
								out.setExternal(false);
							}
							if ((senderCls.contains(appPackname) || isInList(senderCls, clsNames)) && 
									(recverCls.contains(appPackname) || isInList(recverCls, clsNames))) {
								in.setExternal(false);
								out.setExternal(false);
							}
							if (senderCls.contains(appPacknameOther) || isInList(senderCls, clsNamesOther) && 
								(recverCls.contains(appPacknameOther) || isInList(recverCls, clsNamesOther))) {
								in.setExternal(false);
								out.setExternal(false);
							}
							
							if ( ((senderCls.contains(appPackname) || isInList(senderCls, clsNames)) && 
								(recverCls.contains(appPacknameOther) || isInList(recverCls, clsNamesOther))) || 
								((senderCls.contains(appPacknameOther)||isInList(senderCls, clsNamesOther)) && 
								(recverCls.contains(appPackname)||isInList(recverCls, clsNames))) ) {
								in.setExternal(true);
								out.setExternal(true);
								// okay, these pairs communicate indeed, can be used as inter-app analysis benchmark

								Set<ICCIntent> pair = new HashSet<ICCIntent>();
								pair.add(out);
								pair.add(in);
								this.allInterAppIntents.add(pair);
							}
						}
					}
				}
			}
		}
	}
	
	public Map<ICCIntent, ICCIntent> getICCPairs() {
		Map<ICCIntent, ICCIntent> ICCPairs = new HashMap<ICCIntent, ICCIntent>();
		if (this.traceFn == null) return ICCPairs;
		if (this.allIntents.isEmpty()) return ICCPairs;
		
		Set<ICCIntent> coveredInICCs = new HashSet<ICCIntent>();
		Set<ICCIntent> coveredOutICCs = new HashSet<ICCIntent>();
		for (ICCIntent iit : getAllICCs()) {
			if (iit.isIncoming()) {
				coveredInICCs.add(iit);
			}
			else {
				coveredOutICCs.add(iit);
			}
		}
		/** pairing Intent sender and receiver, and adding call edges between them
		 */
		for (ICCIntent in : coveredInICCs) {
			for (ICCIntent out : coveredOutICCs) {
				// two ICCs should be either both explicit or both implicit to be linked
				if ( (in.isExplicit() && !out.isExplicit()) ||
					 (!in.isExplicit() && out.isExplicit()) ) continue;
				
				// for explicit ICCs, link by target component
				if (in.isExplicit()) {
					if (in.getFields("Component").equalsIgnoreCase(out.getFields("Component"))) {
						ICCPairs.put(in, out);
					}
				}
				
				// for implicit ICCs, match by the triple test "action, category, and data"
				if (!in.isExplicit()) {
					if (in.getFields("Action").compareToIgnoreCase(out.getFields("Action"))==0 && 
						in.getFields("Categories").compareToIgnoreCase(out.getFields("Categories"))==0 && 
						in.getFields("DataString").compareToIgnoreCase(out.getFields("DataString"))==0) {
						ICCPairs.put(in, out);
					}
				}
			}
		}	
		return ICCPairs;
	}
	
	public int addICCToCG() {
		Map<ICCIntent, ICCIntent> ICCPairs = getICCPairs();

		int cnticcedge = 0;
		Set<CGEdge> addededges = new HashSet<CGEdge>();
		for (Map.Entry<ICCIntent, ICCIntent> link : ICCPairs.entrySet()) {
			CGNode innode = null, outnode = null;
			/*
			if (link.getKey().getCallsite()==null || link.getValue().getCallsite()==null) {
				innode = cg.getNodeByName(link.getKey().caller);
				outnode = cg.getNodeByName(link.getValue().caller);
			}
			else {
				innode = cg.getNodeByName(link.getKey().getCallsite().getSource().getSootMethodName());
				outnode = cg.getNodeByName(link.getValue().getCallsite().getSource().getSootMethodName());
			}
			*/
			innode = cg.getNodeByName(link.getKey().caller);
			outnode = cg.getNodeByName(link.getValue().caller);
			if (innode == null || outnode==null) {
				if (link.getKey().getCallsite()!=null && link.getValue().getCallsite()!=null) {
					innode = cg.getNodeByName(link.getKey().getCallsite().getSource().getSootMethodName());
					outnode = cg.getNodeByName(link.getValue().getCallsite().getSource().getSootMethodName());
				}
			}

			if (innode==null || outnode==null || innode.equals(outnode)) continue;
			CGEdge e = cg.addEdge(outnode, innode, link.getKey().getTS());
			if (this.useCallTree) {
				ct.addCall(link.getKey().caller+callTree.CALL_DELIMIT+link.getValue().caller, link.getKey().getTS());
			}
			if (!addededges.add(e)) continue;
			//System.out.println("added edge due to icc: " + outnode + " -> " + innode);
			cnticcedge ++;
		}
		
		System.out.println(ICCPairs.size() + " ICC links found and " + cnticcedge + " cg edges added due to ICC links");
		
		return cnticcedge;
	}

	public static void main(String[] args) {
		// at least one argument is required: trace file name
		if (args.length < 1) {
			System.err.println("too few arguments.");
			return;
		}

		traceStat stater = new traceStat (args[0]);
		stater.stat();
		
		stater.dumpInternals();

		return;
	}
}

/* vim :set ts=4 tw=4 tws=4 */

