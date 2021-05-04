package edu.uci.seal.cases.analyses;


import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uci.seal.Config;
import edu.uci.seal.StopWatch;
import edu.uci.seal.Utils;
import edu.uci.seal.analyses.ApkSceneTransformer;
import soot.Body;
import soot.Hierarchy;
import soot.Local;
import soot.PackManager;
import soot.PatchingChain;
import soot.Scene;
import soot.SceneTransformer;
import soot.SootClass;
import soot.SootMethod;
import soot.Transform;
import soot.Unit;
import soot.ValueBox;
import soot.jimple.ClassConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.StaticInvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.jimple.VirtualInvokeExpr;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JInvokeStmt;
import soot.jimple.internal.JNewExpr;
import soot.jimple.toolkits.annotation.logic.Loop;
import soot.jimple.toolkits.callgraph.CHATransformer;
import soot.options.Options;
import soot.tagkit.LoopInvariantTag;
import soot.tagkit.Tag;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.LoopNestTree;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.SimpleLocalDefs;

public class FieldReflectCountTransformer extends ApkSceneTransformer {
	
	Logger logger = LoggerFactory.getLogger(FieldReflectCountTransformer.class);
	private static int totalFieldReflectAccessCount = 0;
		
	public FieldReflectCountTransformer(String apkFilePath) {
		super(apkFilePath);
	}


	@Override
	protected void internalTransform(String phaseName, Map<String, String> options) {
		Utils.setupDummyMainMethod();
		
		logger.debug("Constructing call graph...");
		CHATransformer.v().transform();
		logger.debug("Call graph built");
		
		List<SootMethod> rtoMethods = Utils.getMethodsInReverseTopologicalOrder();

		int currMethodCount = 1;
		logger.debug("total number of possible methods to analyze: " + rtoMethods.size());
		for (SootMethod method : rtoMethods) {
			logger.trace("Checking if I should analyze method: " + method);
			if (Utils.isApplicationMethod(method)) {
				if (method.hasActiveBody()) {
					doAnalysisOnMethod(method);
				}
				else {
					logger.debug("method " + method + " has no active body, so it's won't be analyzed.");
				}
				
				if (Thread.interrupted()) {
					try {
						throw new InterruptedException();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						return;
					}
				}
				
			}
			logger.trace("Finished loop analysis on method: " + method);
			logger.trace("Number of methods analyzed: " + currMethodCount);
			currMethodCount++;
		}
	}
	
	private void doAnalysisOnMethod(SootMethod method) {
		logger.debug("Analyzing method " + method.getSignature());
		
		int perMethodFieldReflectAccessCount = 0;
		if (method.hasActiveBody()) {
			Body body = method.getActiveBody();
			PatchingChain<Unit> units = body.getUnits();
			
			UnitGraph unitGraph = new BriefUnitGraph(body);
			SimpleLocalDefs defs = new SimpleLocalDefs(unitGraph);
			
			LoopNestTree loopNestTree = new LoopNestTree(body);
			for (Loop loop : loopNestTree) {
				logger.debug("Has loop with head: " + loop.getHead());
				for (Stmt stmt : loop.getLoopStatements()) {
					for (Tag tag: stmt.getTags()) {
						if (tag instanceof LoopInvariantTag) {
							logger.debug("\tloop invariant: " + stmt);
						}
					}
				}
			}
			
			for (Unit unit : units) {
				if (unit instanceof JAssignStmt) {
					JAssignStmt assignStmt = (JAssignStmt)unit;
					if (!(assignStmt.getRightOp() instanceof VirtualInvokeExpr)) {
						continue;
					}
					VirtualInvokeExpr invokeExpr = (VirtualInvokeExpr)assignStmt.getRightOp();
					perMethodFieldReflectAccessCount = doFieldReflectAnalysis(invokeExpr,perMethodFieldReflectAccessCount,assignStmt,defs);
				}
				else if (unit instanceof JInvokeStmt) {
					JInvokeStmt invokeStmt = (JInvokeStmt)unit;
					if ( checkForReflectInvocation(invokeStmt.getInvokeExpr()) ) {
						if ( invokeStmt.getInvokeExpr() instanceof VirtualInvokeExpr ) {
							logger.debug("Found reflective field access in JInvokeStmt");
							VirtualInvokeExpr invokeExpr = (VirtualInvokeExpr)invokeStmt.getInvokeExpr();
							perMethodFieldReflectAccessCount = doFieldReflectAnalysis(invokeExpr,perMethodFieldReflectAccessCount,invokeStmt,defs);
						}
					}
				}
			}
		}
		
		totalFieldReflectAccessCount += perMethodFieldReflectAccessCount;
	}
	
