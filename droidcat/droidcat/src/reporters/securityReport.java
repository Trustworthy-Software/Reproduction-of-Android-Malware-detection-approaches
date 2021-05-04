/**
 * File: src/reporter/securityReport.java
 * -------------------------------------------------------------------------------------------
 * Date			Author      Changes
 * -------------------------------------------------------------------------------------------
 * 01/15/16		hcai		created; reporting security related statistics
 * 01/18/16		hcai		done drafting the preliminary statistics (coverage centric only)
 * 01/23/16		hcai		added callback methods (separately for lifecycle methods and event handlers) statistics
 * 01/25/16		hcai		added the use of the exhaustive set of source/sink produced by SuSi; and categorized 
 * 							sources and sinks in the code and the traces
 * 01/26/16		hcai		added method-level taint flow (reachability from source / to sink) metrics
 * 01/27/16		hcai		added categorized statistics of lifecycle methods and event handlers, the latter based on 
 * 							a manually curated classification of the CallbackClasses.txt in Flowdroid;
 * 							also added statistics on instances of being called for all metrics
 * 05/09/16		hcai		fix the method-level taint flow reachability 
 * 05/13/16		hcai		applied the calltree component using it for detailed src/sink reachability computation
 * 05/15/16		hcai		added feature collection for ML classification
*/
package reporters;

import iacUtil.*;
import iacUtil.iccAPICom.EVENTCAT;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
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
import soot.jimple.infoflow.android.data.AndroidMethod;
import soot.jimple.infoflow.android.data.AndroidMethod.CATEGORY;
import soot.jimple.infoflow.android.data.parsers.CategorizedAndroidSourceSinkParser;
import soot.jimple.infoflow.android.data.parsers.PermissionMethodParser;
import soot.jimple.infoflow.android.source.data.ISourceSinkDefinitionProvider;
import soot.jimple.infoflow.android.source.data.SourceSinkDefinition;
import soot.jimple.infoflow.android.source.parsers.xml.XMLSourceSinkParser;
import soot.toolkits.graph.Block;
import soot.toolkits.graph.BlockGraph;
import soot.toolkits.graph.ExceptionalBlockGraph;
import soot.util.*;
import soot.jimple.infoflow.android.data.AndroidMethod.*;

import dynCG.*;
import dynCG.callGraph.CGNode;
import dynCG.traceStat.ICCIntent;


public class securityReport implements Extension {
	
	protected static reportOpts opts = new reportOpts();
	protected final traceStat stater = new traceStat();
	
	// gross ICC coverage statistics
	protected final covStat srcCov = new covStat("source coverage");
	protected final covStat sinkCov = new covStat("sink coverage");

	protected final covStat lifecycleCov = new covStat("lifecylce method coverage");
	protected final covStat eventhandlerCov = new covStat("event handler coverage");
	
	protected final Set<String> allCoveredClasses = new HashSet<String>();
	protected final Set<String> allCoveredMethods = new HashSet<String>();
	
	String packName = "";
	
	/** for uncategorized source/sink */
	Set<String> allSources = new HashSet<String>();
	Set<String> allSinks = new HashSet<String>();
	Set<String> traversedSinks = new HashSet<String>();
	Set<String> traversedSources = new HashSet<String>();
	Set<String> coveredSources = new HashSet<String>();
	Set<String> coveredSinks = new HashSet<String>();
	// total number of instances of src/sink being called
	int allSrcInCalls = 0;
	int allSinkInCalls = 0;
	
	int allMethodInCalls = 0;
	
	/** for categorized source/sink */
	Map<String, CATEGORY> allCatSrcs = new HashMap<String,CATEGORY>();
	Map<String, CATEGORY> allCatSinks = new HashMap<String,CATEGORY>();
	Map<CATEGORY, Set<String>> traversedCatSrcs = new HashMap<CATEGORY, Set<String>>();
	Map<CATEGORY, Set<String>> traversedCatSinks = new HashMap<CATEGORY, Set<String>>();
	Map<CATEGORY, Set<String>> coveredCatSrcs = new HashMap<CATEGORY, Set<String>>();
	Map<CATEGORY, Set<String>> coveredCatSinks = new HashMap<CATEGORY, Set<String>>();
	// total number of instances of src/sink being called
	Map<CATEGORY, Integer> allCatSrcInCalls = new HashMap<CATEGORY, Integer>();
	Map<CATEGORY, Integer> allCatSinkInCalls = new HashMap<CATEGORY, Integer>();
	
	/** for method-level taint flow */
	// total numbers of escaping sources and reachable sinks
	int allEscapeSrcs = 0;
	int allReachableSinks = 0;
	// per-category statistics 
	Map<CATEGORY, Integer> allEscapeCatSrcs = new HashMap<CATEGORY, Integer>();
	Map<CATEGORY, Integer> allReachableCatSinks = new HashMap<CATEGORY, Integer>();
	int allEscapeSrcInCalls = 0;
	int allReachableSinkInCalls = 0;
	// per-category statistics 
	Map<CATEGORY, Integer> allEscapeCatSrcInCalls = new HashMap<CATEGORY, Integer>();
	Map<CATEGORY, Integer> allReachableCatSinkInCalls = new HashMap<CATEGORY, Integer>();	

	/** for callbacks */
	Set<String> callbackClses = new HashSet<String>();
	Set<SootClass> callbackSootClses = new HashSet<SootClass>();
	
	Map<String,EVENTCAT> catCallbackClses = new HashMap<String,EVENTCAT>();
	
