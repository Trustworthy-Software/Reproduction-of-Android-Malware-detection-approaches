package revealdroid.features.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

import org.xmlpull.v1.XmlPullParserException;

import com.google.common.collect.Lists;

import revealdroid.features.config.ApkConfig;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.options.Options;
import soot.util.Chain;

public class Util {
	
	public static void setupSoot() {
		//SetupApplication app = new SetupApplication(ApkConfig.androidJAR,ApkConfig.apkFilePath);
		//app.setCallgraphAlgorithm(CallgraphAlgorithm.CHA);
		//try {
			//app.calculateSourcesSinksEntrypoints("SourcesAndSinks.txt");
			ApkConfig.applyWholeProgramSootOptions();
			// entryPoints are the entryPoints required by Soot to calculate Graph - if there is no main method,
			// we have to create a new main method and use it as entryPoint and store our real entryPoints
			Scene.v().loadNecessaryClasses();
			//Scene.v().setEntryPoints(Collections.singletonList(app.getEntryPointCreator().createDummyMain()));
		//} catch (IOException e) {
		//	e.printStackTrace();
		//} catch (XmlPullParserException e) {
		//	e.printStackTrace();
		//}
	}

	public static String createTabsStr(int tabs) {
		String tabsStr = "";
		for (int i=0;i<tabs;i++) {
			tabsStr += "\t";
		}
		return tabsStr;
	}
	
	public static List<SootMethod> getMethodsInReverseTopologicalOrder() {
		List<SootMethod> entryPoints = Scene.v().getEntryPoints();
		CallGraph cg = Scene.v().getCallGraph();
		List<SootMethod> topologicalOrderMethods = new ArrayList<SootMethod>();

		Stack<SootMethod> methodsToAnalyze = new Stack<SootMethod>();

		for (SootMethod entryPoint : entryPoints) {
			if (isApplicationMethod(entryPoint)) {
				methodsToAnalyze.push(entryPoint);
				while (!methodsToAnalyze.isEmpty()) {
					SootMethod method = methodsToAnalyze.pop();
					if (!topologicalOrderMethods.contains(method)) {
						topologicalOrderMethods.add(method);
						for (Edge edge : getOutgoingEdges(method, cg)) {
							methodsToAnalyze.push(edge.tgt());
						}
					}
				}
			}
		}
		return topologicalOrderMethods;
	}
	
	public static boolean isApplicationMethod(SootMethod method) {
		Chain<SootClass> applicationClasses = Scene.v().getApplicationClasses();
		for (SootClass appClass : applicationClasses) {
			if (appClass.getMethods().contains(method)) {
				return true;
			}
		}
		return false;
	}

	public static List<Edge> getOutgoingEdges(SootMethod method, CallGraph cg) {
		Iterator<Edge> edgeIterator = cg.edgesOutOf(method);
		List<Edge> outgoingEdges = Lists.newArrayList(edgeIterator);
		return outgoingEdges;
	}

}
