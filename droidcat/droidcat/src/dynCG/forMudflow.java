/* Code from MaMaDroid */
package dynCG;

import org.xmlpull.v1.XmlPullParserException;

import dua.util.Pair;
import reporters.reportOpts;

import java.lang.System;
import soot.PackManager;
import soot.Scene;
import soot.SootMethod;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.options.Options;
import soot.util.MultiMap;
import soot.util.queue.QueueReader;


import org.xmlpull.v1.XmlPullParserException;
import soot.*;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.infoflow.Infoflow;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.InfoflowConfiguration.CodeEliminationMode;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.android.data.AndroidMethod;
import soot.jimple.infoflow.android.data.AndroidMethod.CATEGORY;
import soot.jimple.infoflow.android.data.parsers.CategorizedAndroidSourceSinkParser;
import soot.jimple.infoflow.results.InfoflowResults;
import soot.jimple.infoflow.results.ResultSinkInfo;
import soot.jimple.infoflow.results.ResultSourceInfo;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.jimple.infoflow.source.ISourceSinkManager;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.options.Options;

import java.io.*;
import java.util.*;

import static soot.util.PhaseDumper.v;

public class forMudflow {
	static CATEGORY CONTENT_RESOLVER, NO_SENSITIVE_SOURCE, NO_SENSITIVE_SINK;

	protected static reportOpts opts = new reportOpts();
	
