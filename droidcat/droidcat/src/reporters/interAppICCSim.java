/**
 * File: src/reporter/interAppICCSim.java
 * -------------------------------------------------------------------------------------------
 * Date			Author      Changes
 * -------------------------------------------------------------------------------------------
 * 01/28/16		hcai		created; inter-app ICC characterization --- not fully working (Soot does not 
 * 							analyze two apks in one process simply with a process-dir including those apks)
*/
package reporters;

import iacUtil.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import soot.*;
import soot.jimple.*;
import soot.toolkits.graph.Block;
import soot.toolkits.graph.BlockGraph;
import soot.toolkits.graph.ExceptionalBlockGraph;
import soot.util.*;

import dua.Extension;
import dua.Forensics;
import dynCG.*;
import dynCG.callGraph.CGNode;
import dynCG.traceStat.ICCIntent;

public class interAppICCSim implements Extension {
	
	protected static reportOpts opts = new reportOpts();
	protected final traceStat stater = new traceStat();
	
	// gross ICC coverage statistics
	protected final covStat inIccCov = new covStat("Incoming ICC Coverage");
	protected final covStat outIccCov = new covStat("Outgoing ICC Coverage");
	
	protected final covStat meCov = new covStat ("method coverage");
	protected int allMethodInCalls = 0;
	
	Set<ICCIntent> coveredInICCs = new HashSet<ICCIntent>();
	Set<ICCIntent> coveredOutICCs = new HashSet<ICCIntent>();
	
	protected final Set<String> allCoveredMethods = new HashSet<String>();
	
	public static void main(String args[]){
		args = preProcessArgs(opts, args);
		
		if (opts.traceFile==null || opts.traceFile.isEmpty()) { // || opts.apkdir==null) {
			// nothing to do
			return;
		}
		
		interAppICCSim grep = new interAppICCSim();
		// examine catch blocks
		dua.Options.ignoreCatchBlocks = false;
		dua.Options.skipDUAAnalysis = true;
		dua.Options.modelAndroidLC = false;
		dua.Options.analyzeAndroid = true;
		
		// analyze the source apk
		soot.options.Options.v().set_output_format(soot.options.Options.output_format_dex);
		soot.options.Options.v().set_force_overwrite(true);
		Scene.v().addBasicClass("com.ironsource.mobilcore.BaseFlowBasedAdUnit",SootClass.SIGNATURES);
		soot.options.Options.v().set_src_prec(soot.options.Options.src_prec_apk);
		soot.options.Options.v().set_process_dir(Collections.singletonList(opts.apkdir));
		
		Forensics.registerExtension(grep);
		Forensics.main(args);
	}
	
	protected static String[] preProcessArgs(reportOpts _opts, String[] args) {
		opts = _opts;
		args = opts.process(args);
		
		String[] argsForDuaF;
		int offset = 0;

		argsForDuaF = new String[args.length + 2 - offset];
		System.arraycopy(args, offset, argsForDuaF, 0, args.length-offset);
		argsForDuaF[args.length+1 - offset] = "-paramdefuses";
		argsForDuaF[args.length+0 - offset] = "-keeprepbrs";
		
		return argsForDuaF;
	}
	
	/**
	 * Descendants may want to use customized event monitors
	 */
	protected void init() {
		// set up the trace stating agent
		stater.setTracefile(opts.traceFile);
		
		// parse the trace
		stater.stat();
		
		for (ICCIntent iit : stater.getAllICCs()) {
			if (iit.isIncoming()) {
				coveredInICCs.add(iit);
				
				inIccCov.incCovered();
			}
			else {
				coveredOutICCs.add(iit);
				
				outIccCov.incCovered();
			}
		}
		
		Set<CGNode> allCGNodes = stater.getCG().getInternalGraph().vertexSet();
		for (CGNode n : allCGNodes) {
			allCoveredMethods.add(n.getSootMethodName());
			
			allMethodInCalls += stater.getCG().getTotalInCalls(n.getMethodName());
		}
	}
	
