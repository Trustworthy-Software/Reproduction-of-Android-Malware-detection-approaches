package edu.uci.seal.cases.analyses;


import java.io.File;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uci.seal.StopWatch;
import edu.uci.seal.Utils;
import edu.uci.seal.analyses.ApkBodyTransformer;
import soot.Body;
import soot.Local;
import soot.PackManager;
import soot.SootField;
import soot.Transform;
import soot.Unit;
import soot.jimple.CastExpr;
import soot.jimple.ClassConstant;
import soot.jimple.FieldRef;
import soot.jimple.StaticInvokeExpr;
import soot.jimple.StringConstant;
import soot.jimple.VirtualInvokeExpr;
import soot.jimple.internal.JAssignStmt;
import soot.options.Options;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.scalar.SimpleLocalDefs;

public class FieldReflectMethodDefinitionTransformer extends ApkBodyTransformer {
	
	Logger logger = LoggerFactory.getLogger(FieldReflectMethodDefinitionTransformer.class);
	
	public Map<SootField,Set<String>> fieldMethodNamesMap = new LinkedHashMap<SootField,Set<String>>();
	public Map<SootField,Set<String>> fieldClassNamesMap = new LinkedHashMap<SootField,Set<String>>();

	public FieldReflectMethodDefinitionTransformer(String apkFilePath) {
		super(apkFilePath);
	}
	
	@Override
	protected void internalTransform(Body b, String phaseName, Map<String, String> options) {
		logger.debug("frmd over " + b.getMethod().getDeclaringClass().getName() + "." + b.getMethod().getName());
		
		SimpleLocalDefs defs = new SimpleLocalDefs(new BriefUnitGraph(b));
		for (Unit unit : b.getUnits()) {
			if (unit instanceof JAssignStmt) {
				JAssignStmt assignStmt = (JAssignStmt)unit;
				if (assignStmt.getLeftOp() instanceof FieldRef) {
					FieldRef fieldRef = (FieldRef)assignStmt.getLeftOp();
					SootField field = fieldRef.getField();
					if ( field.getType().toString().equals("java.lang.reflect.Method") ) {
						handleReflectMethodAsFieldRef(defs, assignStmt);
					}
					if ( field.getType().toString().equals("java.lang.Class") ) {
						handleReflectClassAsFieldRef(defs, assignStmt, field);
					}
				}
			}
		}

	}

	public void handleReflectClassAsFieldRef(SimpleLocalDefs defs, JAssignStmt assignStmt, SootField field) {
		logger.debug("Field reference of java.lang.Class at " + assignStmt);
		if ( assignStmt.getRightOp() instanceof Local) {
			Local local = (Local)assignStmt.getRightOp();
			for (Unit defUnit : defs.getDefsOfAt(local, assignStmt)) {
				if (defUnit instanceof JAssignStmt) {
					JAssignStmt classNameAssignStmt = (JAssignStmt)defUnit;
					String className = null;
					if (classNameAssignStmt.getRightOp() instanceof StaticInvokeExpr) {
						StaticInvokeExpr invokeExpr = (StaticInvokeExpr)classNameAssignStmt.getRightOp();
						if (invokeExpr.getMethod().getName().equals("forName")) {
							if (invokeExpr.getArg(0) instanceof StringConstant) {
								StringConstant classNameConst = (StringConstant)invokeExpr.getArg(0);
								className = classNameConst.value;
							}
						}
					}
					if (classNameAssignStmt.getRightOp() instanceof ClassConstant) {
						ClassConstant classConst = (ClassConstant)classNameAssignStmt.getRightOp();
						className = classConst.value;
					}
					if (className != null) {
						logger.debug("\t Class field reference of " + field + " is assigned to class " + className);
						Set<String> classNames = null;
						if (fieldClassNamesMap.containsKey(field)) {
							classNames = fieldClassNamesMap.get(field);
						} else {
							classNames = new LinkedHashSet<String>();
						}
						classNames.add(className);
						fieldClassNamesMap.put(field,classNames);
					} else {
						logger.warn("\tCould not identify class name for " + assignStmt);
					}
				}
			}
		}
	}