	public static void main(String[] args) throws FileNotFoundException {
		args = preProcessArgs(opts, args);
		
		String appToRun = (args[0]);
		String androidPlatform = args[1];
		String filename = appToRun + ".txt";
		String outdir = args[2];
		
		//List<String> toInclude = Arrays.asList("java.", "android.", "org.", "com.", "javax.");
		//List<String> toExclude = Arrays.asList("soot.");
		soot.G.reset();
		SetupApplication app = new SetupApplication("/home/hcai/Android/Sdk/platforms/android-21/android.jar", appToRun);
		app.getConfig().setEnableStaticFieldTracking(false); //no static field tracking --nostatic
		app.getConfig().setAccessPathLength(1); // specify access path length
		app.getConfig().setFlowSensitiveAliasing(false); // alias flowin
		        
        (app.getConfig()).printSummary();
        (app.getConfig()).setAccessPathLength(3);
        (app.getConfig()).setCodeEliminationMode(CodeEliminationMode.NoCodeElimination);
        (app.getConfig()).setEnableImplicitFlows(false);
        
        (app.getConfig()).setInspectSinks(true);
        (app.getConfig()).setInspectSources(true);
        (app.getConfig()).setComputeResultPaths(true);
		
        (app.getConfig()).setLayoutMatchingMode(null);
        
		
		app.setCallbackClasses(null);
		
		try {
			app.calculateSourcesSinksEntrypoints("/home/hcai/libs/SourcesAndSinks.txt");
			readCatSrcSinks();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (XmlPullParserException e) {
			e.printStackTrace();
		}
		
	    soot.G.reset();
	    
	    String flowpathfile = outdir + File.separator + new File(filename).getName();
		PrintStream psflowpath = new PrintStream (new FileOutputStream(flowpathfile,true));
		
		String srcmapfile = outdir + File.separator + "susi_src_list.txt";
		PrintStream pssrcmap = new PrintStream (new FileOutputStream(srcmapfile,true));

        InfoflowResults resultMap =  app.runInfoflow();
        //resultMap.printResults();
        System.out.println("dumping results to files....");
        MultiMap<ResultSinkInfo, ResultSourceInfo> resmap = resultMap.getResults();
        
        HashSet<CATEGORY> allsrccats = new HashSet<CATEGORY>();
        //HashMap<Pair<String,String>, Integer> allpaths = new HashMap<Pair<String,String>, Integer>();
                
        for (ResultSinkInfo sinkinfo : resmap.keySet()) {
        	SootMethod sinkm = utils.utils.pickCallee(sinkinfo.getSink());
        	if (null == sinkm) continue;
        	CATEGORY sinkcat = allCatSinks.get(sinkm.getSignature());
        	if (null == sinkcat) sinkcat = NO_SENSITIVE_SINK;
        	if (CATEGORY.NO_CATEGORY == sinkcat) {
        		sinkcat = NO_SENSITIVE_SINK;
        		if (sinkm.getDeclaringClass().toString().toLowerCase().contains("android.net.uri"))
        			sinkcat = CONTENT_RESOLVER;
        	}
        	
        	for (ResultSourceInfo srcinfo : resmap.get(sinkinfo)) {
        		SootMethod srcm = utils.utils.pickCallee(srcinfo.getSource());
        		if (null == srcm) continue;
        		
        		CATEGORY srccat = allCatSrcs.get(srcm.getSignature());
        		
        		if (null == srccat) srccat = NO_SENSITIVE_SOURCE;
        		if (CATEGORY.NO_CATEGORY == srccat) {
        			srccat = NO_SENSITIVE_SOURCE;
            		if (srcm.getDeclaringClass().toString().toLowerCase().contains("android.net.uri"))
            			srccat = CONTENT_RESOLVER;
            	}
        		
        		allsrccats.add(srccat);
        		
        		/*
        		Pair<String,String> path = new Pair<String,String>(srcm.getSignature(),sinkm.getSignature());
        		if (allpaths.containsKey(path)) {
        			allpaths.put(path, allpaths.get(path) + 1);
        		}
        		else {
        			allpaths.put(path, 1);
        		}
        		*/
        		
        		psflowpath.print(srccat == NO_SENSITIVE_SOURCE?"NO_SENSITIVE_SOURCE":srcm.getSignature());
        		psflowpath.print(" -> ");
        		psflowpath.println(sinkcat == NO_SENSITIVE_SINK?"NO_SENSITIVE_SINK":sinkm.getSignature());
        		
        	}
        }
        
        for (CATEGORY srccat : allsrccats) {
        	pssrcmap.print(appToRun + ";");
        	if (srccat == NO_SENSITIVE_SOURCE) {
            	pssrcmap.print("NO_SENSITIVE_SOURCE");
            }
        	else if (srccat == CONTENT_RESOLVER) {
        		pssrcmap.print("CONTENT_RESOLVER");
        	}
        	else {
        		pssrcmap.print(srccat.toString());
        	}
        	pssrcmap.println();
        }
        
        psflowpath.close();
        pssrcmap.close();

		/*

		PackManager.v().getPack("cg");
		PackManager.v().getPack("jb");
		PackManager.v().getPack("wjap.cgg");
		Options.v().set_src_prec(Options.src_prec_apk);
		Options.v().set_process_dir(Collections.singletonList(appToRun));
		Options.v().set_android_jars(androidPlatform);
		Options.v().force_android_jar();
		Options.v().set_force_android_jar("/home/hcai/Android/Sdk/platforms/android-21/android.jar");
		Options.v().set_whole_program(true);
		Options.v().set_allow_phantom_refs(true);
		Options.v().set_prepend_classpath(true);
		Options.v().set_app(true);
		Options.v().set_include(toInclude);
		Options.v().set_exclude(toExclude);
		Options.v().set_output_format(Options.output_format_xml);
		//Options.v().set_output_dir("output/");
		Options.v().set_soot_classpath("/home/hcai/libs/soot-trunk.jar:/home/hcai/libs/soot-infoflow.jar:/home/hcai/libs/soot-infoflow-android.jar:/home/hcai/libs/axml-2.0.jar:/home/hcai/libs/slf4j-simple-1.7.5.jar:/home/hcai/libs/slf4j-api-1.7.5.jar");
		Options.v().setPhaseOption("cg", "safe-newinstance:true");
		Options.v().setPhaseOption("cg.spark", "on");
		Options.v().setPhaseOption("wjap.cgg", "show-lib-meths:true");
		Options.v().setPhaseOption("jb", "use-original-names:true");
		Scene.v().loadNecessaryClasses();
		SootMethod entryPoint = app.getEntryPointCreator().createDummyMain();
		Options.v().set_main_class(entryPoint.getSignature());
		Scene.v().setEntryPoints(Collections.singletonList(entryPoint));
		System.out.println(entryPoint.getActiveBody());
		try {
			PackManager.v().runPacks();
		}
		catch (Exception f){
			f.printStackTrace();
		}
		try (BufferedWriter writer = new BufferedWriter(
				new FileWriter(
						new File(filename)))
		){
			writer.write(Scene.v().getCallGraph().toString());
		}
		catch (IOException e){
			System.out.println("An error occurred");
		}
		
		*/
        System.out.println("done processing " + appToRun);
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
	
	/** for categorized source/sink */
	static Map<String, CATEGORY> allCatSrcs = new HashMap<String,CATEGORY>();
	static Map<String, CATEGORY> allCatSinks = new HashMap<String,CATEGORY>();
	protected static void readCatSrcSinks() {
		Set<CATEGORY> allcats = new HashSet<CATEGORY>();
		allcats.addAll(Arrays.asList(CATEGORY.ALL.getDeclaringClass().getEnumConstants()));
		CategorizedAndroidSourceSinkParser catsrcparser = 
			new CategorizedAndroidSourceSinkParser(allcats, opts.catsrc, true, false);
		CategorizedAndroidSourceSinkParser catsinkparser = 
			new CategorizedAndroidSourceSinkParser(allcats, opts.catsink, false, true);
		
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
	}
}