	public void run() {
		System.out.println("Running static analysis for ICC distribution characterization");

		init();
		
		traverse();
		
		String dir = System.getProperty("user.dir");
		
		try {
			if (opts.debugOut) {
				reportICC(System.out);
				reportICCWithData(System.out);
				reportICCHasExtras(System.out);
				reportICCLinks(System.out);
			}
			else {
				String fngicc = dir + File.separator + "gicc.txt";
				PrintStream psgicc = new PrintStream (new FileOutputStream(fngicc,true));
				reportICC(psgicc);

				String fndataicc = dir + File.separator + "dataicc.txt";
				PrintStream psdataicc = new PrintStream (new FileOutputStream(fndataicc,true));
				reportICCWithData(psdataicc);

				String fnextraicc = dir + File.separator + "extraicc.txt";
				PrintStream psextraicc = new PrintStream (new FileOutputStream(fnextraicc,true));
				reportICCHasExtras(psextraicc);

				String fnicclink = dir + File.separator + "icclink.txt";
				PrintStream psicclink = new PrintStream (new FileOutputStream(fnicclink,true));
				reportICCLinks(psicclink);
			}
		}
		catch (Exception e) {e.printStackTrace();}	

		System.exit(0);
	}
	
	/** obtaining all statically resolved ICCs needs a separate analysis such as IC3 */ 
	//Set<ICCIntent> traversedInICCs = new HashSet<ICCIntent>();
	//Set<ICCIntent> traversedOutICCs = new HashSet<ICCIntent>();
	Map<SootMethod, Set<Stmt>> traversedInICCs = new HashMap<SootMethod, Set<Stmt>>();
	Map<SootMethod, Set<Stmt>> traversedOutICCs = new HashMap<SootMethod, Set<Stmt>>();
	
	public void traverse() {
		/* traverse all classes */
		Iterator<SootClass> clsIt = Scene.v().getClasses().snapshotIterator(); 
		while (clsIt.hasNext()) {
			SootClass sClass = (SootClass) clsIt.next();
			if ( sClass.isPhantom() ) {	continue; }
			/*
			if (!sClass.isApplicationClass()) {
				continue;
			}
			*/
			System.out.println(sClass.getName());
			
			/* traverse all methods of the class */
			Iterator<SootMethod> meIt = sClass.getMethods().iterator();
			while (meIt.hasNext()) {
				SootMethod sMethod = (SootMethod) meIt.next();
				if ( !sMethod.isConcrete() ) {
                    // skip abstract methods and phantom methods, and native methods as well
                    continue; 
                }
				/*
                if ( sMethod.toString().indexOf(": java.lang.Class class$") != -1 ) {
                    // don't handle reflections now either
                    continue;
                }
                */
				String meId = sMethod.getSignature();
				
				meCov.incTotal();
				if (allCoveredMethods.contains(meId)) {
					meCov.incCovered();
				}
				
				Body body = sMethod.retrieveActiveBody();
				PatchingChain<Unit> pchn = body.getUnits();
				
				Iterator<Unit> itchain = pchn.snapshotIterator();
				while (itchain.hasNext()) {
					Stmt s = (Stmt)itchain.next();
					if (iccAPICom.is_IntentSendingAPI(s)) {
						outIccCov.incTotal();
						Set<Stmt> sites = traversedOutICCs.get(sMethod);
						if (null==sites) {
							sites = new HashSet<Stmt>();
							traversedOutICCs.put(sMethod, sites);
						}
						sites.add(s);
					}
					else if (iccAPICom.is_IntentReceivingAPI(s)) {
						inIccCov.incTotal();
						Set<Stmt> sites = traversedInICCs.get(sMethod);
						if (null==sites) {
							sites = new HashSet<Stmt>();
							traversedInICCs.put(sMethod, sites);
						}
						sites.add(s);
					}
				}
				
			} // -- while (meIt.hasNext()) 
			
		} // -- while (clsIt.hasNext())
	}
	