	// rougly, two types of callbacks: lifecycle methods and event handlers
	Set<String> traversedLifecycleMethods = new HashSet<String>();
	Set<String> traversedEventHandlerMethods = new HashSet<String>();
	Set<String> coveredLifecycleMethods = new HashSet<String>();
	Set<String> coveredEventHandlerMethods = new HashSet<String>();
	
	// more fine-grained classification of callbacks
	Map<EVENTCAT, Set<String>> traversedCatEventHandlerMethods = new HashMap<EVENTCAT, Set<String>>();
	Map<EVENTCAT, Set<String>> coveredCatEventHandlerMethods = new HashMap<EVENTCAT, Set<String>>();
	Map<String, Set<String>> traversedCatLifecycleMethods = new HashMap<String, Set<String>>();
	Map<String, Set<String>> coveredCatLifecycleMethods = new HashMap<String, Set<String>>();
	
	int allEHInCalls = 0;
	int allLCInCalls = 0;
	Map<EVENTCAT, Integer> allCatEHInCalls = new HashMap<EVENTCAT, Integer>();
	Map<String, Integer> allCatLCInCalls = new HashMap<String, Integer>();
	
	public static void main(String args[]){
		args = preProcessArgs(opts, args);
		
		if (opts.traceFile==null || opts.traceFile.isEmpty()) {
			// nothing to do
			return;
		}
		if (opts.srcsinkFile==null || opts.srcsinkFile.isEmpty()) {
			if (opts.catsink==null || opts.catsrc==null) {
				// this report relies on an externally purveyed list of taint sources and sinks
				return;
			}
		}
		if (opts.callbackFile ==null || opts.callbackFile.isEmpty()) {
			if (opts.catCallbackFile==null) {
				// this report relies on an externally purveyed list of android callback interfaces
				return;
			}
		}

		securityReport grep = new securityReport();
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
	
	public void run() {
		System.out.println("Running static analysis for security-relevant feature characterization");

		init();
		
		traverse();
		
		String dir = System.getProperty("user.dir");

		try {
			String fngdistfeature = dir + File.separator + "securityfeatures.txt";
			PrintStream psgdistfeature = new PrintStream (new FileOutputStream(fngdistfeature,true));
			collectFeatures(psgdistfeature);
			if (opts.featuresOnly) {
				System.exit(0);
			}
			
			if (opts.debugOut) {
				reportSrcSinks (System.out);
				if (opts.catsink!=null && opts.catsrc!=null) {
					reportSrcs(System.out);
					reportSinks(System.out);
				}
				reportCallbacks (System.out);
				reportLifecycleMethods(System.out);
				if (opts.catCallbackFile!=null) {
					reportEventHandlers(System.out);
				}
			}
			else {
				String fnsrcsink = dir + File.separator + "srcsink.txt";
				PrintStream pssrcsink = new PrintStream (new FileOutputStream(fnsrcsink,true));
				reportSrcSinks (pssrcsink);

				if (opts.catsink!=null && opts.catsrc!=null) {
					String fnsrc = dir + File.separator + "src.txt";
					PrintStream pssrc = new PrintStream (new FileOutputStream(fnsrc,true));
					reportSrcs (pssrc);

					String fnsink = dir + File.separator + "sink.txt";
					PrintStream pssink = new PrintStream (new FileOutputStream(fnsink,true));
					reportSinks (pssink);
				}

				String fncb = dir + File.separator + "callback.txt";
				PrintStream pscb = new PrintStream (new FileOutputStream(fncb,true));
				reportCallbacks(pscb);

				String fnlc = dir + File.separator + "lifecycleMethod.txt";
				PrintStream pslc = new PrintStream (new FileOutputStream(fnlc,true));
				reportLifecycleMethods(pslc);
				
				if (opts.catCallbackFile!=null) {
					String fneh = dir + File.separator + "eventHandler.txt";
					PrintStream pseh = new PrintStream (new FileOutputStream(fneh,true));
					reportEventHandlers(pseh);
				}
			}
		}
		catch (Exception e) {e.printStackTrace();}
		
		System.exit(0);
	}
	
	/**
	 * Descendants may want to use customized event monitors
	 */
	protected void init() {
		packName = ProgramFlowGraph.appPackageName;
		
		// set up the trace stating agent
		stater.setPackagename(packName);
		stater.setTracefile(opts.traceFile);
		
		stater.useCallTree = opts.calltree;
		
		// parse the trace
		stater.stat();
		
		if (opts.srcsinkFile != null) {
			readSrcSinks();
		}
		else if (opts.catsink!=null && opts.catsrc!=null) {
			readCatSrcSinks();
		}
		
		try {
			if (opts.callbackFile!=null) {
				loadAndroidCallbacks();
			}
			else if (opts.catCallbackFile!=null) {
				loadCatAndroidCallbacks();
			}

			for (String clsname : callbackClses) {
				callbackSootClses.add( Scene.v().getSootClass(clsname) );
			}
		}
		catch (Exception e) {
			System.err.println("Failed in parsing the androidCallbacks file: ");
			e.printStackTrace(System.err);
			System.exit(-1);
		}
		for (String lct : iccAPICom.component_type_names) {
			traversedCatLifecycleMethods.put(lct, new HashSet<String>());
			coveredCatLifecycleMethods.put(lct, new HashSet<String>());
			
			allCatLCInCalls.put(lct, 0);
		}
	}
	
	/**
	 * Loads the set of interfaces that are used to implement Android callback
	 * handlers from a file on disk
	 * @return A set containing the names of the interfaces that are used to
	 * implement Android callback handlers
	 */
	private void loadAndroidCallbacks() throws IOException {
		//Set<String> androidCallbacks = new HashSet<String>();
		BufferedReader rdr = null;
		try {
			String fileName = opts.callbackFile;
			if (!new File(fileName).exists()) {
				throw new RuntimeException("Callback definition file not found");
			}
			rdr = new BufferedReader(new FileReader(fileName));
			String line;
			while ((line = rdr.readLine()) != null)
				if (!line.isEmpty())
					callbackClses.add(line);
		}
		finally {
			if (rdr != null)
				rdr.close();
		}
	}
	
	Set<EVENTCAT> allCBCats = new HashSet<EVENTCAT>(Arrays.asList(EVENTCAT.ALL.getDeclaringClass().getEnumConstants()));
	Map<String,EVENTCAT> cat2Literal = new HashMap<String,EVENTCAT>();
	private void loadCatAndroidCallbacks() throws IOException {
		BufferedReader rdr = null;
		for (EVENTCAT cat : allCBCats) {
			cat2Literal.put(cat.toString(),cat);

			traversedCatEventHandlerMethods.put(cat, new HashSet<String>());
			coveredCatEventHandlerMethods.put(cat, new HashSet<String>());
			
			allCatEHInCalls.put(cat, 0);
		}
		try {
			String fileName = opts.catCallbackFile;
			if (!new File(fileName).exists()) {
				throw new RuntimeException("categorized Callback definition file not found");
			}
			rdr = new BufferedReader(new FileReader(fileName));
			String line;
			EVENTCAT curcat = EVENTCAT.ALL;
			while ((line = rdr.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty()) continue;
				
				if (cat2Literal.keySet().contains(line)) {
					curcat = cat2Literal.get(line);
					continue;
				}
				if (curcat == EVENTCAT.ALL) continue;
				catCallbackClses.put(line,curcat);
				
				// maintain a holistic list of ALL callback classes as well
				callbackClses.add(line);
			}
		}
		finally {
			if (rdr != null)
				rdr.close();
		}
	}
	
	protected void readSrcSinks() {
		ISourceSinkDefinitionProvider parser = null;
		String sourceSinkFile = opts.srcsinkFile;

		String fileExtension = sourceSinkFile.substring(sourceSinkFile.lastIndexOf("."));
		fileExtension = fileExtension.toLowerCase();
		
		try {
			if (fileExtension.equals(".xml"))
				parser = XMLSourceSinkParser.fromFile(sourceSinkFile);
			else if(fileExtension.equals(".txt"))
				parser = PermissionMethodParser.fromFile(sourceSinkFile);
		}
		catch (Exception e) {
			System.err.println("Failed in parsing the source-sink file: ");
			e.printStackTrace(System.err);
			System.exit(-1);
		}
		
		
		for (SourceSinkDefinition ssdef : parser.getSources()) {
			allSources.add(ssdef.getMethod().getSignature());
		}
		for (SourceSinkDefinition ssdef : parser.getSinks()) {
			allSinks.add(ssdef.getMethod().getSignature());
		}

		Set<CGNode> allCGNodes = stater.getCG().getInternalGraph().vertexSet();
		for (CGNode n : allCGNodes) {
			if (allSources.contains(n.getSootMethodName())) {
				coveredSources.add(n.getSootMethodName());
				srcCov.incCovered();
				
				allSrcInCalls += stater.getCG().getTotalInCalls(n.getMethodName());
			}
			if (allSinks.contains(n.getSootMethodName())) {
				coveredSinks.add(n.getSootMethodName());
				sinkCov.incCovered();
	
				allSinkInCalls += stater.getCG().getTotalInCalls(n.getMethodName());
			}
			
			allCoveredClasses.add(n.getSootClassName());
			allCoveredMethods.add(n.getSootMethodName());

			allMethodInCalls += stater.getCG().getTotalInCalls(n.getSootMethodName());
		}
		
		for (String src : coveredSources) {
			for (String sink : coveredSinks) {
				//if (stater.getCG().isReachable(src, sink)) {
				int nflows = stater.getCG().getNumberOfReachableFlows(src, sink);
				if (nflows >= 1) {
					// perform detailed inspection 
					if (stater.useCallTree) {
						nflows = stater.getCT().getNumberOfReachableFlows(src, sink);
					}
				}
				/*
				int nflows = 0;
				if (stater.useCallTree) {
					nflows = stater.getCT().getNumberOfReachableFlows(src, sink);
				}
				else {
					nflows = stater.getCG().getNumberOfReachableFlows(src, sink);
				}
				*/
				if (nflows>=1) {
					allEscapeSrcs ++;
					
					//allEscapeSrcInCalls += stater.getCG().getTotalInCalls(src);
					allEscapeSrcInCalls += nflows;
					break;
				}
			}
		}
		for (String sink : coveredSinks) {
			for (String src : coveredSources) {
				//if (stater.getCG().isReachable(src, sink)) {
				int nflows = stater.getCG().getNumberOfReachableFlows(src, sink);
				if (nflows >= 1) {
					// perform detailed inspection 
					if (stater.useCallTree) {
						nflows = stater.getCT().getNumberOfReachableFlows(src, sink);
					}
				}
				/*
				int nflows = 0;
				if (stater.useCallTree) {
					nflows = stater.getCT().getNumberOfReachableFlows(src, sink);
				}
				else {
					nflows = stater.getCG().getNumberOfReachableFlows(src, sink);
				}
				*/
				if (nflows>=1) {
					allReachableSinks ++;
					
					//allReachableSinkInCalls += stater.getCG().getTotalInCalls(sink);
					allReachableSinkInCalls += nflows;
					break;
				}
			}
		}
	}

	protected void readCatSrcSinks() {
		Set<CATEGORY> allcats = new HashSet<CATEGORY>();
		allcats.addAll(Arrays.asList(CATEGORY.ALL.getDeclaringClass().getEnumConstants()));
		CategorizedAndroidSourceSinkParser catsrcparser = 
			new CategorizedAndroidSourceSinkParser(allcats, opts.catsrc, true, false);
		CategorizedAndroidSourceSinkParser catsinkparser = 
			new CategorizedAndroidSourceSinkParser(allcats, opts.catsink, false, true);
		
		for (CATEGORY cat : allcats) {
			traversedCatSrcs.put(cat, new HashSet<String>());
			traversedCatSinks.put(cat, new HashSet<String>());
			coveredCatSrcs.put(cat, new HashSet<String>());
			coveredCatSinks.put(cat, new HashSet<String>());
			allCatSrcInCalls.put(cat, 0);
			allCatSinkInCalls.put(cat, 0);

			allEscapeCatSrcs.put(cat, 0);
			allReachableCatSinks.put(cat, 0);
			allEscapeCatSrcInCalls.put(cat, 0);
			allReachableCatSinkInCalls.put(cat, 0);
		}

		try {
			for (AndroidMethod am : catsrcparser.parse()) {
				allCatSrcs.put(am.getSignature(), am.getCategory());
				
			}
			for (AndroidMethod am : catsinkparser.parse()) {
				allCatSinks.put(am.getSignature(), am.getCategory());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		Set<CGNode> allCGNodes = stater.getCG().getInternalGraph().vertexSet();
		for (CGNode n : allCGNodes) {
			String mename = n.getSootMethodName();
			if (allCatSrcs.keySet().contains(mename)) {
				srcCov.incCovered();

				CATEGORY cat = allCatSrcs.get(mename);
				Set<String> cts = coveredCatSrcs.get(cat);
				if (null==cts) {
					cts = new HashSet<String>();
					coveredCatSrcs.put(cat, cts);
				}
				cts.add(mename);
				
				Integer cct = allCatSrcInCalls.get(cat); 
				if (cct==null) {
					cct = 0;
				}
				int curn = stater.getCG().getTotalInCalls(n.getMethodName());
				cct += curn;
				allCatSrcInCalls.put(cat, cct);
				
				allSrcInCalls += curn;
			}
			if (allCatSinks.keySet().contains(mename)) {
				sinkCov.incCovered();

				CATEGORY cat = allCatSinks.get(mename);
				Set<String> cts = coveredCatSinks.get(cat);
				if (null==cts) {
					cts = new HashSet<String>();
					coveredCatSinks.put(cat, cts);
				}
				cts.add(mename);

				Integer cct = allCatSinkInCalls.get(cat); 
				if (cct==null) {
					cct = 0;
				}
				int curn = stater.getCG().getTotalInCalls(n.getMethodName());
				cct += curn;
				allCatSinkInCalls.put(cat, cct);
				
				allSinkInCalls += curn;
			}
			
			allCoveredClasses.add(n.getSootClassName());
			allCoveredMethods.add(mename);
			
			allMethodInCalls += stater.getCG().getTotalInCalls(mename);
			
			/* a sloppy test of the graph reachability in the dynamic cg 
			for (CGNode callee : stater.getCG().getAllCallees(mename)) {
				//assert stater.getCG().getPath(mename, callee.getMethodName()).size()>=1;
				assert stater.getCG().isReachable(mename, callee.getSootMethodName());
				for (CGNode callee2 : stater.getCG().getAllCallees(callee.getMethodName())) {
					//assert stater.getCG().getPath(mename, callee2.getMethodName()).size()>=2;
					assert stater.getCG().isReachable(mename, callee2.getSootMethodName());
				}
			}
			*/
		}
		
		for (CATEGORY catsrc : coveredCatSrcs.keySet()) {
			for (String src : coveredCatSrcs.get(catsrc)) {
				boolean bbreak = false;
				for (CATEGORY catsink : coveredCatSinks.keySet()) {
					for (String sink : coveredCatSinks.get(catsink)) {
						/*
						if (stater.getCG().isReachable(src, sink)) {
							Integer cct = allEscapeCatSrcs.get(catsrc);
							if (null==cct) cct = 0;
							cct ++;
							allEscapeCatSrcs.put(catsrc, cct);
							
							allEscapeSrcs ++;
							
							allEscapeCatSrcInCalls.put(catsrc, 
									allEscapeCatSrcInCalls.get(catsrc)+stater.getCG().getTotalInCalls(src));
							allEscapeSrcInCalls += stater.getCG().getTotalInCalls(src);

							bbreak = true;
							break;
						}
						*/
						int nflows = stater.getCG().getNumberOfReachableFlows(src, sink);
						if (nflows >= 1) {
							// perform detailed inspection 
							if (stater.useCallTree) {
								nflows = stater.getCT().getNumberOfReachableFlows(src, sink);
							}
						}
						/*
						int nflows = 0;

						if (stater.useCallTree) {
							nflows = stater.getCT().getNumberOfReachableFlows(src, sink);
						}
						else {
							nflows = stater.getCG().getNumberOfReachableFlows(src, sink);
						}
						*/
						if (nflows >= 1) {
							Integer cct = allEscapeCatSrcs.get(catsrc);
							if (null==cct) cct = 0;
							cct ++;
							allEscapeCatSrcs.put(catsrc, cct);
							
							allEscapeSrcs ++;
							
							allEscapeCatSrcInCalls.put(catsrc, allEscapeCatSrcInCalls.get(catsrc)+nflows);
							allEscapeSrcInCalls += nflows;

							System.out.println(nflows + " taint flow paths FOUND from " + src + " to " + sink);
							bbreak = true;
							break;
							
						}
					}
					if (bbreak) break;
				}
			}
		}

		for (CATEGORY catsink : coveredCatSinks.keySet()) {
			for (String sink : coveredCatSinks.get(catsink)) {
				boolean bbreak = false;
				for (CATEGORY catsrc : coveredCatSrcs.keySet()) {
					for (String src : coveredCatSrcs.get(catsrc)) {
						/*
						if (stater.getCG().isReachable(src, sink)) {
							Integer cct = allReachableCatSinks.get(catsink);
							if (null==cct) cct = 0;
							cct ++;
							allReachableCatSinks.put(catsink, cct);
							
							allReachableSinks ++;
							
							allReachableCatSinkInCalls.put(catsink, 
									allReachableCatSinkInCalls.get(catsink)+stater.getCG().getTotalInCalls(sink));
							allReachableSinkInCalls += stater.getCG().getTotalInCalls(sink);

							bbreak = true;
							break;
						}
						*/
						int nflows = stater.getCG().getNumberOfReachableFlows(src, sink);
						if (nflows >= 1) {
							// perform detailed inspection 
							if (stater.useCallTree) {
								nflows = stater.getCT().getNumberOfReachableFlows(src, sink);
							}
						}
						/*
						int nflows = 0;
						if (stater.useCallTree) {
							nflows = stater.getCT().getNumberOfReachableFlows(src, sink);
						}
						else {
							nflows = stater.getCG().getNumberOfReachableFlows(src, sink);
						}
						*/
						if (nflows >= 1) {
							Integer cct = allReachableCatSinks.get(catsink);
							if (null==cct) cct = 0;
							cct ++;
							allReachableCatSinks.put(catsink, cct);
							
							allReachableSinks ++;
							
							allReachableCatSinkInCalls.put(catsink, allReachableCatSinkInCalls.get(catsink)+nflows);
							allReachableSinkInCalls += nflows;

							System.out.println(nflows + " taint flow paths FOUND from " + src + " to " + sink);
							bbreak = true;
							break;
							
						}
					}
					if (bbreak) break;
				}
			}
		}
	}

	public String isCallbackClass(SootClass cls) {
		FastHierarchy har = Scene.v().getOrMakeFastHierarchy();
		for (SootClass scls : callbackSootClses) {
			if (har.getAllSubinterfaces(scls).contains(cls)) {
				return scls.getName();
			}
			if (har.getAllImplementersOfInterface(scls).contains(cls)) {
				return scls.getName();
			}
		}
		return null;
	}
	public boolean isCallbackClassActive(SootClass cls) {
		Hierarchy har = Scene.v().getActiveHierarchy();
		for (SootClass scls : callbackSootClses) {
			if (har.getSubinterfacesOf(scls).contains(cls)) {
				return true;
			}
			if (har.getImplementersOf(scls).contains(cls)) {
				return true;
			}
		}
		return false;
	}
	
	/** obtaining all statically resolved ICCs needs a separate analysis such as IC3 */ 
	int totalCls = 0, totalMethods = 0;
	public void traverse() {
		int all_methods=0;
		int count_exceptions=0;
		/* traverse all classes */
		Iterator<SootClass> clsIt = Scene.v().getClasses().snapshotIterator(); //iterator(); //ProgramFlowGraph.inst().getAppClasses().iterator();
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
			totalCls ++;
			String CallbackCls = isCallbackClass(sClass);
			boolean isComponent = iccAPICom.getComponentType(sClass).compareTo("Unknown")!=0;
			
			/* traverse all methods of the class */
			Iterator<SootMethod> meIt = sClass.getMethods().iterator();
			while (meIt.hasNext()) {
				SootMethod sMethod = (SootMethod) meIt.next();
				String meId = sMethod.getSignature();
				
				totalMethods ++;
				all_methods++; //to calculate number of methods that generate exceptions
				if (isComponent && AndroidEntryPointConstants.isLifecycleMethod(sMethod.getSubSignature())) {
					traversedLifecycleMethods.add(meId);
					lifecycleCov.incTotal();
					
					String lifecycleType = AndroidEntryPointConstants.getLifecycleType(sMethod.getSubSignature());
					traversedCatLifecycleMethods.get(lifecycleType).add(meId);

					if (allCoveredMethods.contains( meId )) {
						coveredLifecycleMethods.add(meId);
						lifecycleCov.incCovered();
						
						coveredCatLifecycleMethods.get(lifecycleType).add(meId);
						
						allCatLCInCalls.put(lifecycleType, allCatLCInCalls.get(lifecycleType) + stater.getCG().getTotalInCalls(meId));
						allLCInCalls += stater.getCG().getTotalInCalls(meId);
					}
				}
				
				if (CallbackCls!=null && sMethod.getName().startsWith("on")) {
					traversedEventHandlerMethods.add(meId);
					eventhandlerCov.incTotal();
					
					EVENTCAT ehType = EVENTCAT.ALL;
					if (opts.catCallbackFile!=null) {
						ehType = catCallbackClses.get(CallbackCls);
						traversedCatEventHandlerMethods.get(ehType).add(meId);
					}
					
					if (allCoveredMethods.contains(meId)) {
						coveredEventHandlerMethods.add(meId);
						eventhandlerCov.incCovered();
						
						if (opts.catCallbackFile!=null) {
							coveredCatEventHandlerMethods.get(ehType).add(meId);
							allCatEHInCalls.put(ehType, allCatEHInCalls.get(ehType) + stater.getCG().getTotalInCalls(meId));
						}
						allEHInCalls += stater.getCG().getTotalInCalls(meId);
					}
				}

				if ( !sMethod.isConcrete() ) {
                    // skip abstract methods and phantom methods, and native methods as well
                    continue; 
                }
				try {
					Body body = sMethod.retrieveActiveBody();
					PatchingChain<Unit> pchn = body.getUnits();
				
					Iterator<Unit> itchain = pchn.snapshotIterator();
					while (itchain.hasNext()) {
						Stmt s = (Stmt)itchain.next();
						if (!s.containsInvokeExpr()) {
							continue;
						}
						String calleename = s.getInvokeExpr().getMethod().getSignature();
					
						if (opts.srcsinkFile != null) {
							if (allSources.contains(calleename)) {
								if (traversedSources.add(calleename))
									srcCov.incTotal();
							}
							if (allSinks.contains(calleename)) {
								if (traversedSinks.add(calleename))
									sinkCov.incTotal();
							}
						}
						else if (opts.catsink!=null && opts.catsrc!=null) {
							if (allCatSrcs.keySet().contains(calleename)) {
	
								Set<String> cts = traversedCatSrcs.get(allCatSrcs.get(calleename));
								if (null==cts) {
									cts = new HashSet<String>();
									traversedCatSrcs.put(allCatSrcs.get(calleename), cts);
								}
								if (cts.add(calleename))
									srcCov.incTotal();
							}
							if (allCatSinks.keySet().contains(calleename)) {
	
								Set<String> cts = traversedCatSinks.get(allCatSinks.get(calleename));
								if (null==cts) {
									cts = new HashSet<String>();
									traversedCatSinks.put(allCatSinks.get(calleename), cts);
								}
								if (cts.add(calleename))
									sinkCov.incTotal();
							}
						}
					}
				} //try
				catch (Exception ex){
					count_exceptions++;
					System.out.println("nadiaException "+count_exceptions);
				}
	
			} // -- while (meIt.hasNext()) 
			
		} // -- while (clsIt.hasNext())
		if (count_exceptions != 0) {
			String dir = System.getProperty("user.dir");
			try {
				BufferedWriter writer = new BufferedWriter(new FileWriter(dir + File.separator + "exceptions_security.txt", true));
				writer.write(packName+" "+count_exceptions+" out of "+all_methods+" "+percentage(count_exceptions, all_methods));
	                        writer.newLine();
	                        writer.close();
	                 }
	                 catch(IOException e) {all_methods++;
	                         e.printStackTrace();
	                 }
		}
	}
	
	public void reportSrcSinks(PrintStream os) {
		/** report statistics for the current trace */
		if (opts.debugOut) {
			os.println(srcCov);
			os.println(sinkCov);
		}
		if (opts.debugOut) {
			os.println("*** tabulation ***");
			os.println("format: s_source\t s_sink\t d_source\t d_sink\t s_all\t d_all\t d_allInCall\t d_allSrcInCall\t d_allSinkInCall\t d_escapeSrc\t d_reachableSink\t d_escapeSrcInCall\t d_reachableSinkInCall");
		}
		os.println(srcCov.getTotal() +"\t " + sinkCov.getTotal() + "\t " + 
				srcCov.getCovered() + "\t " + sinkCov.getCovered() + "\t" +
				totalMethods + "\t" + allCoveredMethods.size() + "\t" + allMethodInCalls + "\t" +
				allSrcInCalls + "\t" + allSinkInCalls + "\t" +
				allEscapeSrcs + "\t" + allReachableSinks + "\t" +
				allEscapeSrcInCalls + "\t" + allReachableSinkInCalls);				
	}
	
	static String percentage(int a, int b) {
		DecimalFormat df = new DecimalFormat("#.####");
		if (b==0) return df.format(0); 
		return df.format(a*1.0/b);
	}
		
	public void reportSrcs(PrintStream os) {
		// list src/sink by category if applicable
		if (opts.debugOut) {
			os.println("[SOURCE]");
			os.println("format: category\t s_source\t d_source\t d_allSrcInCall\t d_allEscapeSrcs\t d_allEscapeSrcInCalls"+
						"\t s_sourceP\t d_sourceP\t d_allSrcInCallP\t d_allEscapeSrcsP\t d_allEscapeSrcInCallsP");
		}
		for (CATEGORY cat : traversedCatSrcs.keySet()) {
			os.println( cat + "\t" + traversedCatSrcs.get(cat).size() + "\t" + 
					(coveredCatSrcs.containsKey(cat)?coveredCatSrcs.get(cat).size():0) + "\t" +
					(allCatSrcInCalls.containsKey(cat)?allCatSrcInCalls.get(cat):0) + "\t" + 
					(allEscapeCatSrcs.containsKey(cat)?allEscapeCatSrcs.get(cat):0) + "\t" +
					allEscapeCatSrcInCalls.get(cat) + "\t" +
					percentage(traversedCatSrcs.get(cat).size(),srcCov.getTotal()) + "\t" + 
					percentage(coveredCatSrcs.get(cat).size(),srcCov.getCovered()) + "\t" +
					percentage(allCatSrcInCalls.get(cat),allSrcInCalls) + "\t" + 
					percentage(allEscapeCatSrcs.get(cat),allEscapeSrcs) + "\t" +
					percentage(allEscapeCatSrcInCalls.get(cat),allEscapeSrcInCalls) );
		}
	}

	public void reportSinks(PrintStream os) {
		if (opts.debugOut) {
			os.println("[SINK]");
			os.println("format: category\t s_sink\t d_sink\t d_allSinkInCall\t d_allReachableSinks\t d_allReachableSinkInCalls"+
			"\t s_sinkP\t d_sinkP\t d_allSinkInCallP\t d_allReachableSinksP\t d_allReachableSinkInCallsP");
		}
		for (CATEGORY cat : traversedCatSinks.keySet()) {
			os.println( cat + "\t" + traversedCatSinks.get(cat).size() + "\t" + 
					(coveredCatSinks.containsKey(cat)?coveredCatSinks.get(cat).size():0) + "\t" +
					(allCatSinkInCalls.containsKey(cat)?allCatSinkInCalls.get(cat):0) + "\t" + 
					(allReachableCatSinks.containsKey(cat)?allReachableCatSinks.get(cat):0) + "\t" +
					allReachableCatSinkInCalls.get(cat) + "\t" +
					percentage(traversedCatSinks.get(cat).size(),sinkCov.getTotal()) + "\t" + 
					percentage(coveredCatSinks.get(cat).size(),sinkCov.getCovered()) + "\t" +
					percentage(allCatSinkInCalls.get(cat),allSinkInCalls) + "\t" + 
					percentage(allReachableCatSinks.get(cat),allReachableSinks) + "\t" +
					percentage(allReachableCatSinkInCalls.get(cat),allReachableSinkInCalls) );
		}
	}
	
	public void reportCallbacks(PrintStream os) {
		/** report statistics for the current trace */
		if (opts.debugOut) {
			os.println(lifecycleCov);
			os.println(eventhandlerCov);
		}
		
		if (opts.debugOut) {
			os.println("*** tabulation ***");
			os.println("format: s_lifecycle\t s_eventHandler\t d_lifecycle\t d_eventHandler\t s_all\t d_all\t d_allInCalls\t d_allLifecycleInCalls\t d_allEventhandlerInCalls");
		}
		os.println(lifecycleCov.getTotal() +"\t " + eventhandlerCov.getTotal() + "\t " + 
				lifecycleCov.getCovered() + "\t " + eventhandlerCov.getCovered() + "\t" +				
				totalMethods + "\t" + allCoveredMethods.size() + "\t" + allMethodInCalls + "\t" +
				allLCInCalls + "\t" + allEHInCalls);				
	}

	public void reportLifecycleMethods(PrintStream os) {
		/** report statistics for the current trace */
		if (opts.debugOut) {
			os.println(lifecycleCov);
			os.println(eventhandlerCov);
		}
		
		if (opts.debugOut) {
			os.println("[LifecycleMethods]");
			os.println("format: category\t s_lifecycle\t d_lifecycle\t d_lifecycleInCalls" + 
			"\t s_lifecycleP\t d_lifecycleP\t d_lifecycleInCallsP");
		}
		for (String lct : traversedCatLifecycleMethods.keySet()) {
			os.println(lct + "\t" + traversedCatLifecycleMethods.get(lct).size() + "\t" + 
						coveredCatLifecycleMethods.get(lct).size() + "\t" + allCatLCInCalls.get(lct) + "\t" +
						percentage(traversedCatLifecycleMethods.get(lct).size(),lifecycleCov.getTotal()) + "\t" + 
						percentage(coveredCatLifecycleMethods.get(lct).size(),lifecycleCov.getCovered()) + "\t" + 
						percentage(allCatLCInCalls.get(lct), allLCInCalls)); 
		}
	}

	public void reportEventHandlers(PrintStream os) {
		/** report statistics for the current trace */
		if (opts.debugOut) {
			os.println(lifecycleCov);
			os.println(eventhandlerCov);
		}
		
		if (opts.debugOut) {
			os.println("[EventHandlers]");
			os.println("format: category\t s_eventhandler\t d_eventHandler\t d_eventhandlerInCalls" +
			"\t s_eventhandlerP\t d_eventHandlerP\t d_eventhandlerInCallsP");
		}
		for (EVENTCAT et : traversedCatEventHandlerMethods.keySet()) {
			os.println(et + "\t" + traversedCatEventHandlerMethods.get(et).size() + "\t" + 
					coveredCatEventHandlerMethods.get(et).size() + "\t" + allCatEHInCalls.get(et) + "\t" +
					percentage(traversedCatEventHandlerMethods.get(et).size(), eventhandlerCov.getTotal()) + "\t" + 
					percentage(coveredCatEventHandlerMethods.get(et).size(), eventhandlerCov.getCovered()) + "\t" + 
					percentage(allCatEHInCalls.get(et), allEHInCalls) );
		}
	}
	
	public void collectFeatures(PrintStream os) {
		// only take the top 5/6 most assessed categories as features
		CATEGORY[] srccats = {CATEGORY.ACCOUNT_INFORMATION, CATEGORY.CALENDAR_INFORMATION, CATEGORY.LOCATION_INFORMATION,
				CATEGORY.NETWORK_INFORMATION, CATEGORY.SYSTEM_SETTINGS};
		//{"ACCOUNT_INFORMATION", "CALENDAR_INFORMATION", "LOCATION_INFORMATION", "NETWORK_INFORMATION", "SYSTEM_SETTINGS"};

		CATEGORY[] sinkcats = {CATEGORY.ACCOUNT_SETTINGS, CATEGORY.FILE, CATEGORY.LOG, CATEGORY.NETWORK, CATEGORY.SMS_MMS, CATEGORY.SYSTEM_SETTINGS};
		//{"ACCOUNT_SETTINGS", "FILE", "LOG", "NETWORK", "SMS_MMS", "SYSTEM_SETTINGS"};
		String[] lccats = {"Activity", "Application", "BroadcastReceiver", "ContentProvider", "Service"};
		EVENTCAT[] ehcats = {EVENTCAT.APPLICATION_MANAGEMENT, EVENTCAT.SYSTEM_STATUS, EVENTCAT.LOCATION_STATUS, EVENTCAT.HARDWARE_MANAGEMENT, EVENTCAT.NETWORK_MANAGEMENT, 
				EVENTCAT.APP_BAR, EVENTCAT.MEDIA_CONTROL, EVENTCAT.VIEW, EVENTCAT.WIDGET, EVENTCAT.DIALOG};
		//{"DIALOG", "HARDWARE_MANAGEMENT", "MEDIA_CONTROL", "NETWORK_MANAGEMENT", "VIEW",	"WIDGET"}; 
		if (opts.debugOut) { 
			os.println("*** security feature collection *** "); 
			os.print("format: packagename"+"\t"+"src"+"\t"+"sink"+"\t"+"srcIns"+"\t"+"sinkIns"
					+"\t"+"riskSrc"+"\t"+"riskSink"+ "\t" + "riskSrcIns" + "\t" + "riskSinkIns");
			for (CATEGORY srccatT:srccats) {
				String srccat = srccatT.toString();
				os.print("\t" + srccat);
				os.print("\t" + srccat+"-Ins");
				os.print("\t" + srccat+"-escape");
				os.print("\t" + srccat+"-escape-Ins");
			}

			for (CATEGORY sinkcatT:sinkcats) {
				String sinkcat = sinkcatT.toString();
				os.print("\t" + sinkcat);
				os.print("\t" + sinkcat+"-Ins");
				os.print("\t" + sinkcat+"-reach");
				os.print("\t" + sinkcat+"-reach-Ins");
			}

			os.print("\t" + "lc" + "\t" + "eh" + "\t" + "lc-ins" + "\t" + "eh-ins");
			for (String lccat:lccats) {
				os.print("\t" + lccat);
				os.print("\t" + lccat+"-Ins");
			}
			for (EVENTCAT ehcatT:ehcats) {
				String ehcat = ehcatT.toString();
				os.print("\t" + ehcat);
				os.print("\t" + ehcat+"-Ins");
			}
			os.println();
		}
		// 1. src/sink usage and reachability
		Path p = Paths.get(soot.options.Options.v().process_dir().get(0));
		os.print(p.getFileName().toString());
		os.print("\t" + percentage(srcCov.getCovered(),allCoveredMethods.size()) +
				   "\t" + percentage(sinkCov.getCovered(), allCoveredMethods.size()) +
				   "\t" + percentage(allSrcInCalls, allMethodInCalls) + 
				   "\t" + percentage(allSinkInCalls, allMethodInCalls) + 
				   "\t" + percentage(allEscapeSrcs, srcCov.getCovered()) + 
				   "\t" + percentage(allReachableSinks, sinkCov.getCovered()) + 
				   "\t" + percentage(allEscapeSrcInCalls, allSrcInCalls) +
				   "\t" + percentage(allReachableSinkInCalls, allSinkInCalls));
		
		// 2. src/sink categorization (only the ones in which we found significant different between benign and malware traces)
		for (CATEGORY srccat:srccats) {
			os.print("\t" +
					percentage(coveredCatSrcs.get(srccat).size(),srcCov.getCovered()) + "\t" +
					percentage(allCatSrcInCalls.get(srccat),allSrcInCalls) + "\t" + 
					percentage(allEscapeCatSrcs.get(srccat),allEscapeSrcs) + "\t" +
					percentage(allEscapeCatSrcInCalls.get(srccat),allEscapeSrcInCalls) );
		}
		
		for (CATEGORY sinkcat:sinkcats) {
			os.print("\t" +
					percentage(coveredCatSinks.get(sinkcat).size(),sinkCov.getCovered()) + "\t" +
					percentage(allCatSinkInCalls.get(sinkcat),allSinkInCalls) + "\t" + 
					percentage(allReachableCatSinks.get(sinkcat),allReachableSinks) + "\t" +
					percentage(allReachableCatSinkInCalls.get(sinkcat),allReachableSinkInCalls) );
		}
		
		// 3. callback usage
		os.print("\t" + 
				percentage(lifecycleCov.getCovered(), allCoveredMethods.size()) + "\t" +
				percentage(eventhandlerCov.getCovered(), allCoveredMethods.size()) + "\t" +				
				percentage(allLCInCalls, allMethodInCalls) + "\t" +
				percentage(allEHInCalls, allMethodInCalls));
		
		// 4. callback categorization
		for (String lccat:lccats) {
			os.print("\t" +
					percentage(coveredCatLifecycleMethods.get(lccat).size(),lifecycleCov.getCovered()) + "\t" + 
					percentage(allCatLCInCalls.get(lccat), allLCInCalls));	
		}
		for (EVENTCAT ehcatT:ehcats) {
			os.print("\t" +
					percentage(coveredCatEventHandlerMethods.get(ehcatT).size(), eventhandlerCov.getCovered()) + "\t" + 
					percentage(allCatEHInCalls.get(ehcatT), allEHInCalls) );
		}
		
		os.println();
	}
}

/* vim :set ts=4 tw=4 tws=4 */

