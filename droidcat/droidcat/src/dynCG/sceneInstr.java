/**
 * File: src/dynCG/icgInst.java
 * -------------------------------------------------------------------------------------------
 * Date			Author      Changes
 * -------------------------------------------------------------------------------------------
 * 10/19/15		hcai		created; for profiling function calls and intent-based ICCs 
 * 10/26/15		hcai		added ICC monitoring with time-stamping call events
 * 02/14/17		hcai		added the option of event tracking
 * 02/15/17		hcai		first working version with event tracking
*/
package dynCG;

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
import dua.method.CallSite;
import dua.util.Util;

import soot.*;
import soot.jimple.*;
//import soot.tagkit.Tag;
//import soot.tagkit.VisibilityAnnotationTag;
import soot.toolkits.graph.Block;
import soot.toolkits.graph.BlockGraph;
import soot.toolkits.graph.ExceptionalBlockGraph;
import soot.util.*;
//import soot.jimple.toolkits.annotation.*;

public class sceneInstr implements Extension {
	
	protected SootClass clsMonitor;
	
	protected SootMethod mInitialize;
	protected SootMethod mEnter;
	protected SootMethod mLibCall;
	protected SootMethod mReturnInto;
	protected SootMethod mTerminate;
	
	protected File fJimpleOrig = null;
	protected File fJimpleInsted = null;
	
	protected static boolean bProgramStartInClinit = false;
	protected static Options opts = new Options();

	// whether instrument in 3rd party code such as android.support.v$x 
	public static boolean g_instr3rdparty = false;
	
	public static void main(String args[]){
		args = preProcessArgs(opts, args);

		sceneInstr icgins = new sceneInstr();
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
		Scene.v().addBasicClass("dynCG.Monitor");
		if (opts.monitorICC()) {
			Scene.v().addBasicClass("intentTracker.Monitor");
			Scene.v().addBasicClass("iacUtil.logicClock");
		}
		
		if (opts.monitorEvents()) {
			Scene.v().addBasicClass("eventTracker.Monitor");
			args = eventTracker.sceneInstr.preProcessArgs(eventTracker.sceneInstr.opts, args);
			eventTracker.sceneInstr.opts.debugOut = opts.debugOut();
			eventTracker.sceneInstr.opts.debugOut = opts.dumpJimple();
			eventTracker.sceneInstr.opts.instr3rdparty = opts.instr3rdparty();
		}
		
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
		clsMonitor = Scene.v().getSootClass("dynCG.Monitor");
		clsMonitor.setApplicationClass();
		Scene.v().getSootClass("iacUtil.MethodEventComparator").setApplicationClass();
		if (opts.monitorICC()) {
			Scene.v().getSootClass("iacUtil.logicClock").setApplicationClass();
		}
		
		if (opts.monitorEvents()) {
			Scene.v().getSootClass("eventTracker.Monitor").setApplicationClass();
		}
		
		mInitialize = clsMonitor.getMethodByName("initialize");
		mEnter = clsMonitor.getMethodByName("enter");
		mLibCall = clsMonitor.getMethodByName("libCall");
		mReturnInto = clsMonitor.getMethodByName("returnInto");
		mTerminate = clsMonitor.getMethodByName("terminate");
	}
	
	public void run() {
		System.out.println("Running static analysis for inter-app function-level profiling");
		//StmtMapper.getCreateInverseMap();

		g_instr3rdparty = opts.instr3rdparty();
		
		init();

		// monitor all method calls
		if (opts.monitorAllCalls()) {
			System.out.println("instrument 3rd party libraries as well: " + g_instr3rdparty);
			instrument();
		}
		
		// monitor all ICCs
		if (opts.monitorICC()) {
			intentTracker.sceneInstr.g_instr3rdparty = g_instr3rdparty;
			new intentTracker.sceneInstr().run();
		}
		
		if (opts.monitorEvents()) {
			new eventTracker.sceneInstr().run();
		}

		if (opts.dumpJimple()) {
			String fnJimple = soot.options.Options.v().output_dir()+File.separator+utils.getAPKName()+"_JimpleInstrumented.out";
			//String fnJimple = Util.getCreateBaseOutPath() + "JimpleInstrumented.out";
			fJimpleInsted = new File(fnJimple);
			utils.writeJimple(fJimpleInsted);
		}
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
			//utils.dumpEntryReachableFunctionList(Util.getCreateBaseOutPath() + "functionList.out");
		}
		