	private int doFieldReflectAnalysis(VirtualInvokeExpr invokeExpr, int perMethodFieldReflectAccessCount, Stmt fieldReflectStmt, SimpleLocalDefs defs) {
		if ( checkForReflectInvocation(invokeExpr) ) {
			perMethodFieldReflectAccessCount++;
			if (!(invokeExpr.getBase() instanceof Local)) {
				return perMethodFieldReflectAccessCount;
			}
			Local fieldLocal = (Local)invokeExpr.getBase();
			List<Unit> fieldDefUnits = defs.getDefsOfAt(fieldLocal, fieldReflectStmt);
			
			for (Unit fieldDefUnit : fieldDefUnits) {
				if (!(fieldDefUnit instanceof JAssignStmt)) {
					continue;
				}
				JAssignStmt fieldAssignStmt = (JAssignStmt)fieldDefUnit;
				if (!(fieldAssignStmt.getRightOp() instanceof VirtualInvokeExpr)) {
					continue;
				}
				VirtualInvokeExpr getFieldExpr = (VirtualInvokeExpr)fieldAssignStmt.getRightOp();
				StringConstant fieldNameConst = null;
				
				boolean foundReflectiveFieldAccess = false;
				boolean foundClassNameOfFieldAccess = false;
				if (getFieldExpr.getMethod().getDeclaringClass().getName().equals("java.lang.Class")) {
					if (MethodConstants.reflectiveGetFieldMethodsSet.contains(getFieldExpr.getMethod().getName())) {
						if ( getFieldExpr.getArg(0) instanceof StringConstant ) {
							fieldNameConst = (StringConstant)getFieldExpr.getArg(0);
							logger.debug("The following field is accessed reflectively: " + fieldNameConst);
							foundReflectiveFieldAccess = true;
						} else {
							logger.warn("The following reflective field access does not use a StringConstant: " + getFieldExpr);
						}
					}
				}
				
				if (fieldNameConst == null) {
					logger.warn("Could not find string for reflective access of field here.");
					continue;
				}
				
				if (!(getFieldExpr.getBase() instanceof Local)) {
					continue;
				}
				Local classLocal = (Local)getFieldExpr.getBase();
				List<Unit> classDefs = defs.getDefsOfAt(classLocal, fieldAssignStmt);
				for (Unit classDef : classDefs) {
					if (!(classDef instanceof JAssignStmt)) {
						continue;
					}
					JAssignStmt classAssignStmt = (JAssignStmt)classDef;
					Set<String> viClassNames = handleVirtualInvoke(defs, classAssignStmt);
					Set<String> siClassNames = handleStaticInvoke(defs, classAssignStmt); 
					Set<String> nsClassNames = handleNonStandardCases(defs, classAssignStmt,0);
					Set<String> allClassNames = new LinkedHashSet<String>();
					allClassNames.addAll(viClassNames);
					allClassNames.addAll(siClassNames);
					allClassNames.addAll(nsClassNames);
					if ( !allClassNames.isEmpty()) {
						foundClassNameOfFieldAccess = true;
						for (String className : allClassNames) {
							logger.info("Full reflectively accessed field name: " + className + "." + fieldNameConst.value);
						}
					}
				}
				
				if (foundReflectiveFieldAccess && !foundClassNameOfFieldAccess) {
					logger.warn("\tCould not identify the class name for this reflective field access");
				}
			}
			
		}
		return perMethodFieldReflectAccessCount;
	}
	
	public Set<String> handleNonStandardCases(SimpleLocalDefs defs, JAssignStmt inAssignStmt, int tabs) {
		logger.debug(Utils.createTabsStr(tabs) + "\tEntered handleClassDollarMethod");
		
		Set<String> classNames = new LinkedHashSet<String>();
		Stack<JAssignStmt> assignStmtStack = new Stack<JAssignStmt>();
		assignStmtStack.push(inAssignStmt);
		Stack<Integer> tabsStack = new Stack<Integer>();
		tabsStack.push(tabs);
		int useDefLimit = 100;
		int useDefCount = 0;
		while (!assignStmtStack.empty()) {
			if (useDefCount > useDefLimit) {
				break;
			}
			JAssignStmt currAssignStmt = assignStmtStack.pop();
			int currTabs = tabsStack.pop();
			handleNonRecursiveNonStandardCases(currTabs, classNames, currAssignStmt);
			for (ValueBox useBox : currAssignStmt.getUseBoxes()) {
				if (useBox.getValue() instanceof Local) {
					Local useLocal = (Local)useBox.getValue();
					for (Unit defUnit : defs.getDefsOfAt(useLocal, currAssignStmt)) {
						if (defUnit instanceof JAssignStmt) {
							JAssignStmt nextAssignStmt = (JAssignStmt)defUnit;
							handleNonRecursiveNonStandardCases(currTabs, classNames, nextAssignStmt);
							assignStmtStack.push(nextAssignStmt);
							tabsStack.push(currTabs+1);
							// The below commented out code should now be handled through the loop and stacks
							/*if ( handleNonStandardCases(defs,nextAssignStmt,tabs+1) ) {
								foundClassName = true;
							}*/
						}
					}
				}
			}	
			useDefCount++;
		}
		return classNames;
	}


