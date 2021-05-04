package edu.uci.seal.flow.reflect;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.jgrapht.DirectedGraph;
import org.jgrapht.Graphs;
import org.jgrapht.alg.FloydWarshallShortestPaths;
import org.jgrapht.alg.TransitiveClosure;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import soot.PackManager;
import soot.Scene;
import soot.SootMethod;
import soot.Transform;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.jimple.ArrayRef;
import soot.jimple.ClassConstant;
import soot.jimple.DefinitionStmt;
import soot.jimple.StringConstant;
import soot.jimple.toolkits.callgraph.CHATransformer;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.options.Options;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.FlowSet;
import soot.toolkits.scalar.Pair;
import soot.util.Chain;
import edu.uci.seal.StopWatch;
import edu.uci.seal.Utils;
import edu.uci.seal.analyses.ApkSceneTransformer;

public class ReflectMethodInvokeTransformer extends ApkSceneTransformer {

	Logger logger = LoggerFactory.getLogger(ReflectMethodInvokeTransformer.class);
	
	public ReflectMethodInvokeTransformer(String apkFilePath) {
		super(apkFilePath);
	}
	
	@Override
	protected void internalTransform(String phaseName, Map<String, String> options) {
		//Utils.setupDummyMainMethod();
		
		logger.debug("Constructing call graph...");
		CHATransformer.v().transform();
		logger.debug("Call graph built");
		
		CallGraph cg = Scene.v().getCallGraph();
		
		logger.debug("Call graph edges:");
		Iterator<Edge> iter = cg.iterator();
		while (iter.hasNext()) {
			Edge edge = iter.next();
			logger.debug("\t cg edge: " + edge); 
		}
		
		Set<SootMethod> methods = Utils.getApplicationMethods();

		int currMethodCount = 1;
		logger.debug("total number of possible methods to analyze: " + methods.size());
		for (SootMethod method : methods) {
			logger.trace("Checking if I should analyze method: " + method);
			if (Utils.isApplicationMethod(method)) {
				if (method.hasActiveBody()) {
					doAnalysisOnMethod(method);
				}
				else {
					logger.trace("method " + method + " has no active body, so it's won't be analyzed.");
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
		
		if (!method.hasActiveBody()) {
			logger.debug("This method has no active body, so we're returning.");
			return;
		}
		
		UnitGraph graph = new BriefUnitGraph(method.getActiveBody());
		
		ReflectMethodInvokeFlow analysis = new ReflectMethodInvokeFlow(graph);
		
		Chain<Unit> units = method.getActiveBody().getUnits();
		for (Unit unit : units) {
			FlowSet<MethodInvokeFact> beforeFlow = analysis.getFlowBefore(unit);
			//FlowSet<MethodInvokeFact> afterFlow = analysis.getFlowAfter(unit);
			
			DefaultDirectedGraph<Value,DefaultEdge> g = new DefaultDirectedGraph<Value,DefaultEdge>(DefaultEdge.class); 
			if (!beforeFlow.isEmpty()) {
				logger.debug("unit " + unit + " IN flow: ");
				for (MethodInvokeFact fact : beforeFlow) {
					logger.debug("\tfact: " + fact);
				}
				for (ValueBox useBox : unit.getUseBoxes()) {
					if (useBox.getValue() instanceof StringConstant) {
						StringConstant stringConst = (StringConstant)useBox.getValue();
						//logger.debug("\t" + stringConst.value + " may affect " + fact);
						computeAffectingValues(unit, beforeFlow, g, stringConst.value);
					}
					
					if (useBox.getValue() instanceof ClassConstant) {
						ClassConstant classConst = (ClassConstant)useBox.getValue();
						computeAffectingValues(unit,beforeFlow,g,classConst.value);
					}
				}
			}
			/*if (!afterFlow.isEmpty()) {
				logger.debug("unit " + unit + " OUT flow: ");
				for (MethodInvokeFact fact : afterFlow) {
					logger.debug("\tfact: " + fact);
				}
			}*/
		}
	}

	public void computeAffectingValues(Unit unit, FlowSet<MethodInvokeFact> beforeFlow, DefaultDirectedGraph<Value, DefaultEdge> g, String affectingString) {
		if (g.edgeSet().size() == 0) {
			for (MethodInvokeFact fact : beforeFlow) {
				Value rightVal = fact.valuePair.getValue1();
				Value leftVal = fact.valuePair.getValue0();
				g.addVertex(leftVal);
				g.addVertex(rightVal);
				g.addEdge(rightVal, leftVal);
			}
			logger.debug(g.toString());
			FloydWarshallShortestPaths<Value,DefaultEdge> paths = new FloydWarshallShortestPaths<Value,DefaultEdge>(g);
			Value zeroVal = null;
			for (Value v : g.vertexSet()) {
				if (v.toString().equals("<<zero>>")) {
					zeroVal = v;
				}
			}
			
			Set<DefaultEdge> edgesToZero = g.incomingEdgesOf(zeroVal);
			Set<Value> touchVals = new LinkedHashSet<Value>();
			for (DefaultEdge e : edgesToZero) {
				Value source = g.getEdgeSource(e);
				touchVals.add(source);
			}
			
			for (ValueBox valBox : unit.getUseAndDefBoxes()) {
				checkIfValueBoxReachesTouchVal(g, affectingString, paths, touchVals, valBox);
			}
		}
	}

	public void checkIfValueBoxReachesTouchVal(DefaultDirectedGraph<Value, DefaultEdge> g, String affectingString, FloydWarshallShortestPaths<Value, DefaultEdge> paths, Set<Value> touchVals, ValueBox valBox) {
		if (g.containsVertex(valBox.getValue())) {
			for (Value touchVal : touchVals) {
				identifyTouchValue(paths, g, affectingString, touchVal, valBox.getValue());
				if (valBox.getValue() instanceof ArrayRef) {
					ArrayRef arrayRef = (ArrayRef) valBox.getValue();
					if (g.containsVertex(arrayRef.getBase())) {
						identifyTouchValue(paths, g, affectingString, touchVal, arrayRef.getBase());
					}
				}
			}
		}
	}

	public void identifyTouchValue(FloydWarshallShortestPaths<Value,DefaultEdge> paths, DefaultDirectedGraph<Value, DefaultEdge> g, String affectingString, Value touchVal, Value potentialTouchVal) {
		if ((paths.getShortestPath(potentialTouchVal, touchVal) != null) || potentialTouchVal.equals(touchVal)) {
			logger.debug("\t" + affectingString + " may affect " + touchVal + " through " + potentialTouchVal);
		}
	}

	public static void main(String[] args) {
		StopWatch allPhaseStopWatch = new StopWatch();
		allPhaseStopWatch.start();
		
		String apkFilePath = args[0];
		File apkFile = new File(apkFilePath);
		
		System.out.println("Analyzing apk " + apkFilePath);
		
		Logger logger = Utils.setupLogger(ReflectMethodInvokeTransformer.class,apkFile.getName());
		ReflectMethodInvokeTransformer transformer = new ReflectMethodInvokeTransformer(apkFilePath);
		
		StopWatch singlePhaseStopWatch = new StopWatch();
		singlePhaseStopWatch.start();
		transformer.run();
		singlePhaseStopWatch.stop();
		logger.debug("rmi analysis time (milliseconds):" + singlePhaseStopWatch.getElapsedTime());
		
		allPhaseStopWatch.stop();
		logger.debug("total runtime for all phases (milliseconds):" + allPhaseStopWatch.getElapsedTime());
		logger.debug("Reached end of rmi main...");

	}
	
	public void run() {
		Options.v().set_whole_program(true);
		Options.v().set_output_format(Options.v().output_format_none);
		Utils.setupDummyMainMethod();
		PackManager.v().getPack("wjtp")
		.add(new Transform("wjtp.mi", this));
		PackManager.v().getPack("wjtp").apply();
	}

}