		//List<SootMethod> entryMes = ProgramFlowGraph.inst().getEntryMethods();
		Set<SootMethod> entryMes = utils.getEntryMethods(true);
		List<SootClass> entryClses = null;
		if (opts.sclinit()) {
			entryClses = new ArrayList<SootClass>();
			for (SootMethod m:entryMes) {
				entryClses.add(m.getDeclaringClass());
			}
		}
		
		/* traverse all classes */
		Iterator<SootClass> clsIt = (g_instr3rdparty?Scene.v().getClasses().snapshotIterator():ProgramFlowGraph.inst().getAppClasses().iterator());
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

            if (sClass.isInterface()) continue;
            if (sClass.isInnerClass()) continue;
			
			// when there is a static initializer in the entry class, we will instrument the monitor initialize in there instead of the entry method
			boolean hasCLINIT = false;
			if (opts.sclinit()) {
				if (entryClses.contains(sClass)) {
					try {
						SootMethod cl = sClass.getMethodByName("<clinit>");
						hasCLINIT = (cl != null);
					}
					catch (Exception e) {
						// if exception was thrown, either because there are more than one such method (ambiguous) or there is none
						hasCLINIT = (e instanceof RuntimeException && e.toString().contains("ambiguous method"));
					}
				}
			}
			
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
				
				//Body body = sMethod.getActiveBody();
				Body body = sMethod.retrieveActiveBody();
				
				/* the ID of a method to be used for identifying and indexing a method in the event maps of EAS */
				//String meId = sClass.getName() +	"::" + sMethod.getName();
				String meId = sMethod.getSignature();
				
				// wrap Try-Catch blocks before instrumenting EA monitors if Required
				if (opts.wrapTryCatch()) {
					TryCatchWrapper.wrapTryCatchAllBlocks(sMethod, opts.statUncaught(), opts.debugOut());
					//TryCatchWrapper.wrapTryCatchBlocks(sMethod, opts.debugOut());
				}
				
				PatchingChain<Unit> pchn = body.getUnits();
				//Chain<Local> locals = body.getLocals();
				
				/* 1. instrument method entry events and program start event */
				CFG cfg = ProgramFlowGraph.inst().getCFG(sMethod);
				
				if (cfg == null || !cfg.isReachableFromEntry()) {
					// skip dead CFG (method)
					if (opts.debugOut()) {
						System.out.println("\nSkipped method unreachable from entry: " + meId + "!");
					}
					continue;
				}
				
				// -- DEBUG
				if (opts.debugOut()) {
					System.out.println("Now instrumenting method for function-call tracking: " + meId + " ...");
				}

				//List<CFGNode> cfgnodes = cfg.getFirstRealNonIdNode().getSuccs();
				/* the first non-Identity-Statement node is right where we instrument method entrance event */
				CFGNode firstNode = cfg.getFirstRealNonIdNode()/*, firstSuccessor = cfgnodes.get(0)*/;
				Stmt firstStmt = firstNode.getStmt();
				/*
				List<CFGNode> cfgnodes = cfg.getNodes();
				for (CFGNode curnode : cfgnodes) {
					if (curnode.isSpecial()) {
						// reached the EXIT node of the CFG
						break;
					}
					Stmt stmt = curnode.getStmt();
					if (stmt instanceof IdentityStmt) {
						// cannot instrument before Id stmt
						continue;
					}
				}
				*/

				List<Stmt> enterProbes = new ArrayList<Stmt>();
				List<StringConstant> enterArgs = new ArrayList<StringConstant>();
				
				boolean bInstProgramStart = false;
				if (opts.sclinit()) {
					bInstProgramStart = (hasCLINIT && sMethod.getName().equalsIgnoreCase("<clinit>")) || 
														(!hasCLINIT && entryMes.contains(sMethod));
					if (!bProgramStartInClinit) {
						bProgramStartInClinit = (hasCLINIT && sMethod.getName().equalsIgnoreCase("<clinit>"));
					}
				}
				else {
					bInstProgramStart = entryMes.contains(sMethod);
				}
				
