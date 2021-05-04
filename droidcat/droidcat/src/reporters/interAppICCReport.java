/**
 * File: src/reporter/interAppICCReport.java
 * -------------------------------------------------------------------------------------------
 * Date			Author      Changes
 * -------------------------------------------------------------------------------------------
 * 01/28/16		hcai		created; inter-app ICC characterization
 * 01/29/16		hcai		debugged away the inconsistent analysis results between different orders of 
 * 							analyzing the two APKs
 * 02/04/16		hcai		added one more result: inter-app ICC actually exercised between the two given apps
 * 02/05/16		hcai		fixed the bug in ICC classification and result reporting
*/
package reporters;

import iacUtil.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.xmlpull.v1.XmlPullParserException;

import soot.*;
import soot.jimple.*;
import soot.jimple.infoflow.android.manifest.ProcessManifest;
import soot.toolkits.graph.Block;
import soot.toolkits.graph.BlockGraph;
import soot.toolkits.graph.ExceptionalBlockGraph;
import soot.util.*;

import dynCG.*;
import dynCG.callGraph.CGNode;
import dynCG.traceStat.ICCIntent;


public class interAppICCReport {
	
	protected static reportOpts opts = new reportOpts();
	protected final traceStat stater = new traceStat();
	
	// gross ICC coverage statistics
	protected final covStat inIccCov = new covStat("Incoming ICC Coverage");
	protected final covStat outIccCov = new covStat("Outgoing ICC Coverage");
	
	protected final covStat meCov = new covStat ("method coverage");
	protected int allMethodInCalls = 0;
	
	static String packName = "";
	static String packNameOther = "";
	
	Set<ICCIntent> coveredInICCs = new HashSet<ICCIntent>();
	Set<ICCIntent> coveredOutICCs = new HashSet<ICCIntent>();
	
	static final Set<SootClass> allSootClasses = new HashSet<SootClass>();

	static final Set<String> clsNames = new HashSet<String>();
	static final Set<String> clsNamesOther = new HashSet<String>();

	static final Map<String, String> cls2comtype = new HashMap<String, String>();
	
	protected final Set<String> allCoveredMethods = new HashSet<String>();
	
