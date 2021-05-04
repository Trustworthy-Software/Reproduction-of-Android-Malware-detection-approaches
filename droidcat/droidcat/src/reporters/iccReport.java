/**
 * File: src/reporter/iccReport.java
 * -------------------------------------------------------------------------------------------
 * Date			Author      Changes
 * -------------------------------------------------------------------------------------------
 * 01/12/16		hcai		created; for computing ICC related statistics in android app call traces
 * 01/14/16		hcai		done the first version : mainly dynamic ICC statics
 * 01/28/16		hcai		added separate file outputs for different metrics; added metrics on icc links
 * 02/02/16		hcai		added intent filter parsing; 
 * 							divided icc metrics into three subcategories: having standard data only, having extras only, 
 * 							and having both data and extras
 * 02/04/16		hcai		added one more data into over icc metric report to facilitate post-processing and tabulation
 * 02/05/16		hcai		fixed the bug in ICC classification and result reporting
 * 02/19/16		hcai		added statistics on ICC coverage
 * 05/14/16		hcai		added feature collection for ML classification
*/
package reporters;

import iacUtil.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.FileWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.nio.file.Path;
import java.nio.file.Paths;

import dua.Extension;
import dua.Forensics;
import dua.global.ProgramFlowGraph;
import dua.method.CFG;
import dua.method.CFG.CFGNode;
import dua.method.CallSite;
import dua.util.Util;

import soot.*;
import soot.jimple.*;
import soot.jimple.infoflow.android.axml.AXmlNode;
import soot.toolkits.graph.Block;
import soot.toolkits.graph.BlockGraph;
import soot.toolkits.graph.ExceptionalBlockGraph;
import soot.util.*;

import dynCG.*;
import dynCG.callGraph.CGNode;
import dynCG.traceStat.ICCIntent;


public class iccReport implements Extension {
	
	protected static reportOpts opts = new reportOpts();
	protected final traceStat stater = new traceStat();
	
	// gross ICC coverage statistics
	protected final covStat inIccCov = new covStat("Incoming ICC Coverage");
	protected final covStat outIccCov = new covStat("Outgoing ICC Coverage");
	
	protected final covStat meCov = new covStat ("method coverage");
	protected int allMethodInCalls = 0;
	
	String packName = "";
	
	Set<ICCIntent> coveredInICCs = new HashSet<ICCIntent>();
	Set<ICCIntent> coveredOutICCs = new HashSet<ICCIntent>();
	
	protected final Set<String> allCoveredMethods = new HashSet<String>();
	
