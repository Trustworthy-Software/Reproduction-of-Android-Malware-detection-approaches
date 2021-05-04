/**
 * File: src/intentTracker/bodyInstr.java
 * -------------------------------------------------------------------------------------------
 * Date			Author      Changes
 * -------------------------------------------------------------------------------------------
 * 09/27/15		hcai		instrument for monitoring runtime resolution of implicit ICC targets
 * 09/28/15		hcai		reached the first working version
*/
package intentTracker;

import iacUtil.iccAPICom;
import iacUtil.utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


import profile.InstrumManager;
import soot.Body;
import soot.PatchingChain;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Transform;
import soot.Unit;
import soot.Value;
import soot.jimple.AbstractStmtSwitch;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.Stmt;

import soot.BodyTransformer;
import soot.PackManager;

public class bodyInstr extends BodyTransformer {
	protected SootClass clsMonitor = null;
	protected SootMethod mTracker = null;
	protected static Options opts = new Options();
    static BufferedWriter g_jimplewriter = null;

	public static void main(String args[]) throws IOException {
		args = preProcessArgs(opts, args);

		soot.options.Options.v().set_src_prec(soot.options.Options.src_prec_apk);
		//soot.options.Options.v().set_force_android_jar("android.jar");
		//output as APK, too//-f J
		soot.options.Options.v().set_output_format(soot.options.Options.output_format_dex);
		//List<String> dirs = new ArrayList<String>();
		//dirs.add("input/AppClude.ShakingFlashLight.apk");
		//soot.options.Options.v().set_process_dir(dirs);
		soot.options.Options.v().set_allow_phantom_refs(true);
		soot.options.Options.v().set_force_overwrite(true);
		
		//Scene.v().addBasicClass("intentTracker.Monitor",SootClass.SIGNATURES);
		Scene.v().addBasicClass("intentTracker.Monitor");
		bodyInstr instr = new bodyInstr();
		System.out.println("Running static analysis for intent tracking instrumentation");
		PackManager.v().getPack("jtp").add(new Transform("jtp.iacInstrumenter", instr));
		soot.Main.main(args);
		
        System.out.println("Done instrumenting all classes.");
        if (opts.dumpJimple()) {
        	g_jimplewriter.flush();
        	g_jimplewriter.close();
        	System.out.println("Done dumping instrumented Jimple code.");
        }
	}
	
	protected static String[] preProcessArgs(Options _opts, String[] args) {
		opts = _opts;
		args = opts.process(args);
		return args;
	}

	protected void init() {
        clsMonitor = Scene.v().getSootClass("intentTracker.Monitor");
        /** add our runtime monitor to application class so that it can be packed together 
         * with the instrumented code into the resulting APK package
         */
        clsMonitor.setApplicationClass();
        
        // System.out.println("all methods in ICA.iacMonitor found: " + clsMonitor.getMethods());
		mTracker = clsMonitor.getMethodByName("onSendIntent");
	}