				// when "-sclinit" option specified, instrument monitor initialize in <clinit> if any, otherwise in entry method
				if ( bInstProgramStart ) {
					// before the entry method executes, the monitor needs be initialized
					List<StringConstant> initializeArgs = new ArrayList<StringConstant>();
					Stmt sInitializeCall = Jimple.v().newInvokeStmt( Jimple.v().newStaticInvokeExpr(
							mInitialize.makeRef(), initializeArgs ));
					enterProbes.add(sInitializeCall);
					
					// -- DEBUG
					if (opts.debugOut()) {
						System.out.println("monitor initialization instrumented at the beginning of Entry method: " + meId);
					}
				} // -- if (entryMes.contains(sMethod))
				
				enterArgs.add(StringConstant.v(meId));
				Stmt sEnterCall = Jimple.v().newInvokeStmt( Jimple.v().newStaticInvokeExpr(
						mEnter.makeRef(), enterArgs ));
				enterProbes.add(sEnterCall);
				
				// -- DEBUG
				if (opts.debugOut()) {
					System.out.println("monitor enter instrumented at the beginning of application method: " + meId);
				}
				if ( firstStmt != null ) {
					InstrumManager.v().insertBeforeNoRedirect(pchn, enterProbes, firstStmt);
				}
				else {
					InstrumManager.v().insertProbeAtEntry(sMethod, enterProbes);
				}
				
				/* 2. instrument method returned-into events and program termination event */
				/*
				List<Stmt> retinProbes = new ArrayList<Stmt>();
				List<StringConstant> retinArgs = new ArrayList<StringConstant>();
				retinArgs.add(StringConstant.v(meId));
				Stmt sReturnIntoCall = Jimple.v().newInvokeStmt( Jimple.v().newStaticInvokeExpr(
						mReturnInto.makeRef(), retinArgs ));
				retinProbes.add(sReturnIntoCall);
				*/
				
				/*
				for (CFGNode curnode : cfgnodes) {
					Stmt stmt = curnode.getStmt();
					
					// 2.1: normal returns
					if (curnode.hasAppCallees()) {
						assert stmt instanceof InvokeStmt;
						
						InstrumManager.v().insertAfter(pchn, retinProbes, stmt);
					}
					
					// 2.2: in a catch block
					else if (curnode.isInCatchBlock()) {
						Block bb = Util.getBB(stmt);
						
						InstrumManager.v().insertAfter(pchn, retinProbes, (Stmt)bb.getHead());
					}
					// 2.3: in a finally block
				}
				*/
				
				// 2.1 normal returns from call sites OUTSIDE any traps (exception catchers) 
				List<CallSite> callsites = cfg.getCallSites();
				int nCandCallsites = 0;
				for (CallSite cs : callsites) {
					if (cs.hasAppCallees() && !cs.isInCatchBlock()) {
						nCandCallsites ++;
					}
					
					/** instrument 'terminate' monitor before 'System.exit' as a special treatment for Java programs */
					for (SootMethod ce : cs.getAllCallees()) {
						if (!ce.getSignature().equals("<java.lang.System: void exit(int)>")) {
							continue;
						}
						List<Stmt> terminateProbe = new ArrayList<Stmt>();
						List<StringConstant> terminateArgs = new ArrayList<StringConstant>();
						terminateArgs.add(StringConstant.v("terminate before System.exit"));
						Stmt sTerminateCall = Jimple.v().newInvokeStmt( Jimple.v().newStaticInvokeExpr(
								mTerminate.makeRef(), terminateArgs ));
						terminateProbe.add(sTerminateCall);
						// -- DEBUG
						if (opts.debugOut()) {
							System.out.println("monitor termination instrumented at the end of " + 
									meId + " before System.exit");
						}
						InstrumManager.v().insertBeforeRedirect (pchn, terminateProbe, cs.getLoc().getStmt());
					}
				}
				boolean havingFinally = false;
				String bodySource = body.toString();
				if (bodySource.contains("throw") && 
					bodySource.contains(":= @caughtexception") &&
					bodySource.contains("catch java.lang.Throwable from")) {
					// a very naive, probably imprecise but safe, way to determine if there is any finally block in this method
					havingFinally = true;
					if (opts.debugOut) {
						System.out.println("finally block exists in method: " + meId);
					}
				}
				
