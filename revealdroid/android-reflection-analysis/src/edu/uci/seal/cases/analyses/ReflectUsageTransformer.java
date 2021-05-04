package edu.uci.seal.cases.analyses;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import soot.Body;
import soot.Hierarchy;
import soot.Local;
import soot.PackManager;
import soot.PatchingChain;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Transform;
import soot.Unit;
import soot.ValueBox;
import soot.jimple.ClassConstant;
import soot.jimple.FieldRef;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.jimple.VirtualInvokeExpr;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JInvokeStmt;
import soot.jimple.toolkits.callgraph.CHATransformer;
import soot.options.Options;
import soot.tagkit.Tag;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.SimpleLocalDefs;
import edu.uci.seal.Config;
import edu.uci.seal.StopWatch;
import edu.uci.seal.Utils;
import edu.uci.seal.analyses.ApkSceneTransformer;

public class ReflectUsageTransformer extends ApkSceneTransformer {
	
	Logger logger = LoggerFactory.getLogger(ReflectUsageTransformer.class);
	private static int totalReflectInvokeCount = 0;
	private static int nonStringConstantMethodNameCount = 0;
	private static int methodNameWithoutClassNameCount = 0;
	private static int fullMethodNameCount = 0;
	private static Map<String,Integer> fullMethodCounts = new LinkedHashMap<String,Integer>();
	private static Map<String,Integer> partMethodCounts = new LinkedHashMap<String,Integer>();
	
	public ReflectUsageTransformer(String apkFilePath) {
		super(apkFilePath);
	}

