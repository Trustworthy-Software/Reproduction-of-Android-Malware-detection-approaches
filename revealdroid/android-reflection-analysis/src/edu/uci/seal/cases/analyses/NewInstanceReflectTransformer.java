package edu.uci.seal.cases.analyses;


import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uci.seal.StopWatch;
import edu.uci.seal.Utils;
import edu.uci.seal.analyses.ApkSceneTransformer;
import soot.Body;
import soot.Local;
import soot.PackManager;
import soot.PatchingChain;
import soot.SceneTransformer;
import soot.SootMethod;
import soot.Transform;
import soot.Unit;
import soot.ValueBox;
import soot.jimple.ClassConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.StringConstant;
import soot.jimple.VirtualInvokeExpr;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JInvokeStmt;
import soot.jimple.toolkits.callgraph.CHATransformer;
import soot.options.Options;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.SimpleLocalDefs;

public class NewInstanceReflectTransformer extends ApkSceneTransformer {
	
	Logger logger = LoggerFactory.getLogger(NewInstanceReflectTransformer.class);	
	public static int totalNewInstanceInvocations = 0;
	
	public NewInstanceReflectTransformer(String apkFilePath) {
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
			logger.trace("Finished analysis on method: " + method);
			logger.trace("Number of methods analyzed: " + currMethodCount);
			currMethodCount++;
		}
	}
	
	private void doAnalysisOnMethod(SootMethod method) {
		logger.debug("Analyzing method " + method.getSignature());
		
		int perMethodNewInstanceCount = 0;
		if (method.hasActiveBody()) {
			Body body = method.getActiveBody();
			PatchingChain<Unit> units = body.getUnits();
			
			UnitGraph unitGraph = new BriefUnitGraph(body);
			SimpleLocalDefs defs = new SimpleLocalDefs(unitGraph);
			
			for (Unit unit : units) {
				if (unit instanceof JAssignStmt) {
					JAssignStmt assignStmt = (JAssignStmt)unit;
					if (!(assignStmt.getRightOp() instanceof VirtualInvokeExpr)) {
						continue;
					}
					VirtualInvokeExpr invokeExpr = (VirtualInvokeExpr)assignStmt.getRightOp();
					boolean foundNewInstanceInvocation = false;
					boolean foundClassOfInvocation = false;
					if ( isFromClassOrConstructor(invokeExpr) && invokeExpr.getMethod().getName().equals("newInstance") ) {
						perMethodNewInstanceCount++;
						foundNewInstanceInvocation = true;
						if (!(invokeExpr.getBase() instanceof Local)) {
							continue;
						}
						Local classorCtorLocal = (Local)invokeExpr.getBase();
						List<Unit> classOrCtorDefs = defs.getDefsOfAt(classorCtorLocal, assignStmt);
						for (Unit classOrCtorDef : classOrCtorDefs) {
							if (!(classOrCtorDef instanceof JAssignStmt)) {
								continue;
							}
							JAssignStmt classOrCtorAssignStmt = (JAssignStmt)classOrCtorDef;
							Stack<JAssignStmt> assignStmts = new Stack<JAssignStmt>();
							assignStmts.push(classOrCtorAssignStmt);
							while (!assignStmts.isEmpty()) {
								JAssignStmt currAssignStmt = assignStmts.pop();
								if (!(currAssignStmt.getRightOp() instanceof ClassConstant)) {
									for (ValueBox useBox : currAssignStmt.getUseBoxes()) {
										if (useBox.getValue() instanceof Local) {
											Local useLocal = (Local) useBox.getValue();
											for (Unit defUnit : defs.getDefsOfAt(useLocal, currAssignStmt)) {
												if (defUnit instanceof JAssignStmt) {
													JAssignStmt newAssignStmt = (JAssignStmt) defUnit;
													assignStmts.add(newAssignStmt);
												}
											}
										}
									}
									continue;
								}
								ClassConstant classConstant = (ClassConstant) currAssignStmt.getRightOp();
								logger.debug("Identified reflective creation of new instance of class: " + classConstant.value);
								foundClassOfInvocation = true;
							}
						}
						if (foundNewInstanceInvocation && !foundClassOfInvocation) {
							logger.warn("Found a reflective object creation but could not identify the class it is creating at: " + assignStmt);
						}
					}
				}
				else if (unit instanceof JInvokeStmt) {
					JInvokeStmt invokeStmt = (JInvokeStmt)unit;
					if ( isFromClassOrConstructor(invokeStmt.getInvokeExpr()) ) {
						perMethodNewInstanceCount++;
					}
				}
			}
		}
		
		totalNewInstanceInvocations += perMethodNewInstanceCount;
	}
	
	private boolean isFromClassOrConstructor(InvokeExpr invokeExpr) {
		return MethodConstants.reflectiveNewInstanceClassesSet.contains( invokeExpr.getMethod().getDeclaringClass().getName() );
	}

	public static void main(String[] args) {
		StopWatch allPhaseStopWatch = new StopWatch();
		allPhaseStopWatch.start();
		
		String apkFilePath = args[0];
		File apkFile = new File(apkFilePath);
		
		System.out.println("Analyzing apk " + apkFilePath);
		
		Logger logger = Utils.setupLogger(NewInstanceReflectTransformer.class,apkFile.getName());
		NewInstanceReflectTransformer transformer = new NewInstanceReflectTransformer(apkFilePath);
		
		StopWatch singlePhaseStopWatch = new StopWatch();
		singlePhaseStopWatch.start();
		transformer.run();
		singlePhaseStopWatch.stop();
		logger.debug("frc analysis time (milliseconds):" + singlePhaseStopWatch.getElapsedTime());
		logger.debug("Total reflection API invocations: " + totalNewInstanceInvocations);
		
		allPhaseStopWatch.stop();
		logger.debug("total runtime for all phases (milliseconds):" + allPhaseStopWatch.getElapsedTime());
		logger.debug("Reached end of ni main...");
	}
	
	public void run() {
		Options.v().set_whole_program(true);
		Options.v().set_output_format(Options.v().output_format_none);
		Options.v().setPhaseOption("jap.lit", "enabled:true");
		PackManager.v().getPack("wjtp")
		.add(new Transform("wjtp.ni", this));
		Options.v().set_time(false);
		PackManager.v().getPack("wjtp").apply();
	}

}