	public void reportICC(PrintStream os) {
		/** report statistics for the current trace */
		if (opts.debugOut) {
			os.println("*** overview ***");
			os.println(inIccCov);
			os.println(outIccCov);
		}
		
		// dynamic
		int int_ex_inc=0, int_ex_out=0, int_im_inc=0, int_im_out=0, ext_ex_inc=0, ext_ex_out=0, ext_im_inc=0, ext_im_out=0;
		int all_data = 0, all_extra = 0;
		for (ICCIntent itn : coveredInICCs) {
			if (itn.isExplicit()) {
				if (itn.isExternal()) ext_ex_inc ++;
				else int_ex_inc ++;
			}
			else {
				if (itn.isExternal()) ext_im_inc ++;
				else int_im_inc ++;
			}
			if (itn.hasData()) all_data++;
			if (itn.hasExtras()) all_extra++;
		}
		for (ICCIntent itn : coveredOutICCs) {
			if (itn.isExplicit()) {
				if (itn.isExternal()) ext_ex_out ++;
				else int_ex_out ++;
			}
			else {
				if (itn.isExternal()) ext_im_out ++;
				else int_im_out ++;
			}
			if (itn.hasData()) all_data++;
			if (itn.hasExtras()) all_extra++;
		}
		//os.println("[ALL]");
		//os.println("int_ex_inc\t int_ex_out\t int_im_inc\t int_im_out\t ext_ex_inc\t ext_ex_out\t ext_im_inc\t ext_im_out");
		if (opts.debugOut) {
			os.println("*** tabulation ***");
			os.print("format: s_all\t d_all\t d_allInCalls\t s_in\t s_out\t d_in\t d_out\t d_alldata\t d_allextra\t ");
			os.println("int_ex_inc\t int_ex_out\t int_im_inc\t int_im_out\t ext_ex_inc\t ext_ex_out\t ext_im_inc\t ext_im_out");
		}
		os.print(meCov.getTotal() + "\t" + meCov.getCovered() + "\t" + allMethodInCalls + "\t" +
				inIccCov.getTotal() + "\t " + outIccCov.getTotal() + "\t " + 
				inIccCov.getCovered() + "\t " + outIccCov.getCovered() + "\t" + 
				all_data + "\t" + all_extra + "\t");
		os.println(int_ex_inc+ "\t " + int_ex_out+ "\t " + int_im_inc+ "\t " + int_im_out+ "\t " + ext_ex_inc+ "\t " 
				+ ext_ex_out+ "\t " + ext_im_inc+ "\t " + ext_im_out);
	}

	public void reportICCWithData(PrintStream os) {
		//// for ICC carrying data only
		//int_ex_inc=0; int_ex_out=0; int_im_inc=0; int_im_out=0; ext_ex_inc=0; ext_ex_out=0; ext_im_inc=0; ext_im_out=0;
		int int_ex_inc=0, int_ex_out=0, int_im_inc=0, int_im_out=0, ext_ex_inc=0, ext_ex_out=0, ext_im_inc=0, ext_im_out=0;
		for (ICCIntent itn : coveredInICCs) {
			if (!itn.hasData()) continue;
			if (itn.isExplicit()) {
				if (itn.isExternal()) ext_ex_inc ++;
				else int_ex_inc ++;
			}
			else {
				if (itn.isExternal()) ext_im_inc ++;
				else int_im_inc ++;
			}
		}
		for (ICCIntent itn : coveredOutICCs) {
			if (!itn.hasData()) continue;
			if (itn.isExplicit()) {
				if (itn.isExternal()) ext_ex_out ++;
				else int_ex_out ++;
			}
			else {
				if (itn.isExternal()) ext_im_out ++;
				else int_im_out ++;
			}
		}
		if (opts.debugOut) {
			os.println("[hasData]");
			os.println("format: int_ex_inc\t int_ex_out\t int_im_inc\t int_im_out\t ext_ex_inc\t ext_ex_out\t ext_im_inc\t ext_im_out");
		}
		os.println(int_ex_inc+ "\t " + int_ex_out+ "\t " + int_im_inc+ "\t " + int_im_out+ "\t " + ext_ex_inc+ "\t " 
				+ ext_ex_out+ "\t " + ext_im_inc+ "\t " + ext_im_out);
	}