				int nCurCallsite = 0;
				for (CallSite cs : callsites) {
					List<Stmt> retinProbes = new ArrayList<Stmt>();
					if (!cs.hasAppCallees()) {
						/** for android analysis, we want to capture framework API calls as well 
						 * however, since we cann't instrument entry event in such methods, we need 
						 * instrument the entry event for the library call immediately before the returned-into event
						 * to keep the current Monitor design: need pair of such events to deduce call graph edges 
						 */
						List<StringConstant> enterlibcallArgs = new ArrayList<StringConstant>();
						assert cs.getAllCallees().size()>=1;
						enterlibcallArgs.add(StringConstant.v(meId));
						enterlibcallArgs.add(StringConstant.v(//cs.getAllCallees().get(0).getSignature()));
							cs.hashCode() + ": returnInto after calling " + cs.getAllCallees().get(0).getSignature()));
						Stmt sEnterLibCall = Jimple.v().newInvokeStmt( Jimple.v().newStaticInvokeExpr(
								mLibCall.makeRef(), enterlibcallArgs ));
						retinProbes.add(sEnterLibCall);
						if (opts.debugOut()) {
							System.out.println("monitor libCall instrumented at library call site " +
									cs + " in method: " + meId);
						}
						InstrumManager.v().insertAfter(pchn, retinProbes, cs.getLoc().getStmt());
						// only care about application calls
						continue;
					}
					
					if (cs.isInCatchBlock()) {
						/*
						// for catch blocks, we always instrument at the beginning of each block, without looking into
						// the call sites inside the block
						continue;
						*/
						if (opts.debugOut()) {
							System.out.println("[To instrument callsite in a Catch Block]");
						}
					}
					else {
						nCurCallsite ++;
					}
					// -- DEBUG
					if (opts.debugOut()) {
						System.out.println("monitor returnInto instrumented at call site " +
								cs + " in method: " + meId);
					}
					
					List<StringConstant> retinArgs = new ArrayList<StringConstant>();
					retinArgs.add(StringConstant.v(meId));
					retinArgs.add(StringConstant.v(//cs.getLoc().toString()
							cs.hashCode() + ": returnInto after calling " + cs.getAllCallees().get(0).getSignature()));
					Stmt sReturnIntoCall = Jimple.v().newInvokeStmt( Jimple.v().newStaticInvokeExpr(
							mReturnInto.makeRef(), retinArgs ));
					retinProbes.add(sReturnIntoCall);
					
					// before the entry method finishes, method events are to be dumped
					// -- the exit point of the entry method is regarded as the program end point

					// if there is no any finally block in the entry method, we probe for monitoring program termination after the last call site
					if (!havingFinally && nCurCallsite==nCandCallsites && entryMes.contains(sMethod)) {	
						List<StringConstant> terminateArgs = new ArrayList<StringConstant>();
						terminateArgs.add(StringConstant.v(/*cs.getLoc().toString()*/
								cs.hashCode() + ": terminate after calling " + cs.getAppCallees().get(0).getSignature()/*.getName()*/));
						Stmt sTerminateCall = Jimple.v().newInvokeStmt( Jimple.v().newStaticInvokeExpr(
								mTerminate.makeRef(), terminateArgs ));
						//retinProbes.add(sTerminateCall);
						
					    // -- DEBUG
						if (opts.debugOut()) {
							System.out.println("monitor terminate instrumented at last app call site " +
									cs + " in method: " + meId);
						}
					}
					InstrumManager.v().insertAfter(pchn, retinProbes, cs.getLoc().getStmt());
					/*
					InstrumManager.v().insertAtProbeBottom(pchn, retinProbes, cs.getLoc().getStmt());
					InstrumManager.v().insertRightBeforeNoRedirect(pchn, retinProbes, cs.getLoc().getStmt());
					*/
				}
				
