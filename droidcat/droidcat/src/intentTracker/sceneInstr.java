/**
 * File: src/intentTracker/sceneInstr.java
 * -------------------------------------------------------------------------------------------
 * Date			Author      Changes
 * -------------------------------------------------------------------------------------------
 * 09/21/15		hcai		instrument for monitoring runtime resolution of implicit ICC targets
 * 09/27/15		hcai		reached the first working version
 * 10/14/15		hcai		added intent receiving monitoring
 * 3/30/16		hcai		instrument to monitor caller and callsite for each ICC API call additionally to ease trace analysis
*/
package intentTracker;

import iacUtil.utils;
import iacUtil.iccAPICom;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import dua.Extension;
import dua.Forensics;
import dua.global.ProgramFlowGraph;
import profile.InstrumManager;
import soot.Body;
import soot.PatchingChain;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.Jimple;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;

public class sceneInstr implements Extension {
	protected SootClass clsMonitor = null;
	protected SootMethod mSendTracker = null;
	protected SootMethod mRecvTracker = null;
	
	protected static Options opts = new Options();
	
	// whether instrument in 3rd party code such as android.support.v$x 
	public static boolean g_instr3rdparty = false;
	
	public static void main(String args[]){
		args = preProcessArgs(opts, args);

		sceneInstr instr = new sceneInstr();
		// examine catch blocks
		dua.Options.ignoreCatchBlocks = false;
		dua.Options.skipDUAAnalysis = !opts.perfDUAF();
		dua.Options.modelAndroidLC = opts.modelAndroidLC();
		dua.Options.analyzeAndroid = true;
		
		soot.options.Options.v().set_src_prec(soot.options.Options.src_prec_apk);
		
		//output as APK, too//-f J
		soot.options.Options.v().set_output_format(soot.options.Options.output_format_dex);
		soot.options.Options.v().set_force_overwrite(true);
		
		//Scene.v().addBasicClass("intentTracker.Monitor",SootClass.SIGNATURES);
		Scene.v().addBasicClass("intentTracker.Monitor");
		
		Forensics.registerExtension(instr);
		Forensics.main(args);
	}
	