	public boolean handleNonRecursiveNonStandardCases(int tabs, Set<String> classNames, JAssignStmt assignStmt) {
		if (assignStmt.containsInvokeExpr()) {
			InvokeExpr invokeExpr = (InvokeExpr)assignStmt.getInvokeExpr();
			if (invokeExpr.getMethod().getName().contains("class$")) {
				logger.debug(Utils.createTabsStr(tabs) + "\tFound invocation of class$ originating from field ref");
				if (invokeExpr.getArg(0) instanceof StringConstant) {
					StringConstant classNameConst = (StringConstant)invokeExpr.getArg(0);
					logger.debug(Utils.createTabsStr(tabs) + "\tReflective field access of class: " + classNameConst.value); 
					classNames.add(classNameConst.value);
				}
			}
		}
		if (assignStmt.getRightOp() instanceof ClassConstant) {
			ClassConstant classConstant = (ClassConstant)assignStmt.getRightOp();
			logger.debug(Utils.createTabsStr(tabs) + "\tReflective field access of class: " + classConstant.getValue()); 
			classNames.add(classConstant.getValue());
		}
		return !classNames.isEmpty();
	}

	public Set<String> handleStaticInvoke(SimpleLocalDefs defs, JAssignStmt classAssignStmt) {
		Set<String> classNames = new LinkedHashSet<String>();
		if (!(classAssignStmt.getRightOp() instanceof StaticInvokeExpr)) {
			return classNames;
		}
		StaticInvokeExpr getClassExpr = (StaticInvokeExpr)classAssignStmt.getRightOp();
		if (getClassExpr.getMethod().getName().equals("forName")) {
			return handleStringConstantFromSingleArg(getClassExpr,"forName");
		}
		return classNames;
	}

	public Set<String> handleVirtualInvoke(SimpleLocalDefs defs, JAssignStmt classAssignStmt) {
		Set<String> classNames = new LinkedHashSet<String>();
		if (!(classAssignStmt.getRightOp() instanceof VirtualInvokeExpr)) {
			return classNames;
		}
		VirtualInvokeExpr getClassExpr = (VirtualInvokeExpr)classAssignStmt.getRightOp();
		if (getClassExpr.getMethod().getName().equals("getClass")) {
			if (!(getClassExpr.getBase() instanceof Local)) {
				logger.warn("\tThis reflective field access of getClass has a non-local base.");
				return classNames;
			}
			Local objLocal = (Local)getClassExpr.getBase();
			List<Unit> objDefs = defs.getDefsOfAt(objLocal, classAssignStmt);
			for (Unit objDef : objDefs) {
				if (!(objDef instanceof JAssignStmt)) {
					continue;
				}
				JAssignStmt objAssignStmt = (JAssignStmt)objDef;
				if (!(objAssignStmt.getRightOp() instanceof JNewExpr)) {
					continue;
				}
				JNewExpr newExpr = (JNewExpr)objAssignStmt.getRightOp();
				logger.debug("\tReflective field access of class: " + newExpr.getType());
				classNames.add(newExpr.getType().toString());
				return classNames;
			}
		} else if (getClassExpr.getMethod().getName().equals("loadClass")) {
			return handleStringConstantFromSingleArg(getClassExpr,"loadClass");
		}
		return classNames;
	}


	public Set<String> handleStringConstantFromSingleArg(InvokeExpr getClassExpr, String methodName) {
		Set<String> classNames = new LinkedHashSet<String>();
		if (!(getClassExpr.getArg(0) instanceof StringConstant)) {
			logger.warn("\tThis reflective field access of " + methodName + " does not use a StringConstant.");
			return classNames;
		}
		StringConstant classNameConst = (StringConstant)getClassExpr.getArg(0);
		logger.debug("\tReflective field access of class: " + classNameConst.value);
		classNames.add(classNameConst.value);
		return classNames;
	}
	
	private boolean checkForReflectInvocation(InvokeExpr invokeExpr) {
		return invokeExpr.getMethod().getDeclaringClass().getName().equals("java.lang.reflect.Field");
	}
	
	public static void main(String[] args) {
		StopWatch allPhaseStopWatch = new StopWatch();
		allPhaseStopWatch.start();
		
		String apkFilePath = args[0];
		File apkFile = new File(apkFilePath);
		
		System.out.println("Analyzing apk " + apkFilePath);
		
		Logger logger = Utils.setupLogger(FieldReflectCountTransformer.class,apkFile.getName());
		FieldReflectCountTransformer transformer = new FieldReflectCountTransformer(apkFilePath);
		
		StopWatch singlePhaseStopWatch = new StopWatch();
		singlePhaseStopWatch.start();
		transformer.run();
		singlePhaseStopWatch.stop();
		logger.debug("frc analysis time (milliseconds):" + singlePhaseStopWatch.getElapsedTime());
		logger.debug("Total reflection API invocations: " + totalFieldReflectAccessCount);
		
		allPhaseStopWatch.stop();
		logger.debug("total runtime for all phases (milliseconds):" + allPhaseStopWatch.getElapsedTime());
		logger.debug("Reached end of frc main...");

	}
	
	public void run() {
		Options.v().set_whole_program(true);
		Options.v().set_output_format(Options.v().output_format_none);
		Options.v().setPhaseOption("jap.lit", "enabled:true");
		PackManager.v().getPack("wjtp")
		.add(new Transform("wjtp.frc", this));
		Options.v().set_time(false);
		PackManager.v().getPack("wjtp").apply();
	}

}
