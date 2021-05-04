/**
 * File: src/dynCG/covInst.java
 * -------------------------------------------------------------------------------------------
 * Date			Author      Changes
 * -------------------------------------------------------------------------------------------
 * 06/18/16		hcai		created; for monitoring user code statement coverage
 * 06/19/16		hcai		reached the working version
*/
package dynCG;

import iacUtil.*;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Iterator;
import java.util.Set;

import profile.InstrumManager;

import dua.Extension;
import dua.Forensics;
import dua.global.ProgramFlowGraph;
import dua.method.CFG;
import dua.method.CFG.CFGNode;
import dua.util.Util;
import fault.StmtMapper;

import soot.*;
import soot.jimple.*;
import soot.tagkit.Tag;
import soot.tagkit.LineNumberTag;

public class covInstr implements Extension {
	
	protected SootClass clsMonitor;
	
	protected SootMethod mSProbe;
	
	protected File fJimpleOrig = null;
	protected File fJimpleInsted = null;
	
	protected static Options opts = new Options();

	public static void main(String args[]){
		args = preProcessArgs(opts, args);

		covInstr icgins = new covInstr();
		// examine catch blocks
		dua.Options.ignoreCatchBlocks = false;
		dua.Options.skipDUAAnalysis = false;
		dua.Options.modelAndroidLC = false;
		dua.Options.analyzeAndroid = true;
		
		soot.options.Options.v().set_src_prec(soot.options.Options.src_prec_apk);
		
		//output as APK, too//-f J
		soot.options.Options.v().set_output_format(soot.options.Options.output_format_dex);
		soot.options.Options.v().set_force_overwrite(true);
		soot.options.Options.v().set_keep_line_number(true);
		
		Scene.v().addBasicClass("com.ironsource.mobilcore.BaseFlowBasedAdUnit",SootClass.SIGNATURES);
		Scene.v().addBasicClass("dynCG.covMonitor");
		
		
		Forensics.registerExtension(icgins);
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
	
	/**
	 * Descendants may want to use customized event monitors
	 */
	protected void init() {
		clsMonitor = Scene.v().getSootClass("dynCG.covMonitor");
		clsMonitor.setApplicationClass();
				
		mSProbe = clsMonitor.getMethodByName("sprobe");
	}
	
	public void run() {
		System.out.println("Running instrumentation for statement coverage monitoring...");
		//StmtMapper.getCreateInverseMap();

		instrument();
		
		if (opts.dumpJimple()) {
			String fnJimple = soot.options.Options.v().output_dir()+File.separator+utils.getAPKName()+"_JimpleInstrumented.out";
			fJimpleInsted = new File(fnJimple);
			utils.writeJimple(fJimpleInsted);
		}
	}
	
	public int getSLOC() {
		Set<Integer> lns = new HashSet<Integer>();
		/* traverse all classes */
		//Iterator<SootClass> clsIt = ProgramFlowGraph.inst().getAppClasses().iterator();
		Iterator<SootClass> clsIt = ProgramFlowGraph.inst().getUserClasses().iterator();
		while (clsIt.hasNext()) {
			SootClass sClass = (SootClass) clsIt.next();
			if ( sClass.isPhantom() ) {
				// skip phantom classes
				continue;
			}
			if ( !sClass.isApplicationClass() ) {
				// skip library classes
				continue;
			}

            //if (sClass.isInterface()) continue;
            //if (sClass.isInnerClass()) continue;
			
			/* traverse all methods of the class */
			Iterator<SootMethod> meIt = sClass.getMethods().iterator();
			while (meIt.hasNext()) {
				SootMethod sMethod = (SootMethod) meIt.next();
				if ( !sMethod.isConcrete() ) {
					// skip abstract methods and phantom methods, and native methods as well
					continue; 
				}
				if ( sMethod.toString().indexOf(": java.lang.Class class$") != -1 ) {
					// don't handle reflections now either
					continue;
				}
				
				// cannot instrument method event for a method without active body
				if ( !sMethod.hasActiveBody() ) {
					continue;
				}
				/*
				if (sMethod.isJavaLibraryMethod() || !sMethod.isDeclared() || sMethod.isNative()) {
					continue;
				}
				*/
				//if (sMethod.isConstructor() || sMethod.isStaticInitializer()) continue;
				
				/* the ID of a method to be used for identifying and indexing a method in the event maps of EAS */
				String meId = sMethod.getSignature();
				
				/* 1. instrument method entry events and program start event */
				CFG cfg = ProgramFlowGraph.inst().getCFG(sMethod);
				
				if (cfg == null || !cfg.isReachableFromEntry()) {
					System.out.println("\nSkipped method unreachable from entry: " + meId + "!");
					continue;
				}
				
				// -- DEBUG
				if (opts.debugOut()) {
					System.out.println("\nNow instrumenting method " + meId + "...");
				}

				for (CFGNode n : cfg.getNodes()) {
					Stmt s = n.getStmt();
					if (n.isInCatchBlock() || n.isSpecial() || s==null) continue;
					if (s instanceof IdentityStmt) continue;
					Tag lntag = null;
					for (Tag t : s.getTags()) {
						//System.out.println("one more tag " + t);
						if (t instanceof LineNumberTag) {
							lntag = t;
							break;
						}
					}
					if (lntag ==null) {
						continue;
					}
						
					byte[] arrln = lntag.getValue();
					int ln = ((arrln[0] & 0xff) << 8) | (arrln[1] & 0xff);
					lns.add(ln);
				}
			} // -- while (meIt.hasNext()) 
		} // -- while (clsIt.hasNext())
		return lns.size();
	}
		
	public void instrument() {
		if (opts.dumpJimple()) {
			//String fnJimple = Util.getCreateBaseOutPath() + "JimpleOrig.out";
			String fnJimple = soot.options.Options.v().output_dir()+File.separator+utils.getAPKName()+"_JimpleOrg.out";
			fJimpleOrig = new File(fnJimple);
			utils.writeJimple(fJimpleOrig);
		}
		
		if (opts.dumpFunctionList()) {
			String fnFunclist = Util.getCreateBaseOutPath() + "functionList.out";
			utils.dumpFunctionList(fnFunclist);
		}
		
		init();
		
		int sloc = getSLOC();
		System.out.println("Total Source Lines of Code in the program: " + sloc);

		/* traverse all classes */
		int cnt = 0, skipped=0;
		//Iterator<SootClass> clsIt = ProgramFlowGraph.inst().getAppClasses().iterator();
		Iterator<SootClass> clsIt = ProgramFlowGraph.inst().getUserClasses().iterator();
		while (clsIt.hasNext()) {
			SootClass sClass = (SootClass) clsIt.next();
			if ( sClass.isPhantom() ) {
				// skip phantom classes
				continue;
			}
			if ( !sClass.isApplicationClass() ) {
				// skip library classes
				continue;
			}

            //if (sClass.isInterface()) continue;
            //if (sClass.isInnerClass()) continue;
			
			
			/* traverse all methods of the class */
			Iterator<SootMethod> meIt = sClass.getMethods().iterator();
			while (meIt.hasNext()) {
				SootMethod sMethod = (SootMethod) meIt.next();
				if ( !sMethod.isConcrete() ) {
					// skip abstract methods and phantom methods, and native methods as well
					continue; 
				}
				if ( sMethod.toString().indexOf(": java.lang.Class class$") != -1 ) {
					// don't handle reflections now either
					continue;
				}
				
				// cannot instrument method event for a method without active body
				if ( !sMethod.hasActiveBody() ) {
					continue;
				}
				/*
				if (sMethod.isJavaLibraryMethod() || !sMethod.isDeclared() || sMethod.isNative()) {
					continue;
				}
				*/
				//if (sMethod.isConstructor() || sMethod.isStaticInitializer()) continue;
				
				PatchingChain<Unit> pchn = sMethod.retrieveActiveBody().getUnits();
				
				/* the ID of a method to be used for identifying and indexing a method in the event maps of EAS */
				String meId = sMethod.getSignature();
				
				/* 1. instrument method entry events and program start event */
				CFG cfg = ProgramFlowGraph.inst().getCFG(sMethod);
				
				if (cfg == null || !cfg.isReachableFromEntry()) {
					System.out.println("\nSkipped method unreachable from entry: " + meId + "!");
					continue;
				}
				
				// -- DEBUG
				if (opts.debugOut()) {
					System.out.println("\nNow instrumenting method " + meId + "...");
				}

				for (CFGNode n : cfg.getNodes()) {
					Stmt s = n.getStmt();
					if (n.isInCatchBlock() || n.isSpecial() || s==null) continue;
					if (n instanceof IdentityStmt) continue;
					Tag lntag = null;
					for (Tag t : s.getTags()) {
						if (t instanceof LineNumberTag) {
							lntag = t;
							break;
						}
					}
					if (lntag ==null) {
						//System.out.println("\nSkipped statement due to lack of linenumber tag: " + s);
						skipped ++;
						continue;
					}
					
					byte[] arrln = lntag.getValue();
					int ln = ((arrln[0] & 0xff) << 8) | (arrln[1] & 0xff);

					List<Stmt> sProbes = new ArrayList<Stmt>();
					List<IntConstant> sArgs = new ArrayList<IntConstant>();
					sArgs.add(IntConstant.v(ln));
					sArgs.add(IntConstant.v(sloc));
					Stmt sCall = Jimple.v().newInvokeStmt( Jimple.v().newStaticInvokeExpr( mSProbe.makeRef(), sArgs ));
					sProbes.add(sCall);	
					
					InstrumManager.v().insertAfter(pchn, sProbes, s);
					cnt ++;
				}
			} // -- while (meIt.hasNext()) 
		} // -- while (clsIt.hasNext())
		System.out.println("Total Jumple Lines of Code Probed: " + cnt);
		System.out.println("Total Jumple Lines of Code Skipped due to lack of source line number: " + skipped);
	} // -- void instrument
} // -- public class icgInst  

/* vim :set ts=4 tw=4 tws=4 */

