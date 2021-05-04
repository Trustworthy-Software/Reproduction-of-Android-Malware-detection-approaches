/**
 * File: src/dynCG/enumCallSeq.java
 * -------------------------------------------------------------------------------------------
 * Date			Author      Changes
 * -------------------------------------------------------------------------------------------
 * 12/10/15		hcai		created; for traversal of the static call graph to enumerate call paths
*/
package dynCG;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;
import java.io.File;
import java.io.BufferedWriter;
import java.io.FileWriter;
import org.xmlpull.v1.XmlPullParserException;
import java.lang.System;
import soot.PackManager;
import soot.Scene;
import soot.SootMethod;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.options.Options;
import soot.util.queue.QueueReader;

public class enumCallSeq {
	public static void main(String[] args) {
		String appToRun = args[0];
		String androidPlatform = args[1];
		String filename = appToRun + ".txt";
		List<String> toInclude = Arrays.asList("java.", "android.", "org.", "com.", "javax.");
		List<String> toExclude = Arrays.asList("soot.");
		soot.G.reset();
		SetupApplication app = new SetupApplication("/home/hcai/Android/Sdk/platforms/android-21/android.jar", appToRun);
		app.getConfig().setEnableStaticFieldTracking(false); //no static field tracking --nostatic
		app.getConfig().setAccessPathLength(1); // specify access path length
		app.getConfig().setFlowSensitiveAliasing(false); // alias flowin
		try {
			app.calculateSourcesSinksEntrypoints("/home/hcai/libs/SourcesAndSinks.txt");
		} catch (IOException e) {
			e.printStackTrace();
		} catch (XmlPullParserException e) {
			e.printStackTrace();
		}

		PackManager.v().getPack("cg");
		PackManager.v().getPack("jb");
		PackManager.v().getPack("wjap.cgg");
		Options.v().set_src_prec(Options.src_prec_apk);
		Options.v().set_process_dir(Collections.singletonList(appToRun));
		Options.v().set_android_jars(androidPlatform);
		Options.v().force_android_jar();
		Options.v().set_force_android_jar("/home/hcai/Android/Sdk/platforms/android-21/android.jar");
		Options.v().set_whole_program(true);
		Options.v().set_allow_phantom_refs(true);
		Options.v().set_prepend_classpath(true);
		Options.v().set_app(true);
		Options.v().set_include(toInclude);
		Options.v().set_exclude(toExclude);
		Options.v().set_output_format(Options.output_format_xml);
		//Options.v().set_output_dir("output/");
		Options.v().set_soot_classpath("/home/hcai/libs/soot-trunk.jar:/home/hcai/libs/soot-infoflow.jar:/home/hcai/libs/soot-infoflow-android.jar:/home/hcai/libs/axml-2.0.jar:/home/hcai/libs/slf4j-simple-1.7.5.jar:/home/hcai/libs/slf4j-api-1.7.5.jar");
		Options.v().setPhaseOption("cg", "safe-newinstance:true");
		Options.v().setPhaseOption("cg.spark", "on");
		Options.v().setPhaseOption("wjap.cgg", "show-lib-meths:true");
		Options.v().setPhaseOption("jb", "use-original-names:true");
		Scene.v().loadNecessaryClasses();
		SootMethod entryPoint = app.getEntryPointCreator().createDummyMain();
		Options.v().set_main_class(entryPoint.getSignature());
		Scene.v().setEntryPoints(Collections.singletonList(entryPoint));
		System.out.println(entryPoint.getActiveBody());
		try {
			PackManager.v().runPacks();
		}
		catch (Exception f){
			f.printStackTrace();
		}
		try (BufferedWriter writer = new BufferedWriter(
				new FileWriter(
						new File(filename)))
		){
			writer.write (partialDFS());
		}
		catch (IOException e){
			System.out.println("An error occurred");
		}
	}
	
