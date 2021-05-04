/**
 * File: src/eventTracker/sceneInstr.java
 * -------------------------------------------------------------------------------------------
 * Date			Author      Changes
 * -------------------------------------------------------------------------------------------
 * 02/04/17		hcai		created; for the instrumentation monitoring event-handling callbacks
 * 02/15/17		hcai		first working version after long debugging (trouble laid in disallowing phantom classes
*/
package eventTracker;

import iacUtil.*;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import profile.InstrumManager;

import dua.Extension;
import dua.Forensics;
import dua.global.ProgramFlowGraph;
import dua.method.CFG;
import dua.method.CFG.CFGNode;


import iacUtil.utils;
import iacUtil.iccAPICom.EVENTCAT;
import iacUtil.AndroidEntryPointConstants;
import iacUtil.iccAPICom;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import soot.Body;
import soot.FastHierarchy;
import soot.PatchingChain;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.Jimple;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;

public class sceneInstr implements Extension {
	protected SootClass clsMonitor = null;
	protected SootMethod mEventTracker = null;
	
	public static Options opts = new Options();
	
	// whether instrument in 3rd party code such as android.support.v$x 
	public static boolean g_instr3rdparty = false;
	
	public static void main(String args[]){
		args = preProcessArgs(opts, args);

		sceneInstr eventinstr = new sceneInstr();
		// examine catch blocks
		dua.Options.ignoreCatchBlocks = false;
		dua.Options.skipDUAAnalysis = false;
		dua.Options.modelAndroidLC = false;
		dua.Options.analyzeAndroid = true;
		
		soot.options.Options.v().set_src_prec(soot.options.Options.src_prec_apk);
		
		//output as APK, too//-f J
		soot.options.Options.v().set_output_format(soot.options.Options.output_format_dex);
		soot.options.Options.v().set_force_overwrite(true);
		
		Scene.v().addBasicClass("com.ironsource.mobilcore.BaseFlowBasedAdUnit",SootClass.SIGNATURES);
		
		Scene.v().addBasicClass("eventTracker.Monitor");
		
		Forensics.registerExtension(eventinstr);
		
		Forensics.main(args);
	}
	