	@Override
	protected void internalTransform(Body b, String phaseName, Map<String, String> options) {
		if (opts.dumpJimple() && null == g_jimplewriter) {
            File fJimpleInsted = new File(soot.options.Options.v().output_dir()+File.separator+utils.getAPKName()+"_JimpleInstrumented.out");
            if (fJimpleInsted.exists()) {
                fJimpleInsted.delete();
            }
            try {
				g_jimplewriter = new BufferedWriter(new FileWriter(fJimpleInsted));
			} catch (IOException e) {
				e.printStackTrace();
			}
        }
		
        if (!b.toString().contains("android.content.Intent")) {
        	return;
        }
		SootMethod sMethod = b.getMethod();
		SootClass sClass = sMethod.getDeclaringClass();
		
		//System.out.println("class visited: " + sClass.getName());
        if ( sClass.isPhantom() ) {
            // skip phantom classes
            return;
        }
        if ( !sClass.isApplicationClass() ) {
            // skip library classes
        	return;
        }
        
        if (sClass.getName().contains("adlib") || sClass.getName().contains("com.google.ads")) {
        	// skip ad lib classes 
        	return;
        }
        
        //System.out.println("\n method visited - " + sMethod );
        if ( !sMethod.isConcrete() ) {
            // skip abstract methods and phantom methods, and native methods as well
        	return; 
        }
        if ( sMethod.toString().indexOf(": java.lang.Class class$") != -1 ) {
            // don't handle reflections now either
        	return;
        }
        
        // cannot instrument method event for a method without active body
        //if ( !sMethod.hasActiveBody() ) {
        //    continue;
        //}
        
        /* the ID of a method to be used for identifying and indexing a method in the event maps of EAS */
        //String meId = sClass.getName() +	"::" + sMethod.getName();
        String meId = sMethod.getSignature();
        
        PatchingChain<Unit> pchn = b.getUnits();
        
        // -- DEBUG
        if (opts.debugOut()) 
        {
            System.out.println("\nNow instrumenting method for Intent target resolution : " + meId + "...");
        }
        
        // performance may be different as per this option
        boolean fullDIY = System.getProperty("fullDIY")!=null && System.getProperty("fullDIY").equalsIgnoreCase("true");
		
        if (fullDIY) {
		    Iterator<Unit> uiter = pchn.snapshotIterator();
		    while (uiter.hasNext()) {
		        Stmt s = (Stmt)uiter.next();
		        if (!iccAPICom.is_IntentSendingAPI(s)) {
		        	continue;
		        }
		        List<Unit> probes = prepareProbe(b, s);
		        if (!probes.isEmpty()) {
		        	InstrumManager.v().insertAfter(b.getUnits(), probes, s);
		        	b.validate();
		        }
		    }
        }
        else {
        	transformBody(b);
        }
        
        if (g_jimplewriter != null) {
        	try {
        		g_jimplewriter.write("\t"+sClass.getName()+"\n");
				g_jimplewriter.write(b + "\n");
			} catch (IOException e) {
				e.printStackTrace();
			}
        }
	}
	
	private void transformBody(final Body b) {
		 PatchingChain<Unit> pchn = b.getUnits();
		 Iterator<Unit> uiter = pchn.snapshotIterator();
	     while (uiter.hasNext()) {
             final Stmt stmt = (Stmt)uiter.next();
             stmt.apply(new AbstractStmtSwitch() {
            	 public void caseInvokeStmt(InvokeStmt s) {
            		 if (!iccAPICom.is_IntentSendingAPI(stmt)) {
                    	 return;
        		     }
            		 List<Unit> probes = prepareProbe(b, s);
            		 if (!probes.isEmpty()) {
            			 // -- DEBUG
            			 if (opts.debugOut()) {
            				 System.out.println("probed after intent-based ICC API call " + s + " in method " + b.getMethod().getSignature());
            			 }
            			 b.getUnits().insertAfter(probes, stmt);
            			 b.validate();
            		 }
            	 }
             });
	     }
	}
	
	private List<Unit> prepareProbe(Body b, Stmt s) {
		List<Unit> itnProbes = new ArrayList<Unit>();
		List<Value> itnArgs = new ArrayList<Value>();
		InvokeExpr inv = s.getInvokeExpr();
		for (int idx = 0; idx < inv.getArgCount(); idx++) {
			Value curarg = inv.getArg(idx);
			if (curarg.getType().equals(Scene.v().getRefType("android.content.Intent"))) {
				//itnArgs.add(curarg);
				itnArgs.add(utils.makeBoxedValue(b.getMethod(), curarg, itnProbes));
				break;
			}
		}
		
		// an ICC API call without an Intent object? something wrong! bail out
		if (itnArgs.isEmpty()) {
			if (opts.debugOut()) {
				System.out.println("ICC API call " + s + " does not take as argument an Intent...");
				return itnProbes;
			}
		}
		
		if (mTracker == null) {
			this.init();
		}
		
		Stmt sitnCall = Jimple.v().newInvokeStmt( Jimple.v().newStaticInvokeExpr(mTracker.makeRef(), itnArgs));
		itnProbes.add(sitnCall);
        return itnProbes;
	}
} // -- public class bodyInstr  

/* vim :set ts=4 tw=4 tws=4 */