	static Set<SootMethod> getEntries (CallGraph cg) {
		Set<SootMethod> ret = new LinkedHashSet<SootMethod>();
		QueueReader<Edge> reader = cg.listener();
		while(reader.hasNext()) {
            Edge e = (Edge) reader.next();
            if (!cg.edgesInto(e.src()).hasNext()) {
            	ret.add(e.src());
            }
		}
		return ret;
	}
	
	static void dumpQueue (Queue<SootMethod> q, StringBuffer out) {
		//Queue <SootMethod> q = new LinkedList<SootMethod>();
		/*
		while (!q.isEmpty()) {
			out.append(q.peek().getSignature());
		}
		out.append('\n');
		*/
		String tmp;
		
		List<SootMethod> mes = new ArrayList<SootMethod>(q); 
		if (mes.size() < 2) return;
		
		out.append(mes.get(0).getSignature() + " ==> [");
		tmp = mes.get(0).getSignature() + " ==> [";
		
		for (int i = 1; i<mes.size(); i++) {
			out.append(mes.get(i).getSignature());
			tmp += mes.get(i).getSignature();
			
			if (i!=mes.size()-1) {
				out.append(", ");
				tmp += ", ";
			}
		}
		out.append("]\n");
		tmp+="]\n";
		
		System.out.println(tmp);
	}
	
	static void dumpStack (Stack<SootMethod> s, StringBuffer out) {
		String tmp;
		
		List<SootMethod> mes = new ArrayList<SootMethod>(s); 
		if (mes.size() < 2) return;
		
		out.append(mes.get(0).getSignature() + " ==> [");
		tmp = mes.get(0).getSignature() + " ==> [";
		
		for (int i = 1; i<mes.size(); i++) {
			out.append(mes.get(i).getSignature());
			tmp += mes.get(i).getSignature();
			
			if (i!=mes.size()-1) {
				out.append(", ");
				tmp += ", ";
			}
		}
		out.append("]\n");
		tmp+="]\n";
		
		System.out.println(tmp);
	}
	
	static void enumerate (CallGraph cg, SootMethod head, StringBuffer out) {
		Stack<SootMethod> s = new Stack<SootMethod>();
		s.push(head);
		while (!s.isEmpty()) {
			SootMethod n = s.pop();
			
			Iterator<Edge> iter = cg.edgesOutOf(n);
			int cnt = 0;
			while (iter.hasNext()) {
				SootMethod child = iter.next().tgt();
				// avoid cycles
				if (!s.contains(child)) {
					s.push(child);
					cnt ++;
				}
			}
			if (cnt == 0) {
				// done with one sequence
				dumpStack(s, out);
			}
		}
	}
	
	static void DFS (Set<SootMethod> visited, Queue<SootMethod> prefix, CallGraph cg, SootMethod n, StringBuffer out) {
		Iterator<Edge> iter = cg.edgesOutOf(n);
		int cnt = 0;
		Queue<SootMethod> curprefix = new LinkedList<SootMethod>(prefix);
		curprefix.add(n);
		visited.add(n);
		while (iter.hasNext()) {
			SootMethod child = iter.next().tgt();
			if (visited.contains(child)) continue;
			// avoid cycles
			if (!curprefix.contains(child)) {
				DFS (visited, curprefix, cg, child, out);
				cnt ++;
			}
		}
		if (cnt == 0) {
			dumpQueue(curprefix, out);
		}
	}
	
	static String partialDFS() {
		CallGraph cg = Scene.v().getCallGraph();
		//QueueReader<Edge> reader = cg.listener();
		StringBuffer out = new StringBuffer();
		//Set<SootMethod> entries = utils.utils.getEntryMethods(false);
		Set<SootMethod> entries = getEntries(cg);
		
		System.out.println("entries are: " + entries);
		
		for (SootMethod entry:entries) {
			//enumerate (cg, entry, out);
			DFS (new LinkedHashSet<SootMethod>(), new LinkedList<SootMethod>(), cg, entry, out);
		}
	    return out.toString();
	}
}
