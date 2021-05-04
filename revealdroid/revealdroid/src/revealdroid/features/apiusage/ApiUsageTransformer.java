package revealdroid.features.apiusage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import revealdroid.features.config.ApkConfig;
import revealdroid.features.util.Util;
import soot.Body;
import soot.PackManager;
import soot.Scene;
import soot.SceneTransformer;
import soot.SootClass;
import soot.SootMethod;
import soot.Transform;
import soot.Unit;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.options.Options;
import soot.util.Chain;

public class ApiUsageTransformer extends SceneTransformer {
	
	boolean DEBUG = false;
	private String apkFilePath = "";
	public Map<String,Integer> pkgAccessCountMap = new HashMap<String,Integer>(); // key: package name, value: number of accesses to the package
	public Map<String,Integer> methodInvokeCountMap = new HashMap<String,Integer>(); // key: method name, value: number of times that method is invoked
	
	Logger logger = LoggerFactory.getLogger(ApiUsageTransformer.class);
	
	public ApiUsageTransformer(String apkFilePath) {
		soot.G.reset();
		ApkConfig.apkFilePath = apkFilePath;
	}
	
	@Override
	protected void internalTransform(String phaseName, Map<String, String> options) {
		Util.setupSoot();

		logger.debug("no. of application classes: " + Scene.v().getApplicationClasses().size());
		for (SootClass sootClass : Scene.v().getApplicationClasses()) {
			if (DEBUG) {
				System.out.println(sootClass + " invokes methods from the following Android packages:");
			}
			List<InvokeStmt> invokeStmtsToAnalyze = new ArrayList<InvokeStmt>();
			for (SootMethod method : sootClass.getMethods() ) {
				if (method.isConcrete()) {
					Body body = method.retrieveActiveBody();
					PackManager.v().getPack("jtp").apply(body);
	                if( Options.v().validate() ) {
	                    body.validate();
	                }
				}
				
				if (method.hasActiveBody()) {
					Body body = method.getActiveBody();
					Chain<Unit> units = body.getUnits();

					for (Unit unit : units) {
						if (unit instanceof InvokeStmt) {
							invokeStmtsToAnalyze.add((InvokeStmt)unit);
						}
					}
				}
			}
			for (InvokeStmt stmt : invokeStmtsToAnalyze) {
				extractFeaturesFromStmt(stmt);
			}
		}
		
	}

	private synchronized void extractFeaturesFromStmt(InvokeStmt unit) {
		InvokeStmt invokeStmt = unit;
		InvokeExpr expr = invokeStmt.getInvokeExpr();
		String invokedPackageName = expr.getMethod().getDeclaringClass().getPackageName();
		if (invokedPackageName.startsWith("android.")) {
            String fullMethodName = expr.getMethod().getDeclaringClass().getName() + "." + expr.getMethod().getName();
            if (DEBUG) {
                System.out.println("\t" + invokedPackageName);
                System.out.println("\t" + fullMethodName);
            }
            Integer invocationCount = null;
            if (pkgAccessCountMap.containsKey(invokedPackageName)) {
                invocationCount = pkgAccessCountMap.get(invokedPackageName);
                invocationCount++;
            }
            else {
                invocationCount = 1;
            }
            pkgAccessCountMap.put(invokedPackageName, invocationCount);

            invocationCount = null;
            if (methodInvokeCountMap.containsKey(fullMethodName)) {
                invocationCount = methodInvokeCountMap.get(fullMethodName);
                invocationCount++;
            } else {
                invocationCount = 1;
            }
            methodInvokeCountMap.put(fullMethodName,invocationCount);
        }
	}

	public void run() {
		Options.v().set_whole_program(true);
		// Options.v().set_verbose(true);

		// Options.v().set_output_format(Options.v().output_format_jimple);

		// Setup dump of method bodies
		/*
		 * List<String> dump = new ArrayList<String>(); dump.add("ALL");
		 * Options.v().set_dump_cfg(dump); Options.v().set_dump_body(dump);
		 */

		PackManager.v().getPack("wjtp").add(new Transform("wjtp.apiusage", this));

		PackManager.v().getPack("wjtp").apply();
		// PackManager.v().writeOutput();
		// PackManager.v().getPack("wjap").apply();
	}

}