				BlockGraph bg = new ExceptionalBlockGraph(body);
				//List<Block> exitBlocks = bg.getTails();
				Map<Stmt, Stmt> cbExitStmts = new LinkedHashMap<Stmt, Stmt>(); // a map from head to exit of each catch block
				for (Block block : bg.getBlocks()) {
					//block.getTail();
					Stmt head = (Stmt)block.getHead(), curunit = head, exit = curunit;
					//if ( ProgramFlowGraph.inst().getNode((head)).isInCatchBlock() ) { // exception blocks are not handled in DuaF for now
					if ( head.toString().contains(":= @caughtexception") ) {	
						while (curunit != null) {
							exit = curunit;
							curunit = (Stmt)block.getSuccOf(curunit);
						}
						cbExitStmts.put(head, exit);
					}
				}
				
				/*
				UnitGraph ug = new ExceptionalUnitGraph(body);
				List<Unit> exitUnits = ug.getTails();
				for (Unit exitPnt : exitUnits) {
					// -- DEBUG
					if (opts.debugOut()) {
						System.out.println("monitor returnedInto instrumented at " + 
								exitPnt + " in method: " + meId);
					}
					
					InstrumManager.v().insertBeforeNoRedirect(pchn, retinProbes, (Stmt)exitPnt);
				}
				*/
								
				// 2.2 catch blocks and finally blocks
				Chain<Trap> traps = body.getTraps();
				if (traps.size() < 1) {
					// no catchers, no catch block instrumentation
					//continue;
				}
				
				/*
				for (Unit u : pchn) {
					Stmt s = (Stmt)u;
					for (Tag tag : s.getTags()) {
						if (tag instanceof LineNumberTag) {
							LineNumberTag ltag = (LineNumberTag) tag;
							ltag.getLineNumber();
							// we could read the source file using this source code line number
							if (mSource.contains("try") && mSource.contains("finally")) {
					
							}
						}
					}
				}
				*/
				
