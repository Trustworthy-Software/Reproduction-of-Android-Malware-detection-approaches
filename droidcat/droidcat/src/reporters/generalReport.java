/**
 * File: src/reporter/generalReport.java
 * -------------------------------------------------------------------------------------------
 * Date			Author      Changes
 * -------------------------------------------------------------------------------------------
 * 01/06/16		hcai		created; for computing basic android app code characteristics with 
 *                          respect to call traces gathered from executions
 * 01/07/16		hcai		added coverage statistics           
 * 01/09/16		hcai		first working version of basic (coverage related) statistics
 * 01/15/16		hcai		added class ranking by edge frequency and call in/out degrees; added component type distribution statistics              
 * 01/19/16		hcai		separate different statistics outputs by streaming them to separate files
 * 01/26/16		hcai		added total instances of being called for gdistcov and compDist metrics
 * 01/28/16		hcai		added reports on caller/callee ranking by total outgoing/incoming call instances
*/
package reporters;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.file.Path;
import java.nio.file.Paths;

import dua.Extension;
import dua.Forensics;
import dua.global.ProgramFlowGraph;

import soot.*;

import dynCG.*;
import dynCG.callGraph.CGEdge;
import dynCG.callGraph.CGNode;
import iacUtil.iccAPICom;

public class generalReport implements Extension {
	
	protected static reportOpts opts = new reportOpts();
	protected final traceStat stater = new traceStat();
	
	protected final Set<String> allCoveredClasses = new HashSet<String>();
	protected final Set<String> allCoveredMethods = new HashSet<String>();
	
	public final static String AndroidClassPattern = "(android|com\\.example\\.android|com\\.google|com\\.android|dalvik)\\.(.)+"; 
	public final static String OtherSDKClassPattern = "(gov\\.nist|java|javax|junit|libcore|net\\.oauth|org\\.apache|org\\.ccil|org\\.javia|" +
			"org\\.jivesoftware|org\\.json|org\\.w3c|org\\.xml|sun|com\\.adobe|com\\.svox|jp\\.co\\.omronsoft|org\\.kxml2|org\\.xmlpull)\\.(.)+";

	// application code coverage statistics
	protected final covStat appClsCov = new covStat("Application Class");
	protected final covStat appMethodCov = new covStat("Application Method");
	// user/third-party library code coverage  
	protected final covStat ulClsCov = new covStat("Library Class");
	protected final covStat ulMethodCov = new covStat("Library Method");
	// framework library (Android SDK) code coverage  
	protected final covStat sdkClsCov = new covStat("SDK Class");
	protected final covStat sdkMethodCov = new covStat("SDK Method");
	
	// count all instances per category of code
	int[] insClsAll = new int[] {0,0,0}; // {userCode, 3rdpartyLib, SDK}
	int[] insMethodAll = new int[] {0,0,0};
	
	String packName = "";