	protected static String[] preProcessArgs(Options _opts, String[] args) {
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

	protected void init() {
        clsMonitor = Scene.v().getSootClass("intentTracker.Monitor");
        
        /** add our runtime monitor to application class so that it can be packed together 
         * with the instrumented code into the resulting APK package
         */
        clsMonitor.setApplicationClass();
        //System.out.println("all methods in ICA.iacMonitor found: " + clsMonitor.getMethods());
		mSendTracker = clsMonitor.getMethodByName("onSendIntent");
		mRecvTracker = clsMonitor.getMethodByName("onRecvIntent");
	}
	
	public void run() {
		System.out.println("Running static analysis for intent tracking instrumentation");
		//StmtMapper.getCreateInverseMap();
		
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
            
            /*
            if (sClass.getName().contains("adlib") || sClass.getName().contains("com.google.ads")) {
            	// skip ad lib classes 
            	continue;
            }
            */
            
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
                
                // cannot instrument method event for a method without active body
                //if ( !sMethod.hasActiveBody() ) {
                //    continue;
                //}
                
                //Body body = sMethod.getActiveBody();
                Body body = sMethod.retrieveActiveBody();
                
                //if (!body.toString().contains("android.content.Intent")) {
                //	continue;
                //}
                
                /* the ID of a method to be used for identifying and indexing a method in the event maps of EAS */
                //String meId = sClass.getName() +	"::" + sMethod.getName();
                String meId = sMethod.getSignature();
                
                PatchingChain<Unit> pchn = body.getUnits();
//                CFG cfg = ProgramFlowGraph.inst().getCFG(sMethod);
//                
//                if (cfg == null || !cfg.isReachableFromEntry()) {
//                    // skip dead CFG (method)
//                    if (opts.debugOut()) {
//                        System.out.println("\nSkipped method unreachable from entry: " + meId + "!");
//                    }
//                    continue;
//                }
                
                // -- DEBUG
                if (opts.debugOut()) 
                {
                    System.out.println("\nNow instrumenting method for Intent target resolution : " + meId + "...");
                }
                
                //CombinedDUAnalysis du = new CombinedDUAnalysis(new ExceptionalUnitGraph(body));
                
                Iterator<Unit> uiter = pchn.snapshotIterator();
                
                while (uiter.hasNext()) {
                     Stmt s = (Stmt)uiter.next();
                //for (CFGNode cn : cfg.getNodes()) {
                //    if (cn.isSpecial()) {continue;}
                    //Stmt s = cn.getStmt();
                    if (iccAPICom.is_IntentSendingAPI(s)) {
                    	List<Object> itnProbes = new ArrayList<Object>();
    					List itnArgs = new ArrayList();
    					InvokeExpr inv = s.getInvokeExpr();
    					for (int idx = 0; idx < inv.getArgCount(); idx++) {
    						Value curarg = inv.getArg(idx);
    						if (curarg.getType().equals(Scene.v().getRefType("android.content.Intent"))) {
    							//itnArgs.add(curarg);
    							itnArgs.add(utils.makeBoxedValue(sMethod, curarg, itnProbes));
    							break;
    						}
    					}
    					
    					// an ICC API call without an Intent object? something wrong! bail out
    					if (itnArgs.isEmpty()) {
    						if (opts.debugOut()) {
    							System.out.println("Intent sending API call " + s + " does not take as argument an Intent...");
    						}
    						continue;
    					}
    					
    					itnArgs.add(StringConstant.v(meId));
    					itnArgs.add(StringConstant.v(s.toString()));
    					
    					Stmt sitnCall = Jimple.v().newInvokeStmt( Jimple.v().newStaticInvokeExpr(mSendTracker.makeRef(), itnArgs));
    					itnProbes.add(sitnCall);
                    	
                        // -- DEBUG
                        if (opts.debugOut()) {
                            System.out.println("probed after intent-sending API call " + s + " in method " + meId);
                        }
                        //InstrumManager.v().insertAfter(pchn, itnProbes, s);
                        InstrumManager.v().insertBeforeRedirect(pchn, itnProbes, s);
                        body.validate();
                    }
                    else if (iccAPICom.is_IntentReceivingAPI(s)) {
                    	List<Object> itnProbes = new ArrayList<Object>();
    					List itnArgs = new ArrayList();
    					AssignStmt as = (AssignStmt)s;
    					Value lv = as.getLeftOp();
    					if (lv!=null && lv.getType().equals(Scene.v().getRefType("android.content.Intent"))) {
							//itnArgs.add(lv);
							itnArgs.add(utils.makeBoxedValue(sMethod, lv, itnProbes));
						}
    					// an Intent receiving call with the receiver object not being an Intent object? something wrong! bail out
    					if (itnArgs.isEmpty()) {
    						if (opts.debugOut()) {
    							System.out.println("Intent receiving API call " + s + " does not take the return to an Intent...");
    						}
							continue;
    					}

    					itnArgs.add(StringConstant.v(meId));
    					itnArgs.add(StringConstant.v(s.toString()));

    					Stmt sitnCall = Jimple.v().newInvokeStmt( Jimple.v().newStaticInvokeExpr(mRecvTracker.makeRef(), itnArgs));
    					itnProbes.add(sitnCall);
                    	
                        // -- DEBUG
                        if (opts.debugOut()) {
                            System.out.println("probed after intent-receiving API call " + s + " in method " + meId);
                        }
                        InstrumManager.v().insertAfter(pchn, itnProbes, s);
                        
                        body.validate();
                    }
                    else {
                        continue;
                    }
                }
            } // -- while (meIt.hasNext()) 
        } // -- while (clsIt.hasNext())
       
        System.out.println("Done instrumenting all classes.");
        
    } // -- void instMonitors
	
} // -- public class sceneInstr  

/* vim :set ts=4 tw=4 tws=4 */