				// record instrumentation targets to avoid repetitive instrumentation
				Set<Unit> instTargets = new LinkedHashSet<Unit>();
				for (Iterator<Trap> trapIt = traps.iterator(); trapIt.hasNext(); ) {
				    Trap trap = trapIt.next();

				    // instrument at the beginning of each catch block
				    // -- DEBUG
					if (opts.debugOut()) {
						System.out.println("monitor returnInto instrumented at the beginning of catch block " +
								trap + " in method: " + meId);
					}
					
					List<Stmt> retinProbes = new ArrayList<Stmt>();
					List<StringConstant> retinArgs = new ArrayList<StringConstant>();
					retinArgs.add(StringConstant.v(meId));
					retinArgs.add(StringConstant.v(/*trap.toString()*/
							trap.hashCode() + ": returnInto in catch block for " + trap.getException().getName()));
					Stmt sReturnIntoCall = Jimple.v().newInvokeStmt( Jimple.v().newStaticInvokeExpr(
							mReturnInto.makeRef(), retinArgs ));
					retinProbes.add(sReturnIntoCall);
					
					/*
					if (entryMes.contains(sMethod)) {	
						List<StringConstant> terminateArgs = new ArrayList<StringConstant>();
						terminateArgs.add(StringConstant.v(trap.toString()));
						Stmt sTerminateCall = Jimple.v().newInvokeStmt( Jimple.v().newStaticInvokeExpr(
								mTerminate.makeRef(), terminateArgs ));
						retinProbes.add(sTerminateCall);
						// -- DEBUG
						if (opts.debugOut()) {
							System.out.println("monitor terminate instrumented at catch block " +
									trap + " in method: " + meId);
						}
					}
					*/
					
				    //InstrumManager.v().insertBeforeNoRedirect(pchn, retinProbes, (Stmt)trap.getHandlerUnit());
					Stmt instgt = (Stmt)trap.getHandlerUnit();
					if (!instTargets.contains(instgt)) {
						InstrumManager.v().insertAfter (pchn, retinProbes, instgt);
						instTargets.add(instgt);
					}
					
					// instrument at the beginning of each finally block if any
					if (havingFinally) {
						if (trap.getHandlerUnit().equals(trap.getEndUnit())) {
							// we don't need instrument the probes at the same point twice
							// -- note that for a same TRY statement, the beginning of the finally block, which is trap.getEndUnit(),
							// might be equal to the beginning of a catch block
							continue;
						}
						/* whenever there is a finally block for a TRY statement (maybe without any catch block),
						 *   the block will be copied to the end of every trap; So we can just instrument at the end of
						 *   each of these traps to meet the requirement of instrumenting in each finally block
						 */
						List<Stmt> retinProbesF = new ArrayList<Stmt>();
						List<StringConstant> retinArgsF = new ArrayList<StringConstant>();
						retinArgsF.add(StringConstant.v(meId));
						retinArgsF.add(StringConstant.v(/*trap.toString() */
								trap.getEndUnit().hashCode() + ": returnInto in finally block for " + trap.getException().getName()));
						Stmt sReturnIntoCallF = Jimple.v().newInvokeStmt( Jimple.v().newStaticInvokeExpr(
								mReturnInto.makeRef(), retinArgsF ));
						retinProbesF.add(sReturnIntoCallF);
						// -- DEBUG
						if (opts.debugOut()) {
							System.out.println("monitor returnInto instrumented at the beginning of the finally block " +
									trap.getEndUnit() + " in method: " + meId);
						}
						/* -- when there is a finally block in the entry method, it is complicated to place the probe for the 
						 * termination event correctly so that it is to be always the actual terminating point and, to be 
						 * invoked exactly once -- we need then instrument at the end of the last finally block if it is at the
						 * end of the method (namely no app calls afterwards)
						 *  
						if (entryMes.contains(sMethod)) {
							List<StringConstant> terminateArgsF = new ArrayList<StringConstant>();
							terminateArgsF.add(StringConstant.v("In finally block: " + trap.getEndUnit().toString()));
							Stmt sTerminateCallF = Jimple.v().newInvokeStmt( Jimple.v().newStaticInvokeExpr(
									mTerminate.makeRef(), terminateArgsF ));
							retinProbesF.add(sTerminateCallF);
							// -- DEBUG
							if (opts.debugOut()) {
								System.out.println("monitor terminate instrumented at finally block " +
										trap.getEndUnit() + " in method: " + meId);
							}
						}
						*/
						// again, avoid repetitive instrumentation although it does not affect the algorithm's correctness of EAS
						/** BUT IT DOES AFFECT Diver which uses full sequence of method events, at least for its current design */
						if (!((Stmt)trap.getEndUnit()).toString().contains("Monitor: void returnInto")) {
							InstrumManager.v().insertBeforeRedirect (pchn, retinProbesF, (Stmt)trap.getEndUnit());
						}
					}
					else if (entryMes.contains(sMethod)) {
						// if there is no finally block in the entry method, we can safely insert the probe for termination event
						// after the end of each catch block
						List<Stmt> terminateProbes = new ArrayList<Stmt>();
						List<StringConstant> terminateArgs = new ArrayList<StringConstant>();
						terminateArgs.add(StringConstant.v(/*trap.toString()*/
								trap.hashCode() + ": terminate in catch block for " + trap.getException().getName()));
						Stmt sTerminateCall = Jimple.v().newInvokeStmt( Jimple.v().newStaticInvokeExpr(
								mTerminate.makeRef(), terminateArgs ));
						terminateProbes.add(sTerminateCall);
						// -- DEBUG
						if (opts.debugOut()) {
							System.out.println("monitor terminate instrumented after the end of catch block " +
									trap + " in method: " + meId);
						}
						// if a monitor probe has already become the end unit, we should insert this termination probe after that point
						if (((Stmt)trap.getEndUnit()).toString().contains("Monitor: void returnInto")) {
							//InstrumManager.v().insertAfter (pchn, terminateProbes, (Stmt)trap.getEndUnit());
						}
						// otherwise, just insert right before the last unit of the catch block
						else {
							/*
							//Stmt tgt = (Stmt)trap.getUnitBoxes().get(trap.getUnitBoxes().size()-1).getUnit();
							//InstrumManager.v().insertBeforeRedirect (pchn, terminateProbes, tgt);
							Stmt tgt = (Stmt)trap.getEndUnit();
							InstrumManager.v().insertRightBeforeNoRedirect (pchn, terminateProbes, tgt);
							*/
							Stmt cbHead = (Stmt)trap.getHandlerUnit(); // the handler unit is the beginning unit of the catch block
							assert cbExitStmts.containsKey(cbHead);
							Stmt tgt = cbExitStmts.get(cbHead);
							if (tgt instanceof GotoStmt /*&& !(tgt instanceof IdentityStmt)*/) {
								//InstrumManager.v().insertBeforeRedirect (pchn, terminateProbes, tgt);
							}
							else {
								//InstrumManager.v().insertAfter (pchn, terminateProbes, tgt);
							}
						}
					}
				}
				