	public void handleReflectMethodAsFieldRef(SimpleLocalDefs defs, JAssignStmt assignStmt) {
		logger.debug("Field reference of java.lang.reflect.Method at " + assignStmt);
		if ( assignStmt.getRightOp() instanceof Local) {
			Local local = (Local)assignStmt.getRightOp();
			for (Unit defUnit : defs.getDefsOfAt(local, assignStmt)) {
				if (defUnit instanceof JAssignStmt) {
					JAssignStmt getMethodAssignStmt = (JAssignStmt)defUnit;
					if (getMethodAssignStmt.getRightOp() instanceof VirtualInvokeExpr) {
						VirtualInvokeExpr invokeExpr = (VirtualInvokeExpr)getMethodAssignStmt.getRightOp();
						if (MethodConstants.reflectiveGetMethodsSet.contains(invokeExpr.getMethod().getName())) {
							if ( invokeExpr.getArg(0) instanceof StringConstant ) {
								StringConstant methodNameConst = (StringConstant)invokeExpr.getArg(0);
								if (invokeExpr.getBase() instanceof Local) {
									Local invokedClassLocal = (Local)invokeExpr.getBase();
									for (Unit classDefUnit : defs.getDefsOfAt(invokedClassLocal, getMethodAssignStmt)) {
										if (classDefUnit instanceof JAssignStmt) {
											JAssignStmt classAssignStmt = (JAssignStmt)classDefUnit;
											if ( classAssignStmt.getRightOp() instanceof FieldRef) {
												FieldRef classFieldRef = (FieldRef)classAssignStmt.getRightOp();
												logger.debug("\t" + methodNameConst + " invoked with " + classFieldRef.getField());
												Set<String> methodNames = null;
												if (fieldMethodNamesMap.containsKey(classFieldRef.getField())) {
													methodNames = fieldMethodNamesMap.get(classFieldRef.getField());
												} else {
													methodNames = new LinkedHashSet<String>();
												}
												methodNames.add(methodNameConst.value);
												fieldMethodNamesMap.put(classFieldRef.getField(), methodNames);
											}
											if ( classAssignStmt.getRightOp() instanceof VirtualInvokeExpr) {
												VirtualInvokeExpr classInvokeExpr = (VirtualInvokeExpr)classAssignStmt.getRightOp();
												if (classInvokeExpr.getMethod().getName().equals("loadClass")) {
													if (classInvokeExpr.getArg(0) instanceof StringConstant) {
														StringConstant classNameConst = (StringConstant)classInvokeExpr.getArg(0);
														logger.info("\tFound reflective invocation of " + classNameConst.value + "." + methodNameConst.value);
													} else {
														logger.warn("\tloadClass(...) invoked with non-constant string value at " + classAssignStmt);
													}
												}
												if (classInvokeExpr.getMethod().getName().equals("getClass")) {
													if (classInvokeExpr.getBase() instanceof Local) {
														Local getClassLocal = (Local)classInvokeExpr.getBase();
														for (Unit getClassDefUnit : defs.getDefsOfAt(getClassLocal, classAssignStmt)) {
															if (getClassDefUnit instanceof JAssignStmt) {
																JAssignStmt getClassAssignStmt = (JAssignStmt)getClassDefUnit;
																if (getClassAssignStmt.getRightOp() instanceof CastExpr) {
																	CastExpr castExpr = (CastExpr)getClassAssignStmt.getRightOp();
																	logger.info("\tFound reflective invocation of " + castExpr.getType() + "." + methodNameConst.value);
																}
																if (getClassAssignStmt.getRightOp() instanceof StaticInvokeExpr) {
																	StaticInvokeExpr staticInvokeExpr = (StaticInvokeExpr)getClassAssignStmt.getRightOp();
																	 logger.info("\tFound reflective invocation of " + staticInvokeExpr.getMethod().getReturnType() + "." + methodNameConst.value);
																}
															}
														}
													}
												}
											}
											if ( classAssignStmt.getRightOp() instanceof StaticInvokeExpr) {
												StaticInvokeExpr classInvokeExpr = (StaticInvokeExpr)classAssignStmt.getRightOp();
												if (classInvokeExpr.getMethod().getName().equals("forName")) {
													if (classInvokeExpr.getArg(0) instanceof StringConstant) {
														StringConstant classNameConst = (StringConstant)classInvokeExpr.getArg(0);
														logger.info("\tFound reflective invocation of " + classNameConst.value + "." + methodNameConst.value);
													} else {
														logger.warn("\tforName(...) invoked with non-constant string value at " + classAssignStmt);
													}
												}
											}
											if ( classAssignStmt.getRightOp() instanceof ClassConstant) {
												ClassConstant classConst = (ClassConstant)classAssignStmt.getRightOp();
												logger.info("\tFound reflective invocation of " + classConst.value.replaceAll("/", ".") + "." + methodNameConst.value);
											}
										}
									}
								}
								
							} else {
								logger.debug("\tNon-constant string value for reflective method invocation, where method is stored as field");
							}
						}
					}
				}
			}
		}
	}

	public static void main(String[] args) {
		StopWatch allPhaseStopWatch = new StopWatch();
		allPhaseStopWatch.start();
		
		String apkFilePath = args[0];
		File apkFile = new File(apkFilePath);
		
		System.out.println("Analyzing apk " + apkFilePath);
		
		Logger logger = Utils.setupLogger(FieldReflectMethodDefinitionTransformer.class,apkFile.getName());
		FieldReflectMethodDefinitionTransformer transformer = new FieldReflectMethodDefinitionTransformer(apkFilePath);
		
		StopWatch singlePhaseStopWatch = new StopWatch();
		singlePhaseStopWatch.start();
		transformer.run();
		singlePhaseStopWatch.stop();
		logger.debug("frmd analysis time (milliseconds):" + singlePhaseStopWatch.getElapsedTime());
		
		printFieldStringsMap(transformer.fieldClassNamesMap,logger);
		printFieldStringsMap(transformer.fieldMethodNamesMap,logger);
		
		for (SootField field: transformer.fieldClassNamesMap.keySet()) {
			if (transformer.fieldClassNamesMap.containsKey(field) && transformer.fieldMethodNamesMap.containsKey(field)) {
				Set<String> classNames = transformer.fieldClassNamesMap.get(field);
				Set<String> methodNames = transformer.fieldMethodNamesMap.get(field);
				for (String className : classNames) {
					for (String methodName : methodNames) {
						logger.info(className + "." + methodName + " is reflectively invoked.");
					}
				}
			}
		}
		
		allPhaseStopWatch.stop();
		logger.debug("total runtime for all phases (milliseconds):" + allPhaseStopWatch.getElapsedTime());
		logger.debug("Reached end of frmd main...");

	}
	
	public static void printFieldStringsMap(Map<SootField,Set<String>> map, Logger logger) {
		for (Entry<SootField,Set<String>> entry : map.entrySet()) {
			SootField field = entry.getKey();
			Set<String> classNames = entry.getValue();
			logger.debug("Field " + field + " has the following values: ");
			for (String className : classNames) {
				logger.debug("\t" + className);
			}
		}
	}
	
	public void run() {
		Options.v().set_output_format(Options.v().output_format_none);
		Utils.setupAndroidAppForBody();
		PackManager.v().getPack("jtp").add(new Transform("jtp.frmd", this));
		PackManager.v().runPacks();
	}

}