	public static void main(String args[]){
		args = preProcessArgs(opts, args);
		
		if (opts.traceFile==null || opts.traceFile.isEmpty()) {
			// nothing to do
			return;
		}

		iccReport grep = new iccReport();
		// examine catch blocks
		dua.Options.ignoreCatchBlocks = false;
		dua.Options.skipDUAAnalysis = true;
		dua.Options.modelAndroidLC = false;
		dua.Options.analyzeAndroid = true;
		
		soot.options.Options.v().set_src_prec(soot.options.Options.src_prec_apk);
		
		//output as APK, too//-f J
		soot.options.Options.v().set_output_format(soot.options.Options.output_format_dex);
		soot.options.Options.v().set_force_overwrite(true);
		Scene.v().addBasicClass("com.ironsource.mobilcore.BaseFlowBasedAdUnit",SootClass.SIGNATURES);
		
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
	
	static final Set<IntentFilter> intentfilters = new HashSet<IntentFilter>();
	private void parseComponentIntentfilters(String compType, List<AXmlNode> nodelist) {
		for (AXmlNode node : nodelist) {
			boolean exported = false;
			if (node.hasAttribute("android:exported")) {
				exported = node.getAttribute("android:exported").getValue().toString().equals("true");
			}
			else {
				exported = node.getChildrenWithTag("intent-filter").size()>0; 
			}
			
			if (!exported) {
				// if this component is closed, no point to look further at the intent filter at all
				continue;
			}
			
			String attval = node.getAttribute( "android:name" ).getValue().toString();
			String componentName = null;
			String packageName = ProgramFlowGraph.appPackageName;
			if (attval.startsWith(".") ) {                                                        
				componentName = packageName + attval;
			}
			else if ( ! attval.contains(packageName) && ! attval.startsWith(".")  && ! attval.contains(".")){
				componentName = packageName + "." + attval;
			}  
			else {
				componentName = attval;
			}
			for (AXmlNode child : node.getChildrenWithTag("intent-filter")) {
				IntentFilter itf = new IntentFilter();
				itf.setType(compType);
				itf.setComponentName(componentName);
				
				for (AXmlNode cact : child.getChildrenWithTag("action")) {
					if (cact.hasAttribute("android:name")) {
						itf.addAction(cact.getAttribute("android:name").getValue().toString());
					}
				}
				for (AXmlNode cact : child.getChildrenWithTag("category")) {
					if (cact.hasAttribute("android:name")) {
						itf.addCategory(cact.getAttribute("android:name").getValue().toString());
					}
				}
				for (AXmlNode cd: child.getChildrenWithTag("data")) {
						IntentData InD=new IntentData();
						InD.setmimeType(cd.getAttribute("android:mimeType").getValue().toString()+
								cd.getAttribute("android:type").getValue());
						InD.setscheme(cd.getAttribute("android:scheme").getValue().toString());
						InD.sethost(cd.getAttribute("android:host").getValue().toString());
						InD.setpath(cd.getAttribute("android:path").getValue().toString());
						InD.setpathPattern(cd.getAttribute("android:pathPattern").getValue().toString());
						InD.setpathPrefix(cd.getAttribute("android:pathPrefix").getValue().toString());
						InD.setport(cd.getAttribute("android:port").getValue().toString());
						itf.addData(InD);
				}
				
				intentfilters.add(itf);
			}
		}
	}
	
	protected void getIntentfilters() {
		parseComponentIntentfilters("Activity", ProgramFlowGraph.processMan.getActivities());
		parseComponentIntentfilters("Receiver", ProgramFlowGraph.processMan.getReceivers());
		parseComponentIntentfilters("Service", ProgramFlowGraph.processMan.getServices());
	}
	
	/**
	 * Descendants may want to use customized event monitors
	 */
	protected void init() {
		packName = ProgramFlowGraph.appPackageName;
		
		// set up the trace stating agent
		stater.setPackagename(packName);
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
			String fngdistfeature = dir + File.separator + "iccfeatures.txt";
			PrintStream psgdistfeature = new PrintStream (new FileOutputStream(fngdistfeature,true));
			collectFeatures(psgdistfeature);
			if (opts.featuresOnly) {
				System.exit(0);
			}
			
			if (opts.debugOut) {
				reportICC(System.out);
				reportICCWithData(System.out);
				reportICCHasExtras(System.out);
				reportICCHasDataAndExtras(System.out);
				reportICCLinks(System.out);
				ICCCoverage(System.out);
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

				String fnicccov = dir + File.separator + "icccov.txt";
				PrintStream psicccov = new PrintStream (new FileOutputStream(fnicccov,true));
				ICCCoverage(psicccov);
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
		int all_methods=0;
		int count_exceptions=0;
		/* traverse all classes */
		Iterator<SootClass> clsIt = Scene.v().getClasses().snapshotIterator(); //ProgramFlowGraph.inst().getAppClasses().iterator();
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
				all_methods++;
				String meId = sMethod.getSignature();
				
				meCov.incTotal();
				if (allCoveredMethods.contains(meId)) {
					meCov.incCovered();
				}
				//System.out.println("nadia"+meId);
                                try {
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
			
                        	}
				catch (Exception ex){
					count_exceptions++;
					System.out.println("nadiaException "+count_exceptions);
				}        
			} // -- while (meIt.hasNext()) 
			
		} // -- while (clsIt.hasNext())
	if (count_exceptions != 0) {
		String dir = System.getProperty("user.dir");
		try { 
			BufferedWriter writer = new BufferedWriter(new FileWriter(dir + File.separator + "exceptions_icc.txt", true));
			writer.write(packName+" "+count_exceptions+" out of "+all_methods+" "+percentage(count_exceptions, all_methods));
       			writer.newLine();
			writer.close();
		}
		catch(IOException e) {
			e.printStackTrace();
		}

	}
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
			SootClass incls = null, outcls = null;
			if (link.getKey().getCallsite()!=null) {
				incls = Scene.v().getSootClass(link.getKey().getCallsite().getSource().getSootClassName());
			}
			if (link.getValue().getCallsite()!=null) {
				outcls = Scene.v().getSootClass(link.getValue().getCallsite().getSource().getSootClassName());
			}
			
			if (incls==null || outcls==null) continue;
			
			os.println(iccAPICom.getComponentType(outcls) +"->"+iccAPICom.getComponentType(incls));
		}
	}
	
	static String percentage(int a, int b) {
		DecimalFormat df = new DecimalFormat("#.####");
		if (b==0) return df.format(0); 
		return df.format(a*1.0/b);
	}
	
	public void ICCCoverage(PrintStream os) {
		int inICCCovered = 0, outICCCovered = 0;
		
		for (SootMethod sm : traversedInICCs.keySet()) {
			for (Stmt st : traversedInICCs.get(sm)) {
				InvokeExpr inv = st.getInvokeExpr();
				String calleename = inv.getMethod().getName();
				for (ICCIntent iit : coveredInICCs) {
					if (iit.getCallsite()!=null) {
						if (iit.getCallsite().getSource().getSootMethodName().contains(sm.getName()) && 
							iit.getCallsite().getTarget().getSootMethodName().contains(calleename)) {
							inICCCovered ++;
							break;
						}
					}
				}
			}
		}

		for (SootMethod sm : traversedOutICCs.keySet()) {
			for (Stmt st : traversedOutICCs.get(sm)) {
				InvokeExpr inv = st.getInvokeExpr();
				String calleename = inv.getMethod().getName();
				for (ICCIntent iit : coveredOutICCs) {
					if (iit.getCallsite()!=null) {
						if (iit.getCallsite().getSource().getSootMethodName().contains(sm.getName()) && 
							iit.getCallsite().getTarget().getSootMethodName().contains(calleename)) {
							outICCCovered ++;
							break;
						}
					}
				}
			}
		}
		
		if (opts.debugOut) {
			os.println("*** tabulation ***");
			os.print("format: inICC-coverage\t outICC-coverage\t allICC-coverage");
		}
		
		os.println(percentage(inICCCovered, inIccCov.getTotal()) + "\t" + percentage(outICCCovered, outIccCov.getTotal())
				+ "\t" + percentage(inICCCovered+outICCCovered, inIccCov.getTotal()+outIccCov.getTotal()));
	}
	
	/** collect metrics to be used as features in the ML classification model */
	public void collectFeatures(PrintStream os) {
		if (opts.debugOut) {
			os.println("*** ICC feature collection *** ");
			os.print("format: packagename"+"\t");
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
		//os.println("int_ex_inc\t int_ex_out\t int_im_inc\t int_im_out\t ext_ex_inc\t ext_ex_out\t ext_im_inc\t ext_im_out");
		if (opts.debugOut) {
			os.println("int_ex" + "\t" + "int_im" + "\t" + "ext_ex" + "\t" + "ext_im" + "\t" + 
						"data_only" + "\t" + "extras_only" + "\t" + "data_both");
		}
		// 1. ICC categorization
		Path p = Paths.get(soot.options.Options.v().process_dir().get(0));
		os.print(p.getFileName().toString());
		int iccTotal = int_ex_inc+int_ex_out+int_im_inc+int_im_out+ext_ex_inc+ext_ex_out+ext_im_inc+ext_im_out;
		os.print("\t" + percentage(int_ex_inc+int_ex_out, iccTotal) +
				 "\t" + percentage(int_im_inc+int_im_out, iccTotal) +
				 "\t" + percentage(ext_ex_inc+ext_ex_out, iccTotal) +
				 "\t" + percentage(ext_im_inc+ext_im_out, iccTotal));
		
		// 2. data-carrying ICC
		os.println("\t" + percentage(all_dataonly, iccTotal) +
				   "\t" + percentage(all_extraonly, iccTotal) + 
				   "\t" + percentage(all_both, iccTotal));
	}
}

/* vim :set ts=4 tw=4 tws=4 */