	public static String[] preProcessArgs(Options _opts, String[] args) {
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
	
	Set<EVENTCAT> allCBCats = new HashSet<EVENTCAT>(Arrays.asList(EVENTCAT.ALL.getDeclaringClass().getEnumConstants()));
	Map<String,EVENTCAT> cat2Literal = new HashMap<String,EVENTCAT>();
	Set<String> callbackClses = new HashSet<String>();
	Map<String,EVENTCAT> catCallbackClses = new HashMap<String,EVENTCAT>();
	Set<SootClass> callbackSootClses = new HashSet<SootClass>();
	
	private void loadCatAndroidCallbacks() throws IOException {
		BufferedReader rdr = null;
		for (EVENTCAT cat : allCBCats) {
			cat2Literal.put(cat.toString(),cat);
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

	protected void init() {
        clsMonitor = Scene.v().getSootClass("eventTracker.Monitor");
        
        /** add our runtime monitor to application class so that it can be packed together 
         * with the instrumented code into the resulting APK package
         */
        clsMonitor.setApplicationClass();
        mEventTracker = clsMonitor.getMethodByName("onEvent");
		
		try {
			if (opts.catCallbackFile!=null) {
				loadCatAndroidCallbacks();
			}

			for (String clsname : callbackClses) {
				SootClass scl = null;
				try {
					scl = Scene.v().getSootClass(clsname);
				}
				catch (Exception e) {System.err.println("class " + clsname + " not resolved by Soot"); continue;}
				callbackSootClses.add( scl );
			}
		}
		catch (Exception e) {
			System.err.println("Failed in parsing the androidCallbacks file: " + opts.catCallbackFile);
			e.printStackTrace(System.err);
			System.exit(-1);
		}
		System.out.println(callbackSootClses.size() + " callback classes loaded.");
	}
	
	public void run() {
		System.out.println("Running static analysis for event tracking instrumentation");
		//StmtMapper.getCreateInverseMap();
		if (opts.catCallbackFile==null) {
			System.err.println("callback classes not specified, cannot proceed.");
			return;
		}
		
		init();
		
		this.instMonitors();
		
		 if (opts.dumpJimple()) {
			String outapk = soot.options.Options.v().output_dir()+File.separator+utils.getAPKName()+"_JimpleInstrumented.out";
            File fJimpleInsted = new File(outapk); 
            		//new File(soot.options.Options.v().output_dir() + "JimpleInstrumented.scene.out");
            if (fJimpleInsted.exists()) {
                // remove the incomplete file possibly dumped by parent class already
                fJimpleInsted.delete();
            }
            utils.writeJimple(fJimpleInsted);
	     }
	}

    public void instMonitors() {
        /* traverse all classes */
        //Iterator<SootClass> clsIt = ProgramFlowGraph.inst().getAppClasses().iterator();// Scene.v().getApplicationClasses().iterator(); //.getClasses().iterator();
		Iterator<SootClass> clsIt = (g_instr3rdparty?Scene.v().getClasses().snapshotIterator():ProgramFlowGraph.inst().getAppClasses().iterator());
        while (clsIt.hasNext()) {
            SootClass sClass = (SootClass) clsIt.next();
            //System.out.println("class visited: " + sClass.getName());
            if ( sClass.isPhantom() ) {
                // skip phantom classes
                continue;
            }
            if (sClass.isInterface()) continue;
            if (sClass.isInnerClass()) continue;
            if ( !sClass.isApplicationClass() ) {
                // skip library classes
                continue;
            }
            
            String CallbackCls = isCallbackClass(sClass);
            boolean isComponent = false;
            if (opts.instrlifecycle()) {
            	isComponent = iccAPICom.getComponentType(sClass).compareTo("Unknown")!=0;
            }
            if (isComponent==false && CallbackCls==null) { 
            	//System.err.println(sClass.getName() + " is not a callback class."); 
            	continue; 
            }
            
            EVENTCAT ehType = null;
            if (CallbackCls!=null) {
            	System.out.println("\n" + sClass.getName() + " is a callback class of type "+ CallbackCls);
            	ehType = catCallbackClses.get(CallbackCls);
            }
            
            /* traverse all methods of the class */
            Iterator<SootMethod> meIt = sClass.getMethods().iterator();
            while (meIt.hasNext()) {
                SootMethod sMethod = (SootMethod) meIt.next();
                //System.out.println("\n method visited - " + sMethod );
                if ( !sMethod.isConcrete() ) {
                    // skip abstract methods and phantom methods, and native methods as well
                    continue; 
                }
                if ( sMethod.toString().indexOf(": java.lang.Class class$") != -1 ) {
                    // don't handle reflections now either
                    continue;
                }
                
                String lifecycleType = null;
                if (isComponent && AndroidEntryPointConstants.isLifecycleMethod(sMethod.getSubSignature())) {
               		lifecycleType = AndroidEntryPointConstants.getLifecycleType(sMethod.getSubSignature());
               		if (lifecycleType.compareTo("Unknown")==0) lifecycleType = null;
                }
                
                if (lifecycleType==null && ! (CallbackCls!=null && sMethod.getName().startsWith("on")) ) { continue; }
                
                Body body = sMethod.retrieveActiveBody();
                
                /* the ID of a method to be used for identifying and indexing a method in the event maps of EAS */
                //String meId = sClass.getName() +	"::" + sMethod.getName();
                String meId = sMethod.getSignature();
                
                PatchingChain<Unit> pchn = body.getUnits();
                
                // -- DEBUG
                if (opts.debugOut()) 
                {
                	if (opts.instrlifecycle()) {
                		System.out.println("Now instrumenting lifecycle and event-handling callback method for event tracking : " + meId + " ...");
                	}
                	else {
                		System.out.println("Now instrumenting event-handling callback method for event tracking : " + meId + " ...");
                	}
                }
                
				/* instrument method entry events at each event-handling callback */
				CFG cfg = ProgramFlowGraph.inst().getCFG(sMethod);
                
                CFGNode firstNode = cfg.getFirstRealNonIdNode()/*, firstSuccessor = cfgnodes.get(0)*/;
				Stmt firstStmt = firstNode.getStmt();

				List<Stmt> enterProbes = new ArrayList<Stmt>();
				List<StringConstant> enterArgs = new ArrayList<StringConstant>();
				
				//enterArgs.add(StringConstant.v(meId));
				enterArgs.add(StringConstant.v(sMethod.getName()));
				if (lifecycleType!=null) {
					enterArgs.add(StringConstant.v(lifecycleType)); 
				}
				else {
					if (ehType==null) {
						assert CallbackCls!=null;
						System.err.println(sMethod.getSignature());
					}
					assert ehType !=null;
					enterArgs.add(StringConstant.v(ehType.name()));
				}
				Stmt sEnterCall = Jimple.v().newInvokeStmt( Jimple.v().newStaticInvokeExpr(	mEventTracker.makeRef(), enterArgs ));
				enterProbes.add(sEnterCall);
				
				// -- DEBUG
				if (opts.debugOut()) {
					System.out.println("monitor instrumented at the beginning of method: " + meId);
				}
				if ( firstStmt != null ) {
					InstrumManager.v().insertBeforeNoRedirect(pchn, enterProbes, firstStmt);
				}
				else {
					InstrumManager.v().insertProbeAtEntry(sMethod, enterProbes);
				}
                
            } // -- while (meIt.hasNext()) 
        } // -- while (clsIt.hasNext())
       
        System.out.println("Done instrumenting all classes.");
        
    } // -- void instMonitors
	
} // -- public class sceneInstr  

/* vim :set ts=4 tw=4 tws=4 */

