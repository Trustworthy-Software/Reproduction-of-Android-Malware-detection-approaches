package revealdroid.features.intent;

import java.util.Map;

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
import soot.Value;
import soot.ValueBox;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.StringConstant;
import soot.jimple.toolkits.callgraph.CHATransformer;
import soot.options.Options;
import soot.util.Chain;

public class IntentActionExtractionTransformer extends SceneTransformer {

	private String apkFilePath = "";
	
	public IntentActionExtractionTransformer(String apkFilePath) {
		soot.G.reset();
		ApkConfig.apkFilePath = apkFilePath;
	}
	
	@Override
	protected void internalTransform(String phaseName, Map<String, String> options) {
		Util.setupSoot();
		//PackManager.v().runBodyPacks();
		//PackManager.v().getPack("jtp").apply();
		for (SootClass sootClass : Scene.v().getApplicationClasses()) {
			System.out.println("Found " + sootClass);
			if (sootClass.hasSuperclass()) {
				SootClass superClass = sootClass.getSuperclass();
				if (superClass.getName().startsWith("android.")) {
					System.out.println("\t" + sootClass + " extends " + superClass);
					
					if (superClass.getName().contains("BroadcastReceiver")) {
						System.out.println("\t" + sootClass + " is a BroadcastReceiver");
						if (sootClass.getMethods().isEmpty()) {
							System.out.println("\t" + sootClass + " has no methods");
						}
						for (SootMethod method : sootClass.getMethods()) {
							System.out.println("\t\thas method" + method);
							if (method.isConcrete()) {
								Body body = method.retrieveActiveBody();
								PackManager.v().getPack("jtp").apply(body);
				                if( Options.v().validate() ) {
				                    body.validate();
				                }
							}
							if (method.hasActiveBody() && method.getName().contains("onReceive")) {
								Body body = method.getActiveBody();
								Chain<Unit> units = body.getUnits();
								for (Unit unit : units) {
									for (ValueBox useBox : unit.getUseBoxes())  {
										Value useValue = useBox.getValue();
										if (useValue instanceof StringConstant) {
											StringConstant constant = (StringConstant)useValue;
											if (constant.value.startsWith("android.intent.action")) {
												System.out.println("\t\t\tthis receivers does something with the following Intent action: " + constant);
												ExtractSystemIntentActions.programmaticActions.add(constant.value);
											}
										}
									}
								}

								/*for (Unit unit : units) {
									if (unit instanceof InvokeStmt) {
										InvokeStmt invokeStmt = (InvokeStmt) unit;
										InvokeExpr expr = invokeStmt.getInvokeExpr();
										String invokedPackageName = expr.getMethod().getDeclaringClass().getPackageName();
										if (invokedPackageName.startsWith("android.")) {
											System.out.println("\t" + invokedPackageName);
										}
									}
								}*/
							} else  {
								System.out.println("\t\t\t" + method + " has no active body");
							}
						}
					}					
				}
			}
			
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

		PackManager.v().getPack("wjtp").add(new Transform("wjtp.intentaction", this));

		PackManager.v().getPack("wjtp").apply();
		// PackManager.v().writeOutput();
		// PackManager.v().getPack("wjap").apply();
	}

}