	public void reportICCHasExtras(PrintStream os) {
		//// for ICC carrying extraData only
		// int_ex_inc=0; int_ex_out=0; int_im_inc=0; int_im_out=0; ext_ex_inc=0; ext_ex_out=0; ext_im_inc=0; ext_im_out=0;
		int int_ex_inc=0, int_ex_out=0, int_im_inc=0, int_im_out=0, ext_ex_inc=0, ext_ex_out=0, ext_im_inc=0, ext_im_out=0;
		for (ICCIntent itn : coveredInICCs) {
			if (!itn.hasExtras()) continue;
			if (itn.isExplicit()) {
				if (itn.isExternal()) ext_ex_inc ++;
				else int_ex_inc ++;
			}
			else {
				if (itn.isExternal()) ext_im_inc ++;
				else int_im_inc ++;
			}
		}
		for (ICCIntent itn : coveredOutICCs) {
			if (!itn.hasExtras()) continue;
			if (itn.isExplicit()) {
				if (itn.isExternal()) ext_ex_out ++;
				else int_ex_out ++;
			}
			else {
				if (itn.isExternal()) ext_im_out ++;
				else int_im_out ++;
			}
		}
		if (opts.debugOut) {
			os.println("[hasExtras]");
			os.println("format: int_ex_inc\t int_ex_out\t int_im_inc\t int_im_out\t ext_ex_inc\t ext_ex_out\t ext_im_inc\t ext_im_out");
		}
		os.println(int_ex_inc+ "\t " + int_ex_out+ "\t " + int_im_inc+ "\t " + int_im_out+ "\t " + ext_ex_inc+ "\t " 
				+ ext_ex_out+ "\t " + ext_im_inc+ "\t " + ext_im_out);
	}
	
	public void reportICCLinks(PrintStream os) {
		int cntLinks = 0;
		Map<ICCIntent, ICCIntent> ICCPairs = new HashMap<ICCIntent, ICCIntent>();
		for (ICCIntent in : coveredInICCs) {
			for (ICCIntent out : coveredOutICCs) {
				// two ICCs should be either both explicit or both implicit to be linked
				if ( (in.isExplicit() && !out.isExplicit()) ||
					 (!in.isExplicit() && out.isExplicit()) ) continue;
				
				// for explicit ICCs, link by target component
				if (in.isExplicit()) {
					if (in.getFields("Component").equalsIgnoreCase(out.getFields("Component"))) {
						cntLinks ++;
						ICCPairs.put(in, out);
					}
				}
				
				// for implicit ICCs, match by the triple test "action, category, and data"
				if (!in.isExplicit()) {
					if (in.getFields("Action").compareToIgnoreCase(out.getFields("Action"))==0 && 
						in.getFields("Categories").compareToIgnoreCase(out.getFields("Categories"))==0 && 
						in.getFields("DataString").compareToIgnoreCase(out.getFields("DataString"))==0) {
						cntLinks ++;
						ICCPairs.put(in, out);
					}
				}
			}
		}

		if (opts.debugOut) {
			os.println("*** tabulation ***");
			os.print("format: srcICC_component\t tgtICC_component");
		}
		
		for (Map.Entry<ICCIntent, ICCIntent> link : ICCPairs.entrySet()) {
			SootClass incls = null, outcls = null;
			if (link.getKey().getCallsite()!=null) {
				incls = Scene.v().getSootClass(link.getKey().getCallsite().getSource().getSootClassName());
			}
			if (link.getValue().getCallsite()!=null) {
				outcls = Scene.v().getSootClass(link.getValue().getCallsite().getSource().getSootClassName());
			}
			
			if (incls==null || outcls==null) continue;

			os.println(iccAPICom.getComponentType(incls) +"->"+iccAPICom.getComponentType(outcls));
		}
	}
}

/* vim :set ts=4 tw=4 tws=4 */