	public static void main(String args[]) throws IOException, XmlPullParserException{
		args = preProcessArgs(opts, args);
		for (int i=0; i < args.length; i++) {
			if (args[i].equalsIgnoreCase("-allowphantom")) args[i] = "-allow-phantom-refs";
		}
		
		if (opts.traceFile==null || opts.traceFile.isEmpty() || opts.secondapk==null) {
			// nothing to do
			return;
		}
		
		// analyze the source apk
		System.out.println("\n\n Analyzing the first APK " + opts.firstapk);
		soot.options.Options.v().parse(args);
		soot.options.Options.v().set_output_format(soot.options.Options.output_format_dex);
		soot.options.Options.v().set_force_overwrite(true);
		soot.options.Options.v().set_allow_phantom_refs(true);
		Scene.v().addBasicClass("com.ironsource.mobilcore.BaseFlowBasedAdUnit",SootClass.SIGNATURES);
		soot.options.Options.v().set_src_prec(soot.options.Options.src_prec_apk);
		soot.options.Options.v().set_process_dir(Collections.singletonList(opts.firstapk));

		//PackManager.v().getPack("wjtp").add(new Transform("wjtp.mt", new firstAPK()));
		//try { soot.Main.main(args); }catch (Exception e){}
		Scene.v().loadNecessaryClasses();
		//PackManager.v().runPacks();
		
		{
			Iterator<SootClass> clsIt = Scene.v().getClasses().snapshotIterator();
			while (clsIt.hasNext()) {
				SootClass sClass = (SootClass) clsIt.next();
				if ( sClass.isPhantom() ) {	continue; }
				allSootClasses.add(sClass);
				clsNames.add(sClass.getName());
				//Scene.v().removeClass(sClass);
				String comt = iccAPICom.getComponentType(sClass);
				if (!comt.equalsIgnoreCase("Unknown")) {
					cls2comtype.put(sClass.getName(), comt);
				}
			}
		}
		//System.out.println("number of sootClasses found so far: " + allSootClasses.size() + "; number of components: " + cls2comtype.size());
		packName = new ProcessManifest(opts.firstapk).getPackageName();

		System.out.println("\n\n Analyzing the second APK " + opts.secondapk);
		
		// analyze the target apk
		soot.G.reset();
		iccAPICom.fhar = null;
		Scene.v().releaseFastHierarchy();
		soot.options.Options.v().parse(args);
		soot.options.Options.v().set_output_format(soot.options.Options.output_format_dex);
		soot.options.Options.v().set_force_overwrite(true);
		soot.options.Options.v().set_allow_phantom_refs(true);
		Scene.v().addBasicClass("com.ironsource.mobilcore.BaseFlowBasedAdUnit",SootClass.SIGNATURES);
		soot.options.Options.v().set_src_prec(soot.options.Options.src_prec_apk);
		soot.options.Options.v().set_process_dir(Collections.singletonList(opts.secondapk));
		//soot.Main.main(args);
		Scene.v().loadNecessaryClasses();
		//PackManager.v().runPacks();
		iccAPICom.reInitializeComponentTypeClasses();
		{
			Iterator<SootClass> clsIt = Scene.v().getClasses().snapshotIterator();
			while (clsIt.hasNext()) {
				SootClass sClass = (SootClass) clsIt.next();
				if ( sClass.isPhantom() ) {	continue; }
				allSootClasses.add(sClass);
				clsNamesOther.add(sClass.getName());
				String comt = iccAPICom.getComponentType(sClass);
				if (!comt.equalsIgnoreCase("Unknown")) {
					cls2comtype.put(sClass.getName(), comt);
				}
			}
		}
		System.out.println("#sootclasses: " + allSootClasses.size());
		System.out.println("#components: " + cls2comtype.size());
		packNameOther = new ProcessManifest(opts.secondapk).getPackageName();
		
		new interAppICCReport().run();
	}
	
	protected static String[] preProcessArgs(reportOpts _opts, String[] args) {
		opts = _opts;
		args = opts.process(args);
		
		return args;
	}
	
