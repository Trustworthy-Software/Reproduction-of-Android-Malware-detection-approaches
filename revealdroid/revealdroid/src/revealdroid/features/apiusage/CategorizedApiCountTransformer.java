package revealdroid.features.apiusage;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import revealdroid.enduser.cli.AnalyzeSingleAppForReputationUsingCatApicount;
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
import soot.jimple.toolkits.callgraph.CHATransformer;
import soot.options.Options;
import soot.util.Chain;

public class CategorizedApiCountTransformer extends SceneTransformer {
	
	static Logger logger = LoggerFactory.getLogger(CategorizedApiCountTransformer.class);
	
	boolean DEBUG = false;
	private String apkFilePath = "";
	public Map<String,Integer> catAccessCountMap; // key: package name, value: number of accesses to the package
	
	public CategorizedApiCountTransformer(String apkFilePath) {
		soot.G.reset();
		ApkConfig.apkFilePath = apkFilePath;
	}
	
	@Override
	protected void internalTransform(String phaseName, Map<String, String> options) {
		Util.setupSoot();
		
		catAccessCountMap = new HashMap<String,Integer>();
		
		// key: method, value: category
		Map<String,String> methodCategories = new LinkedHashMap<String,String>();
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(AnalyzeSingleAppForReputationUsingCatApicount.revealdroidDir+"prmDomains.txt"), Charset.defaultCharset())) {
		    String line = null;
		    while ((line = reader.readLine()) != null) {
		    	if (!line.contains("%")) {
		    		continue;
		    	}
		        String[] tokens = line.split("%");
		        String category = tokens[0].trim();
		        String method = tokens[1].trim();
		        
		        methodCategories.put(method, category);
		    }
		} catch (IOException x) {
		    x.printStackTrace();
		}
		
		try {
			extractCatApiCounts(methodCategories);
		}
		catch (IllegalStateException e) {
			e.printStackTrace();
		}
		
		
	}

	public void extractCatApiCounts(Map<String, String> methodCategories) {
		for (SootClass sootClass : Scene.v().getApplicationClasses()) {
			/*if (DEBUG) {
				logger.debug(sootClass + " invokes methods from the following Android packages:");
			}*/
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
							InvokeStmt invokeStmt = (InvokeStmt) unit;
							InvokeExpr expr = invokeStmt.getInvokeExpr();
							String invokedMethod = expr.getMethod().toString();
							String category = methodCategories.get(invokedMethod);
							if (category == null) {
								category = "NO_CATEGORY";
							}
							
							Integer invocationCount = null;
							if (catAccessCountMap.containsKey(category)) {
								invocationCount = catAccessCountMap.get(category);
								invocationCount++;
							}
							else {
								invocationCount = 1;
							}
							if (DEBUG) {
								logger.debug("Found API invocation of category " + category + ":");
								logger.debug("\t" + unit);
							}
							catAccessCountMap.put(category, invocationCount);
							
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

		PackManager.v().getPack("wjtp").add(new Transform("wjtp.apiusage", this));

		PackManager.v().getPack("wjtp").apply();
		// PackManager.v().writeOutput();
		// PackManager.v().getPack("wjap").apply();
	}

}