	public static void main(String[] args) {
		StopWatch allPhaseStopWatch = new StopWatch();
		allPhaseStopWatch.start();
		
		String apkFilePath = args[0];
		File apkFile = new File(apkFilePath);
		
		System.out.println("Analyzing apk " + apkFilePath);
		
		Logger logger = Utils.setupLogger(ReflectUsageTransformer.class,apkFile.getName());
		ReflectUsageTransformer transformer = new ReflectUsageTransformer(apkFilePath);
		
		StopWatch singlePhaseStopWatch = new StopWatch();
		singlePhaseStopWatch.start();
		transformer.run();
		singlePhaseStopWatch.stop();

		Map<String,Integer> reflectFeatures = new LinkedHashMap<String,Integer>();

		String RIC = "reflect_invoke_count";
		String NCMC = "nonstring_constant_method_count";
		String MNCC = "method_no_class_count";
		String FMC = "full_method_count";
		logger.debug("RU analysis time (milliseconds):" + singlePhaseStopWatch.getElapsedTime());

		logger.debug("Total reflection API invocations: " + totalReflectInvokeCount);
		logger.debug(RIC + ": " + totalReflectInvokeCount);
		reflectFeatures.put(RIC,totalReflectInvokeCount);

		logger.debug(NCMC + ": " + nonStringConstantMethodNameCount);
		reflectFeatures.put(NCMC,nonStringConstantMethodNameCount);

		logger.debug(MNCC + ": " + methodNameWithoutClassNameCount);
		reflectFeatures.put(MNCC,methodNameWithoutClassNameCount);

		logger.debug(FMC + ": " + fullMethodNameCount);
		reflectFeatures.put(FMC,fullMethodNameCount);

		logger.debug("Full method counts:");
		for (String methodName : fullMethodCounts.keySet()) {
			logger.debug("\t" + methodName + ": " + fullMethodCounts.get(methodName));
			reflectFeatures.put(methodName,fullMethodCounts.get(methodName));
		}
		logger.debug("Partial method counts:");
		for (String methodName : partMethodCounts.keySet()) {
			logger.debug("\t" + methodName + ": " + partMethodCounts.get(methodName));
			reflectFeatures.put(methodName,partMethodCounts.get(methodName));
		}

		String apkFileName = apkFile.getName();
		String apkFileBase = apkFileName.substring(0,apkFileName.lastIndexOf("."));
		String outFileName = "data" + File.separator + apkFileBase + "_reflect.txt";
		File outFile = new File(outFileName);

		boolean DEBUG=true;
		try {
			if (DEBUG) {
				logger.debug("Writing the following reflection features:");
			}
			FileWriter writer = new FileWriter(outFile);
			for (String name : reflectFeatures.keySet()) {
				String outLine = name + "," + reflectFeatures.get(name);
				if (DEBUG) {
					logger.debug(outLine);
				}
				writer.write(outLine + "\n");
			}
			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		allPhaseStopWatch.stop();
		logger.debug("total runtime for all phases (milliseconds):" + allPhaseStopWatch.getElapsedTime());
		logger.debug("Reached end of RU main...");
	}

	@Override
	protected void internalTransform(String phaseName, Map<String, String> options) {
		Options.v().set_whole_program(true);
		Options.v().set_src_prec(Options.src_prec_apk);
		Options.v().set_output_format(Options.output_format_jimple);

		Options.v().set_allow_phantom_refs(true);
		Options.v().set_time(true);

		Options.v().set_no_bodies_for_excluded(true);
		Options.v().set_show_exception_dests(false);
		//Options.v().set_print_tags_in_output(true);
		Options.v().set_verbose(false);
		Options.v().set_android_jars(System.getenv("ANDROID_HOME"));
		Options.v()
				.set_soot_classpath(Config.apkFilePath + File.pathSeparator + 
						"lib/android.jar");
		List<String> processDirs = new ArrayList<String>();
		processDirs.add(Config.apkFilePath);
		Options.v().set_process_dir(processDirs);

		Options.v().set_keep_line_number(true);
		Options.v().set_coffi(true);
		Scene.v().loadNecessaryClasses();
		
		/*logger.debug("Constructing call graph...");
		CHATransformer.v().transform();
		logger.debug("Call graph built");*/
		
		//List<SootMethod> methods = Utils.getMethodsInReverseTopologicalOrder();
		Set<SootMethod> methods = Utils.getApplicationMethods();

		int currMethodCount = 1;
		logger.debug("total number of possible methods to analyze: " + methods.size());
		for (SootMethod method : methods) {
			logger.trace("Checking if I should analyze method: " + method);
			if (Utils.isApplicationMethod(method)) {
				if (method.isConcrete()) {
					Body body = method.retrieveActiveBody();
					PackManager.v().getPack("jtp").apply(body);
	                if( Options.v().validate() ) {
	                    body.validate();
	                }
				}
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
		
		int methodReflectInvokeCount = 0;
		if (method.hasActiveBody()) {
			Body body = method.getActiveBody();
			PatchingChain<Unit> units = body.getUnits();
			
			UnitGraph unitGraph = new BriefUnitGraph(body);
			SimpleLocalDefs defs = new SimpleLocalDefs(unitGraph);
			
			for (Unit unit : units) {
				if (unit instanceof JAssignStmt) {
					JAssignStmt assignStmt = (JAssignStmt)unit;
					if (assignStmt.getRightOp() instanceof VirtualInvokeExpr) {
						VirtualInvokeExpr invokeExpr = (VirtualInvokeExpr)assignStmt.getRightOp();
						methodReflectInvokeCount = identifyReflectiveCall(method, methodReflectInvokeCount, defs, assignStmt, invokeExpr);
					}
				}
				else if (unit instanceof JInvokeStmt) {
					JInvokeStmt invokeStmt = (JInvokeStmt)unit;
					if ( checkForReflectInvocation(invokeStmt.getInvokeExpr()) ) {
						if (invokeStmt.getInvokeExpr() instanceof VirtualInvokeExpr) {
							VirtualInvokeExpr virtualInvokeExpr = (VirtualInvokeExpr)invokeStmt.getInvokeExpr();
							methodReflectInvokeCount = identifyReflectiveCall(method, methodReflectInvokeCount, defs, invokeStmt, virtualInvokeExpr);
						}
					}
				}
			}
		}
		
		totalReflectInvokeCount += methodReflectInvokeCount;
	}

	public int identifyReflectiveCall(SootMethod method, int methodReflectInvokeCount, SimpleLocalDefs defs, Stmt inStmt, VirtualInvokeExpr invokeExpr) {
		if ( checkForReflectInvocation(invokeExpr) ) {
			Hierarchy hierarchy = Scene.v().getActiveHierarchy();
			SootClass classLoaderClass = Utils.getLibraryClass("java.lang.ClassLoader");
			methodReflectInvokeCount++;
			logger.debug(method.getName() + " reflectively invokes "  + invokeExpr.getMethod().getDeclaringClass() + "." + invokeExpr.getMethod().getName());
			if (invokeExpr.getMethod().getDeclaringClass().getName().equals("java.lang.reflect.Method")) {
				if (!(invokeExpr.getBase() instanceof Local)) {
					logger.debug("\tThis reflection API usage invocation has a callee of a non-local class");
					return methodReflectInvokeCount;
				}
				Local invokeExprLocal = (Local) invokeExpr.getBase();
				List<Unit> defUnits = defs.getDefsOfAt(invokeExprLocal, inStmt);
				for (Unit defUnit : defUnits) {
					if (!(defUnit instanceof JAssignStmt)) {
						continue;
					}
					JAssignStmt methodAssignStmt = (JAssignStmt) defUnit;
					if (methodAssignStmt.getRightOp() instanceof VirtualInvokeExpr) {
						VirtualInvokeExpr getDeclaredMethodExpr = (VirtualInvokeExpr)methodAssignStmt.getRightOp();
						if (getDeclaredMethodExpr.getMethod().getDeclaringClass().getName().equals("java.lang.Class") && MethodConstants.reflectiveGetMethodsSet.contains(getDeclaredMethodExpr.getMethod().getName())) {
							boolean result = handleReflectiveGetMethods(getDeclaredMethodExpr, methodAssignStmt, defs, inStmt, hierarchy, classLoaderClass);
							if (!result) {
								continue;
							} 
						}
					}
					for (ValueBox useBox : methodAssignStmt.getUseBoxes()) {
						if (useBox.getValue() instanceof FieldRef) {
							logger.debug("\t\t" + useBox + " is instanceof FieldRef");
							FieldRef fieldRef = (FieldRef)useBox.getValue();
							for (Tag tag : fieldRef.getField().getTags()) {
								logger.debug("\t\t\ttag: " + tag);
							}
						}
					}
				}
			}
		}
		return methodReflectInvokeCount;
	}
	
	private boolean handleReflectiveGetMethods(VirtualInvokeExpr getDeclaredMethodExpr, JAssignStmt methodAssignStmt, SimpleLocalDefs defs, Stmt inStmt, Hierarchy hierarchy, SootClass classLoaderClass) {
		if (!(getDeclaredMethodExpr.getArg(0) instanceof StringConstant)) {
			logger.warn("Reflective invocation is not a string constant at " + methodAssignStmt);
			nonStringConstantMethodNameCount++;
			return false;
		}
		StringConstant reflectivelyInvokedMethodName = (StringConstant)getDeclaredMethodExpr.getArg(0);
		//logger.debug("Found the following method invoked reflectively: " + reflectivelyInvokedMethodName);
		if (!(getDeclaredMethodExpr.getBase() instanceof Local)) {
			logger.warn("Reflective invocation receives a non-local method name at " + methodAssignStmt);
			return false;
		}
		Local classLocal = (Local)getDeclaredMethodExpr.getBase();
		List<Unit> classDefUnits = defs.getDefsOfAt(classLocal,inStmt);
		boolean foundClassName = false;
		for (Unit classDefUnit : classDefUnits) {
			if (!(classDefUnit instanceof JAssignStmt)) {
				return false;
			}
			JAssignStmt classAssignStmt = (JAssignStmt) classDefUnit;
			if (classAssignStmt.getRightOp() instanceof InvokeExpr) {
				InvokeExpr classInvokeExpr = (InvokeExpr) classAssignStmt.getRightOp();
				if (classInvokeExpr.getMethod().getDeclaringClass().getName().equals("java.lang.Class") && classInvokeExpr.getMethod().getName().equals("forName")) {
					if (classInvokeExpr.getArg(0) instanceof StringConstant) {
						StringConstant classNameConst = (StringConstant) classInvokeExpr.getArg(0);
						String fullMethodName = classNameConst.value + "." + reflectivelyInvokedMethodName.value;
						logger.debug("\twith class: " + classNameConst.value);
						logger.info("\tFound reflective invocation of " + fullMethodName);
						foundClassName = true;
						incrementFullMethodCounts(fullMethodName);
						fullMethodNameCount++;
					}
				} else if (classLoaderClass != null) {
					if (hierarchy.isClassSubclassOfIncluding(classInvokeExpr.getMethod().getDeclaringClass(), classLoaderClass)) {
						if (classInvokeExpr.getMethod().getName().equals("loadClass")) {
							if (classInvokeExpr.getArg(0) instanceof StringConstant) {
								StringConstant classNameConst = (StringConstant) classInvokeExpr.getArg(0);
								String fullMethodName = classNameConst.value + "." + reflectivelyInvokedMethodName.value;
								logger.debug("\twith class: " + classNameConst.value);
								logger.info("\tFound reflective invocation of " + fullMethodName);
								foundClassName = true;
								incrementFullMethodCounts(fullMethodName);
								fullMethodNameCount++;
							}
						}
					}
				} else if (classInvokeExpr.getMethod().getName().equals("getClass")) {
					if (classInvokeExpr instanceof VirtualInvokeExpr) {
						VirtualInvokeExpr classVirtualInvokeExpr = (VirtualInvokeExpr) classInvokeExpr;
						if (classVirtualInvokeExpr.getBase() instanceof Local) {
							Local objLocal = (Local) classVirtualInvokeExpr.getBase();
							for (Unit objDefUnit : defs.getDefsOfAt(objLocal, classAssignStmt)) {
								if (objDefUnit instanceof JAssignStmt) {
									JAssignStmt objAssignStmt = (JAssignStmt) objDefUnit;
									if (objAssignStmt.getRightOp() instanceof InvokeExpr) {
										InvokeExpr objInvokeExpr = (InvokeExpr) objAssignStmt.getRightOp();
										String fullMethodName = objInvokeExpr.getMethod().getReturnType() + "." + reflectivelyInvokedMethodName.value;
										logger.info("\tFound reflective invocation of " + fullMethodName);
										foundClassName = true;
										incrementFullMethodCounts(fullMethodName);
										fullMethodNameCount++;
									} else if (objAssignStmt.getRightOp() instanceof FieldRef) {
										FieldRef fieldRef = (FieldRef)objAssignStmt.getRightOp();
										String fullMethodName = fieldRef.getField().getType() + "." + reflectivelyInvokedMethodName.value;
										logger.info("\tFound reflective invocation of " + fullMethodName);
										foundClassName = true;
										incrementFullMethodCounts(fullMethodName);
										fullMethodNameCount++;
									}
								}
							}
						}
						if (!foundClassName) {
							foundClassName = true;
							logger.warn("\tCould not find class name for the following reflectively invoked method: " + reflectivelyInvokedMethodName.value);
							incrementPartMethodCounts(reflectivelyInvokedMethodName.value);
							methodNameWithoutClassNameCount++;
						}
					}
				}
			} else if (classAssignStmt.getRightOp() instanceof ClassConstant) {
				ClassConstant classConstant = (ClassConstant)classAssignStmt.getRightOp();
				String fullMethodName = classConstant.getValue() + "." + reflectivelyInvokedMethodName.value;
				logger.info("\tFound reflective invocation of " + fullMethodName);
				foundClassName = true;
				incrementFullMethodCounts(fullMethodName);
				fullMethodNameCount++;
			}
		}
		if (!foundClassName) {
			logger.warn("\tCould not find class name to match reflective invocation of method: " + reflectivelyInvokedMethodName);
			incrementPartMethodCounts(reflectivelyInvokedMethodName.value);
			methodNameWithoutClassNameCount++;
		}
		return true;
	}
	
	private void incrementFullMethodCounts(String fullMethodName) {
		Integer count = null;
		if (fullMethodCounts.containsKey(fullMethodName)) {
			count = fullMethodCounts.get(fullMethodName);
		} else {
			count = 0;
		}
		count++;
		fullMethodCounts.put(fullMethodName, count);
		
	}
	
	private void incrementPartMethodCounts(String methodNameOnly) {
		Integer count = null;
		if (partMethodCounts.containsKey(methodNameOnly)) {
			count = partMethodCounts.get(methodNameOnly);
		} else {
			count = 0;
		}
		count++;
		partMethodCounts.put(methodNameOnly, count);
		
	}

	private boolean checkForReflectInvocation(InvokeExpr invokeExpr) {
		return invokeExpr.getMethod().getDeclaringClass().getPackageName().startsWith("java.lang.reflect");
	}
	
	public void run() {
		Options.v().set_whole_program(true);
		Options.v().set_output_format(Options.v().output_format_none);
		PackManager.v().getPack("wjtp")
		.add(new Transform("wjtp.ru", this));
		PackManager.v().getPack("wjtp").apply();
	}

}