	/**
	 * Descendants may want to use customized event monitors
	 */
	protected void init() {
		// set up the trace stating agent
		stater.setPackagename(packName);
		stater.setPackagenameOther(packNameOther);
		stater.setClassNames(clsNames);
		stater.setClassNamesOther(clsNamesOther);
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

			meCov.incCovered();
		}
	}
	
	public void run() {
		System.out.println("Running static analysis for inter-app ICC distribution characterization");

		init();
		
		//traverse();
		
		String dir = System.getProperty("user.dir");
		
		try {
			if (opts.debugOut) {
				reportICC(System.out);
				reportICCWithData(System.out);
				reportICCHasExtras(System.out);
				reportICCHasDataAndExtras(System.out);
				reportICCLinks(System.out);
				reportInterAppPairs(System.out);
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
				
				String fnbothdataicc = dir + File.separator + "bothdataicc.txt";
				PrintStream psbothdataicc = new PrintStream (new FileOutputStream(fnbothdataicc,true));
				reportICCHasDataAndExtras(psbothdataicc);

				String fnicclink = dir + File.separator + "icclink.txt";
				PrintStream psicclink = new PrintStream (new FileOutputStream(fnicclink,true));
				reportICCLinks(psicclink);

				String fnpairicc = dir + File.separator + "pairicc.txt";
				PrintStream pspairicc = new PrintStream (new FileOutputStream(fnpairicc,true));
				reportInterAppPairs(pspairicc);
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
		Iterator<SootClass> clsIt = allSootClasses.iterator();
		while (clsIt.hasNext()) {
			SootClass sClass = (SootClass) clsIt.next();
			if ( sClass.isPhantom() ) {	continue; }
			boolean isAppCls = false, isSDKCls = false, isULCls = false;
			//if ( sClass.isApplicationClass() ) {
			if (sClass.getName().contains(packName)) {	
				isAppCls = true;
			}
			else {
				// differentiate user library from SDK library
				if (sClass.getName().matches(generalReport.AndroidClassPattern) || sClass.getName().matches(generalReport.OtherSDKClassPattern)) {
					isSDKCls = true;
				}
				//else if (!sClass.getName().contains(packName)) {
				else {	
					isULCls = true;
				}
			}
			
			/*
			if (!sClass.isApplicationClass()) {
				continue;
			}
			*/
			
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
		int all_dataonly = 0, all_extraonly = 0, all_both = 0;
		for (ICCIntent itn : coveredInICCs) {
			if (itn.isExplicit()) {
				if (itn.isExternal()) ext_ex_inc ++;
				else int_ex_inc ++;
			}
			else {
				if (itn.isExternal()) ext_im_inc ++;
				else int_im_inc ++;
			}
			if (itn.hasData() && !itn.hasExtras()) all_dataonly++;
			if (itn.hasExtras() && !itn.hasData()) all_extraonly++;
			if (itn.hasExtras() && itn.hasData()) all_both++;
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
			if (itn.hasData() && !itn.hasExtras()) all_dataonly++;
			if (itn.hasExtras() && !itn.hasData()) all_extraonly++;
			if (itn.hasExtras() && itn.hasData()) all_both++;
		}
		//os.println("[ALL]");
		//os.println("int_ex_inc\t int_ex_out\t int_im_inc\t int_im_out\t ext_ex_inc\t ext_ex_out\t ext_im_inc\t ext_im_out");
		if (opts.debugOut) {
			os.println("*** tabulation ***");
			os.print("format: s_all\t d_all\t d_allInCalls\t s_in\t s_out\t d_in\t d_out\t d_alldata\t d_allextra\t d_allboth\t ");
			os.println("int_ex_inc\t int_ex_out\t int_im_inc\t int_im_out\t ext_ex_inc\t ext_ex_out\t ext_im_inc\t ext_im_out");
		}
		os.print(meCov.getTotal() + "\t" + meCov.getCovered() + "\t" + allMethodInCalls + "\t" +
				inIccCov.getTotal() + "\t " + outIccCov.getTotal() + "\t " + 
				inIccCov.getCovered() + "\t " + outIccCov.getCovered() + "\t" + 
				all_dataonly + "\t" + all_extraonly + "\t" + all_both + "\t");
		os.println(int_ex_inc+ "\t " + int_ex_out+ "\t " + int_im_inc+ "\t " + int_im_out+ "\t " + ext_ex_inc+ "\t " 
				+ ext_ex_out+ "\t " + ext_im_inc+ "\t " + ext_im_out);
	}

	public void reportICCWithData(PrintStream os) {
		//// for ICC carrying data only
		//int_ex_inc=0; int_ex_out=0; int_im_inc=0; int_im_out=0; ext_ex_inc=0; ext_ex_out=0; ext_im_inc=0; ext_im_out=0;
		int int_ex_inc=0, int_ex_out=0, int_im_inc=0, int_im_out=0, ext_ex_inc=0, ext_ex_out=0, ext_im_inc=0, ext_im_out=0;
		for (ICCIntent itn : coveredInICCs) {
			// count those that have data only (without extraData)
			if (!itn.hasData() || itn.hasExtras()) continue;
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
			// count those that have data only (without extraData)
			if (!itn.hasData() || itn.hasExtras()) continue;
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
			// count those that have extraData only (without 'standard' data)
			if (!itn.hasExtras() || itn.hasData()) continue;
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
			// count those that have extraData only (without 'standard' data)
			if (!itn.hasExtras() || itn.hasData()) continue;
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
	
	public void reportICCHasDataAndExtras(PrintStream os) {
		//// for ICC carrying both data and extraData 
		// int_ex_inc=0; int_ex_out=0; int_im_inc=0; int_im_out=0; ext_ex_inc=0; ext_ex_out=0; ext_im_inc=0; ext_im_out=0;
		int int_ex_inc=0, int_ex_out=0, int_im_inc=0, int_im_out=0, ext_ex_inc=0, ext_ex_out=0, ext_im_inc=0, ext_im_out=0;
		for (ICCIntent itn : coveredInICCs) {
			if (!itn.hasExtras() || !itn.hasData()) continue;
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
			if (!itn.hasExtras() || !itn.hasData()) continue;
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
			os.println("[hasDataAndExtras]");
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
			os.println("totally " + cntLinks + " ICC pairs found!");
			os.print("format: srcICC_component\t tgtICC_component");
		}
		
		for (Map.Entry<ICCIntent, ICCIntent> link : ICCPairs.entrySet()) {
			String incls = null, outcls = null;
			if (link.getKey().getCallsite()!=null) {
				incls = link.getKey().getCallsite().getSource().getSootClassName();
			}
			if (link.getValue().getCallsite()!=null) {
				outcls = link.getValue().getCallsite().getSource().getSootClassName();
			}
			
			if (incls==null || outcls==null) continue;
			if (!cls2comtype.containsKey(incls) || !cls2comtype.containsKey(outcls)) continue;

			os.println(cls2comtype.get(outcls) +"->"+cls2comtype.get(incls));
		}
	}
	
	// report how many external ICCs occurred actually connecting the two given apps
	public void reportInterAppPairs(PrintStream os) {
		int lex1 = 0, lim1 = 0, lex2 = 0, lim2 = 0; // break down explicit vs implicit ICC links
		for (Set<ICCIntent> itnpair : stater.getInterAppICCs()) {
			Iterator<ICCIntent> iter = itnpair.iterator();
			ICCIntent outicc = iter.next();
			ICCIntent inicc = iter.next();
			String senderCls = outicc.getCallsite().getSource().getSootClassName();
			//String recverCls = inicc.getCallsite().getSource().getSootClassName();
			if ((senderCls.contains(packName) || traceStat.isInList(senderCls, clsNames))) 
			{
				if (outicc.isExplicit()) lex1 ++; else lim1 ++;
				if (inicc.isExplicit()) lex1 ++; else lim1 ++;
			}
			else {
				if (outicc.isExplicit()) lex2 ++; else lim2 ++;
				if (inicc.isExplicit()) lex2 ++; else lim2 ++;
			}
		}
		int ex1 = 0, im1 = 0, ex2 = 0, im2 = 0; // break down explicit vs implicit ICCs 
		for (ICCIntent itn : coveredInICCs) {
			if (!itn.isExternal()) continue;
			if (itn.getCallsite()!=null) {
				String recvCls = itn.getCallsite().getSource().getSootClassName();
				if (recvCls.contains(packNameOther)||traceStat.isInList(recvCls, clsNamesOther)) {
					if (itn.isExplicit()) ex1++; else im1++;
				}
				if (recvCls.contains(packName)||traceStat.isInList(recvCls, clsNames)) {
					if (itn.isExplicit()) ex2++; else im2++;
				}
			}
		}
		for (ICCIntent itn : coveredOutICCs) {
			if (!itn.isExternal()) continue;
			if (itn.getCallsite()!=null) {
				String senderCls = itn.getCallsite().getSource().getSootClassName();
				if (senderCls.contains(packNameOther)||traceStat.isInList(senderCls, clsNamesOther)) {
					if (itn.isExplicit()) ex2++; else im2++;
				}
				if (senderCls.contains(packName)||traceStat.isInList(senderCls, clsNames)) {
					if (itn.isExplicit()) ex1++; else im1++;
				}
			}
		}
		if (opts.debugOut) {
			os.println("*** tabulation ***");
			os.print("format: srcAPK\t tgtAPK\t exicc\t imicc\t exlink\t imlink");
		}
		os.println(packName + "\t" + packNameOther + "\t" + ex1 + "\t" + im1 + "\t" + lex1 + "\t" + lim1);
		os.println(packNameOther + "\t" + packName + "\t" + ex2 + "\t" + im2 + "\t" + lex2 + "\t" + lim2);
	}
}

/* vim :set ts=4 tw=4 tws=4 */