	public static void main(String args[]){
		args = preProcessArgs(opts, args);
		
		if (opts.traceFile==null || opts.traceFile.isEmpty()) {
			// nothing to do
			return;
		}

		generalReport grep = new generalReport();
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
	
	/**
	 * Descendants may want to use customized event monitors
	 */
	/** mapping from component type to component classes */
	Map<String, Set<String>> sct2cc = new HashMap<String, Set<String>>();
	Map<String, Set<String>> dct2cc = new HashMap<String, Set<String>>();
	Map<String, Integer> dct2ins = new HashMap<String, Integer>();
	
	protected void init() {
		packName = ProgramFlowGraph.appPackageName;
		
		for (String ctn : iccAPICom.component_type_names) {
			sct2cc.put(ctn, new HashSet<String>());
			dct2cc.put(ctn, new HashSet<String>());
			dct2ins.put(ctn, 0);
		}
		
		// set up the trace stating agent
		stater.setPackagename(packName);
		stater.setTracefile(opts.traceFile);
		
		// parse the trace
		stater.stat();
		
		Set<CGNode> allCGNodes = stater.getCG().getInternalGraph().vertexSet();
		for (CGNode n : allCGNodes) {
			allCoveredClasses.add(n.getSootClassName());
			allCoveredMethods.add(n.getSootMethodName());
		}
	}
	
	public void run() {
		System.out.println("Running static analysis for method/class coverage characterization");
		//System.out.println(Scene.v().getPkgList());
		
		init();
		
		traverse();
		
		Set<CGNode> allCGNodes = stater.getCG().getInternalGraph().vertexSet();
		for (CGNode n : allCGNodes) {
			String cls = n.getSootClassName();
			String me = n.getSootMethodName();
			if (coveredAppClasses.contains(cls)) {
				insClsAll[0] += stater.getCG().getTotalInCalls(me);
			}
			if (coveredULClasses.contains(cls)) {
				insClsAll[1] += stater.getCG().getTotalInCalls(me);
			}
			if (coveredSDKClasses.contains(cls)) {
				insClsAll[2] += stater.getCG().getTotalInCalls(me);
			}

			if (coveredAppMethods.contains(me)) {
				insMethodAll[0] += stater.getCG().getTotalInCalls(me);
			}
			if (coveredULMethods.contains(me)) {
				insMethodAll[1] += stater.getCG().getTotalInCalls(me);
			}
			if (coveredSDKMethods.contains(me)) {
				insMethodAll[2] += stater.getCG().getTotalInCalls(me);
			}
		}
		
		for (CGNode n : allCGNodes) {
			String cls = n.getSootClassName();
			for (String ct : dct2cc.keySet()) {
				if (dct2cc.get(ct).contains(cls)) {
					Integer cct = dct2ins.get(ct);
					if (cct==null) cct = 0;
					cct += stater.getCG().getTotalInCalls(n.getSootMethodName());
					dct2ins.put(ct, cct);
				}
			}
		}
		
		String dir = System.getProperty("user.dir");
		

		try {
			String fngdistfeature = dir + File.separator + "gfeatures.txt";
			PrintStream psgdistfeature = new PrintStream (new FileOutputStream(fngdistfeature,true));
			collectFeatures(psgdistfeature);
			if (opts.featuresOnly) {
				System.exit(0);
			}

			if (opts.debugOut) {
				report (System.out);
				reportIns (System.out);
				rankingEdgeFreqByClass (System.out);
				rankingCallerByClass (System.out);
				rankingCalleeByClass (System.out);
				rankingCallerInsByClass (System.out);
				rankingCalleeInsByClass (System.out);
				componentTypeDist(System.out);
			}
			else {
				String fngdistcov = dir + File.separator + "gdistcov.txt";
				PrintStream psgdistcov = new PrintStream (new FileOutputStream(fngdistcov,true));
				report(psgdistcov);

				String fngdistcovIns = dir + File.separator + "gdistcovIns.txt";
				PrintStream psgdistcovIns = new PrintStream (new FileOutputStream(fngdistcovIns,true));
				reportIns(psgdistcovIns);

				String fnedgefreq = dir + File.separator + "edgefreq.txt";
				PrintStream psedgefreq = new PrintStream (new FileOutputStream(fnedgefreq,true));
				rankingEdgeFreqByClass(psedgefreq);
				
				String fncallerrank = dir + File.separator + "callerrank.txt";
				PrintStream pscallerrank = new PrintStream (new FileOutputStream(fncallerrank,true));
				rankingCallerByClass(pscallerrank);

				String fncalleerank = dir + File.separator + "calleerank.txt";
				PrintStream pscalleerank = new PrintStream (new FileOutputStream(fncalleerank,true));
				rankingCalleeByClass(pscalleerank);

				String fncallerInsrank = dir + File.separator + "callerrankIns.txt";
				PrintStream pscallerInsrank = new PrintStream (new FileOutputStream(fncallerInsrank,true));
				rankingCallerInsByClass(pscallerInsrank);

				String fncalleeInsrank = dir + File.separator + "calleerankIns.txt";
				PrintStream pscalleeInsrank = new PrintStream (new FileOutputStream(fncalleeInsrank,true));
				rankingCalleeInsByClass(pscalleeInsrank);
				
				String fncompdist = dir + File.separator + "compdist.txt";
				PrintStream pscompdist = new PrintStream (new FileOutputStream(fncompdist,true));
				componentTypeDist(pscompdist);
			}
		}
		catch (Exception e) {e.printStackTrace();}
			
		System.exit(0);
	}

	Set<String> traversedClasses = new HashSet<String>();
	Set<String> traversedMethods = new HashSet<String>();
	
	Set<String> coveredAppClasses = new HashSet<String>();
	Set<String> coveredULClasses = new HashSet<String>();
	Set<String> coveredSDKClasses = new HashSet<String>();

	Set<String> coveredAppMethods = new HashSet<String>();
	Set<String> coveredULMethods = new HashSet<String>();
	Set<String> coveredSDKMethods = new HashSet<String>();
	
	public void traverse() {
		/* traverse all classes */
		Iterator<SootClass> clsIt = Scene.v().getClasses().snapshotIterator();//.iterator(); //ProgramFlowGraph.inst().getAppClasses().iterator();
		while (clsIt.hasNext()) {
			SootClass sClass = (SootClass) clsIt.next();
			if ( sClass.isPhantom() ) {	continue; }
			boolean isAppCls = false, isSDKCls = false, isULCls = false;
			//if ( sClass.isApplicationClass() ) {
			if (sClass.getName().contains(packName)) {	
				appClsCov.incTotal();
				if (allCoveredClasses.contains(sClass.getName())) {
					appClsCov.incCovered();
				}
				isAppCls = true;
				coveredAppClasses.add(sClass.getName());
			}
			else {
				// differentiate user library from SDK library
				if (sClass.getName().matches(AndroidClassPattern) || sClass.getName().matches(OtherSDKClassPattern)) {
					sdkClsCov.incTotal();
					if (allCoveredClasses.contains(sClass.getName())) {
						sdkClsCov.incCovered();
					}
					isSDKCls = true;
					coveredSDKClasses.add(sClass.getName());
				}
				//else if (!sClass.getName().contains(packName)) {
				else {	
					ulClsCov.incTotal();
					if (allCoveredClasses.contains(sClass.getName())) {
						ulClsCov.incCovered();
					}
					isULCls = true;
					coveredULClasses.add(sClass.getName());
				}
			}
			traversedClasses.add(sClass.getName());
			
			String ctn = iccAPICom.getComponentType(sClass);
			if (ctn.compareTo("Unknown")!=0) {
				sct2cc.get(ctn).add( sClass.getName() );
				if (allCoveredClasses.contains(sClass.getName())) {
					dct2cc.get( ctn ).add( sClass.getName() );	
				}
			}
			
			/* traverse all methods of the class */
			Iterator<SootMethod> meIt = sClass.getMethods().iterator();
			while (meIt.hasNext()) {
				SootMethod sMethod = (SootMethod) meIt.next();
				if ( !sMethod.isConcrete() ) {
					// skip abstract methods and phantom methods, and native methods as well
					//continue; 
				}
				String meId = sMethod.getSignature();
				
				if (isAppCls) {
					appMethodCov.incTotal();
					if (allCoveredMethods.contains(meId)) {
						appMethodCov.incCovered();
						coveredAppMethods.add(meId);
					}
				}
				else if (isSDKCls ){
					sdkMethodCov.incTotal();
					if (allCoveredMethods.contains(meId)) {
						sdkMethodCov.incCovered();
						coveredSDKMethods.add(meId);
					}
				}
				else {
					assert isULCls;
					ulMethodCov.incTotal();
					if (allCoveredMethods.contains(meId)) {
						ulMethodCov.incCovered();
						coveredULMethods.add(meId);
					}
				}
				
				traversedMethods.add(meId);
				
			} // -- while (meIt.hasNext()) 
			
		} // -- while (clsIt.hasNext())
	}
	
	public void report(PrintStream os) {
		/** report statistics for the current trace */
		if (opts.debugOut) {
			os.println(appClsCov);
			os.println(appMethodCov);
			os.println(ulClsCov);
			os.println(ulMethodCov);
			os.println(sdkClsCov);
			os.println(sdkMethodCov);
		}
		
		int sclsTotal = appClsCov.getTotal()+ulClsCov.getTotal()+sdkClsCov.getTotal();
		if (opts.debugOut) {
			os.println();
			os.println("Total classes: " +  sclsTotal);
			os.print("distribution: application user-lib sdk ");
			os.println(appClsCov.getTotal()*1.0/sclsTotal + " " + ulClsCov.getTotal()*1.0/sclsTotal + " " + sdkClsCov.getTotal()*1.0/sclsTotal);
		}
		
		int dclsTotal = (appClsCov.getCovered()+ulClsCov.getCovered()+sdkClsCov.getCovered());
		if (opts.debugOut) {
			os.println("Covered classes: " +  dclsTotal);
			os.print("distribution: application user-lib sdk ");
			os.println(appClsCov.getCovered()*1.0/dclsTotal + " " + ulClsCov.getCovered()*1.0/dclsTotal + " " + sdkClsCov.getCovered()*1.0/dclsTotal);
			os.println("Covered classes seen in the dynamic callgraph: " + allCoveredClasses.size() );
		}
		
		int smeTotal = (appMethodCov.getTotal()+ulMethodCov.getTotal()+sdkMethodCov.getTotal());
		if (opts.debugOut) {
			os.println();
			os.println("Total methods: " + smeTotal);
			os.print("distribution: application user-lib sdk ");
			os.println(appMethodCov.getTotal()*1.0/smeTotal + " " + ulMethodCov.getTotal()*1.0/smeTotal + " " + sdkMethodCov.getTotal()*1.0/smeTotal);
		}
		
		int dmeTotal = (appMethodCov.getCovered()+ulMethodCov.getCovered()+sdkMethodCov.getCovered());
		if (opts.debugOut) {
			os.println("Covered methods: " +  dmeTotal);
			os.print("distribution: application user-lib sdk ");
			os.println(appMethodCov.getCovered()*1.0/dmeTotal + " " + ulMethodCov.getCovered()*1.0/dmeTotal + " " + sdkMethodCov.getCovered()*1.0/dmeTotal);
			os.println("Covered methods seen in the dynamic callgraph: " + allCoveredMethods.size() );
			
			os.println();
			allCoveredClasses.removeAll(traversedClasses);
			os.println("covered classes not found during traversal: " + allCoveredClasses);
			allCoveredMethods.removeAll(traversedMethods);
			os.println("covered methods not found during traversal: " + allCoveredMethods);
		}
		
		if (opts.debugOut) {
			os.println("*** tabulation *** ");
			os.println("format: class_app\t class_ul\t class_sdk\t class_all\t method_app\t method_ul\t method_sdk\t method_all");
		}
		DecimalFormat df = new DecimalFormat("#.####");
		
		if (opts.debugOut) {
			os.println("[static]");
		}
		os.println(appClsCov.getTotal() + "\t" + ulClsCov.getTotal() + "\t" + sdkClsCov.getTotal() + "\t" + sclsTotal + "\t" + 
						   appMethodCov.getTotal() + "\t" + ulMethodCov.getTotal() + "\t" + sdkMethodCov.getTotal() + "\t" + smeTotal);
		
		if (opts.debugOut) {
			os.println("[dynamic]");
		}
		os.println(appClsCov.getCovered() + "\t" + ulClsCov.getCovered() + "\t" + sdkClsCov.getCovered() + "\t" + dclsTotal + "\t" +
				   appMethodCov.getCovered() + "\t" + ulMethodCov.getCovered() + "\t" + sdkMethodCov.getCovered() + "\t" + dmeTotal);
		
		if (opts.debugOut) {
			os.println("[dynamic/static ratio]");
		}
		os.println(df.format(appClsCov.getCoverage()) + "\t" + df.format(ulClsCov.getCoverage()) + "\t" + df.format(sdkClsCov.getCoverage()) + "\t" + 
				df.format(1.0*dclsTotal/sclsTotal) + "\t" + 
				df.format(appMethodCov.getCoverage()) + "\t" + df.format(ulMethodCov.getCoverage()) + "\t" + df.format(sdkMethodCov.getCoverage()) + "\t" + 
				df.format(1.0*dmeTotal/smeTotal));
	}
	
	public void reportIns(PrintStream os) {
		if (opts.debugOut) {
			os.println("*** total instances of being called *** ");
			os.println("format: class_app\t class_ul\t class_sdk\t class_all\t method_app\t method_ul\t method_sdk\t method_all");
		}
		os.println(insClsAll[0] + "\t" + insClsAll[1] + "\t" + insClsAll[2] + "\t" + (insClsAll[0]+insClsAll[1]+insClsAll[2]) + 
				"\t"+ 
				insMethodAll[0] + "\t" + insMethodAll[1] + "\t" + insMethodAll[2] + "\t" + 
				(insMethodAll[0]+insMethodAll[1]+insMethodAll[2]) ); 
	}
	
	/** rank covered classes/methods by call frequency and out/in degrees */
	private String getCategory(String nameCls) {
		if (coveredAppClasses.contains(nameCls)) return "UserCode";
		if (coveredULClasses.contains(nameCls)) return "3rdLib";
		if (coveredSDKClasses.contains(nameCls)) return "SDK";
		return "Unknown";
	}

	public void rankingEdgeFreqByClass(PrintStream os) {
		List<CGEdge> orderedEdges = stater.getCG().listEdgeByFrequency(false);
		
		if (opts.debugOut) {
			os.println("*** edge frequency ranking *** ");
			os.println("format: rank\t class_source\t class_tgt");
		}
		for (CGEdge e : orderedEdges) {
			os.println(e.getFrequency() + "\t " + getCategory(e.getSource().getSootClassName()) + "\t " + getCategory(e.getTarget().getSootClassName()));
		}
	}
	public void rankingCallerByClass(PrintStream os) {
		List<CGNode> orderedCallers = stater.getCG().listCallers(false);
		if (opts.debugOut) {
			os.println("*** caller (out-degree) ranking *** ");
			os.println("format: rank\t class");
			os.println("[caller]");
		}
		for (CGNode n : orderedCallers) {
			os.println(stater.getCG().getInternalGraph().outDegreeOf(n) + "\t" + getCategory(n.getSootClassName()));
		}
	}
	public void rankingCalleeByClass(PrintStream os) {
		List<CGNode> orderedCallees = stater.getCG().listCallees(false);
		
		if (opts.debugOut) {
			os.println("*** callee (in-degree) ranking *** ");
			os.println("format: rank\t class");
			os.println("[callee]");
		}
		for (CGNode n : orderedCallees) {
			os.println(stater.getCG().getInternalGraph().inDegreeOf(n) + "\t" + getCategory(n.getSootClassName()));
		}
	}

	public void rankingCallerInsByClass(PrintStream os) {
		List<CGNode> orderedCallers = stater.getCG().listCallerInstances(false);
		if (opts.debugOut) {
			os.println("*** caller ranking by outgoing call instances *** ");
			os.println("format: rank\t class");
			os.println("[caller]");
		}
		for (CGNode n : orderedCallers) {
			os.println(stater.getCG().getTotalOutCalls(n.getMethodName()) + "\t" + getCategory(n.getSootClassName()));
		}
	}
	public void rankingCalleeInsByClass(PrintStream os) {
		List<CGNode> orderedCallees = stater.getCG().listCalleeInstances(false);
		
		if (opts.debugOut) {
			os.println("*** callee ranking by incoming call instances *** ");
			os.println("format: rank\t class");
			os.println("[callee]");
		}
		for (CGNode n : orderedCallees) {
			os.println(stater.getCG().getTotalInCalls(n.getMethodName()) + "\t" + getCategory(n.getSootClassName()));
		}
	}
	
	/** distribution regarding the four component types */
	public void componentTypeDist(PrintStream os) {
		if (opts.debugOut) {
			os.println("*** component type distribution *** ");
			os.println("format: activity\t service\t broadcast_receiver\t content_provider\t application");
			os.println("[static]");
		}
		for (String ctn : iccAPICom.component_type_names) {
			os.print(sct2cc.get(ctn).size() + "\t ");
		}
		os.println();

		if (opts.debugOut) {
			os.println("[dynamic]");
		}
		for (String ctn : iccAPICom.component_type_names) {
			os.print(dct2cc.get(ctn).size() + "\t ");
		}
		os.println();

		if (opts.debugOut) {
			os.println("[call instances]");
		}
		for (String ctn : iccAPICom.component_type_names) {
			os.print( (dct2ins.containsKey(ctn)?dct2ins.get(ctn):0) + "\t ");
		}
		os.println();
	}
	static String percentage(int a, int b) {
		DecimalFormat df = new DecimalFormat("#.####");
		if (b==0) return df.format(0); 
		return df.format(a*1.0/b);
	}

	// gather metrics used as potential ML classification features 
	public void collectFeatures(PrintStream os) {
		if (opts.debugOut) {
			os.println("*** general feature collection *** ");
			os.print("format: packagename"+"\t");
		}
		Map<String, Integer> c2n = new HashMap<String, Integer>();
		String[] cats = {"SDK->SDK", "SDK->3rdLib", "SDK->UserCode", "3rdLib->SDK", "3rdLib->3rdLib", "3rdLib->UserCode",
				"UserCode->SDK", "UserCode->3rdLib", "UserCode->UserCode"};
		for (String cat:cats) {
			c2n.put(cat, 0);
		}
		int totaln = 0;
		for (CGEdge e : stater.getCG().getInternalGraph().edgeSet()) {
			String cat = getCategory(e.getSource().getSootClassName()).trim() + "->" + 
						getCategory(e.getTarget().getSootClassName()).trim();
			if (cat.contains("Unknown")) continue;
			Integer n = c2n.get(cat);
			assert n!=null;
			n += e.getFrequency();
			c2n.put(cat, n);
			totaln += e.getFrequency();
		}
		
		if (opts.debugOut) {
			for (String cat : cats) {
				os.print(cat + "\t");
			}
			os.println("userCode-cls"+"\t"+"3rdLib-cls"+"\t"+"sdk-cls"+"\t"+"userCode-me"+"\t"+"3rdlib-me"+"\t"+"sdk-me"+
			   "\t"+"userCode-clsIns"+"\t"+"3rdLib-clsIns"+"\t"+"sdk-clsIns"+"\t"+"userCode-meIns"+"\t"+"3rdlib-meIns"+ "\t"+"sdk-meIns"+
			   "\t"+"activity"+"\t"+"service"+"\t"+"receiver"+"\t"+"provider"+
			   "\t"+"activityIns"+"\t"+"serviceIns"+"\t"+"receiverIns"+"\t"+"providerIns");
		}
		// 1. inter-code-layer calls - all nine categories - percentage of instances
                Path p = Paths.get(soot.options.Options.v().process_dir().get(0));
		os.print(p.getFileName().toString());
		for (String cat : cats) {
			//if (!c2n.containsKey(cat)) if (!cat.contains("Unknown")) {System.out.println("weird cat=" + cat); assert false;}
			os.print("\t" + percentage(c2n.get(cat), totaln));
		}
		
		// 2. composition - percentage of each code layer (w.r.t call targets) - by call sites
		int dclsTotal = (appClsCov.getCovered()+ulClsCov.getCovered()+sdkClsCov.getCovered());
		int dmeTotal = (appMethodCov.getCovered()+ulMethodCov.getCovered()+sdkMethodCov.getCovered());
		os.print("\t" + percentage(appClsCov.getCovered(),dclsTotal) + "\t" + 
				   percentage(ulClsCov.getCovered(),dclsTotal) + "\t" + 
				   percentage(sdkClsCov.getCovered(),dclsTotal) + "\t" + 
				   percentage(appMethodCov.getCovered(),dmeTotal) + "\t" + 
				   percentage(ulMethodCov.getCovered(),dmeTotal) + "\t" + 
				   percentage(sdkMethodCov.getCovered(),dmeTotal));

		// 3. composition - percentage of each code layer (w.r.t call targets) - by instances
		int insClsTotal = insClsAll[0]+insClsAll[1]+insClsAll[2];
		int insMeTotal = insMethodAll[0]+insMethodAll[1]+insMethodAll[2];
		/* UserCode-cls, 3rdLib-cls, SDK-cls, UserCode-method, 3rdLib-method, SDK-method */
		os.print("\t" + percentage(insClsAll[0],insClsTotal) + "\t" + 
				   percentage(insClsAll[1],insClsTotal) + "\t" + 
				   percentage(insClsAll[2],insClsTotal) + "\t" + 
				   percentage(insMethodAll[0],insMeTotal) + "\t" + 
				   percentage(insMethodAll[1],insMeTotal) + "\t" + 
				   percentage(insMethodAll[2],insMeTotal));
		
		// 4. component distribution - percentage of each type - by unique classes
		int dctsum = 0;
		for (int i=0;i<4;i++) {
			String key = iccAPICom.component_type_names[i];
			dctsum += dct2cc.get(key).size();
		}
		for (int i=0;i<4;i++) {
			String key = iccAPICom.component_type_names[i];
			os.print("\t"+percentage(dct2cc.get(key).size(),dctsum));
		}

		// 5. component distribution - percentage of each type - by instances
		int dctinssum = 0;
		for (int i=0;i<4;i++) {
			String key = iccAPICom.component_type_names[i];
			dctinssum += dct2ins.get(key);
		}
		for (int i=0;i<4;i++) {
			String key = iccAPICom.component_type_names[i];
			os.print("\t"+percentage(dct2ins.get(key),dctinssum));
		}
		
		os.println();
	}
}  

/* vim :set ts=4 tw=4 tws=4 */