				// Thus far, we have not insert probe for termination event in the entry method if there is any finally blocks in there
				if (entryMes.contains(sMethod) /*&& havingFinally*/) {
					Set<Stmt> fTargets = new LinkedHashSet<Stmt>();
					Stmt lastThrow = null;
					for (Unit u : pchn) {
						Stmt s = (Stmt)u;
						if (havingFinally) {
							// if the finally block is to be executed after an uncaught exception was thrown, a throw statement 
							// will be the exit point; However, we only probe at the last such finally block if there is no any app call site following it  
							if (s instanceof ThrowStmt) {
								//System.out.println("Got a throw statement " + s);
								lastThrow = s;
							}
							if (lastThrow != null) {
								try {
									// ProgramFlowGraph has no way to collect the nodes in the catch block we possibly added for outermost try-catch
									// block wrapping
									if (ProgramFlowGraph.inst().getNode(s).hasAppCallees()) {
										lastThrow = null;
									}
								}catch (Exception e) {}
							}
						}
						
						// In Jimple IR, any method has exactly one return statement; unless it ends with an infinite loop (like service daemon)
						if (dua.util.Util.isReturnStmt(s)) {
							fTargets.add(s);
						}

						/* Try statement and Finally block identification are  NOT supported with Jimple IR 
						if (s instanceof TryStmt) {
							System.out.println("Got a Try statement " + s);
						}
						if (s instanceof FinallyHost) {
							System.out.println("Got a Finally block " + s);
						}
						*/
					}
					if (lastThrow != null) {
						//fTargets.add(lastThrow);
					}
					Stmt laststmt = (Stmt)pchn.getLast();
					if (!fTargets.contains(laststmt)) {
						fTargets.add(laststmt);
					}
					if (fTargets.size() < 1) {
						System.out.println("WARNING: no return statement found in method: " + meId);
					}
					// assert fTargets.size() >= 1;
					for (Stmt tgt : fTargets) {
						List<Stmt> terminateProbe = new ArrayList<Stmt>();
						List<StringConstant> terminateArgs = new ArrayList<StringConstant>();
						String flag =  (dua.util.Util.isReturnStmt(tgt)?"return":"throw") + " statement";
						terminateArgs.add(StringConstant.v("terminate before " + flag));
						Stmt sTerminateCall = Jimple.v().newInvokeStmt( Jimple.v().newStaticInvokeExpr(
								mTerminate.makeRef(), terminateArgs ));
						terminateProbe.add(sTerminateCall);
						// -- DEBUG
						if (opts.debugOut()) {
							System.out.println("monitor termination instrumented at the end of " + 
									meId + " before " + flag);
						}
						InstrumManager.v().insertBeforeRedirect (pchn, terminateProbe, tgt);
					}
				}
				
			} // -- while (meIt.hasNext()) 
			
		} // -- while (clsIt.hasNext())
		
		// important warning concerning the later runs of the instrumented subject
		if (bProgramStartInClinit) {
			System.out.println("\n***NOTICE***: program start event probe has been inserted in EntryClass::<clinit>(), " +
					"the instrumented subject MUST thereafter run independently rather than via EARun!");
		}
	} // -- void instrument
	
} // -- public class icgInst  

/* vim :set ts=4 tw=4 tws=4 */

