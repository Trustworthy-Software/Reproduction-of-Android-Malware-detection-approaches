package edu.uci.seal.flow.reflect;

import java.util.ArrayList;
import java.util.List;

import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import soot.Local;
import soot.NullType;
import soot.Unit;
import soot.Value;
import soot.jimple.ArrayRef;
import soot.jimple.AssignStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.VirtualInvokeExpr;
import soot.jimple.internal.JimpleLocal;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.scalar.ArraySparseSet;
import soot.toolkits.scalar.BackwardFlowAnalysis;
import soot.toolkits.scalar.FlowSet;

public class ReflectMethodInvokeFlow extends BackwardFlowAnalysis<Unit, FlowSet<MethodInvokeFact>> {
	
	Logger logger = LoggerFactory.getLogger(ReflectMethodInvokeFlow.class);
	JimpleBasedInterproceduralCFG icfg = new JimpleBasedInterproceduralCFG();

	public ReflectMethodInvokeFlow(DirectedGraph<Unit> graph) {
		super(graph);
		doAnalysis();
	}
	
	public void printFlowSet(FlowSet<MethodInvokeFact> s) {
		for (MethodInvokeFact f : s) {
			logger.debug(f.toString());
		}
	}

	@Override
	protected void flowThrough(FlowSet<MethodInvokeFact> in, Unit d, FlowSet<MethodInvokeFact> out) {
		InvokeExpr invokeExpr = null;
		if (logger.isTraceEnabled()) {
			logger.trace("flowThrough for method " + icfg.getMethodOf(d));
			logger.trace("before in set: ");
			printFlowSet(in);
			logger.trace("before out set: ");
			printFlowSet(out);
		}
		if (d instanceof AssignStmt) {
			AssignStmt assignStmt = (AssignStmt)d;
			if (assignStmt.getRightOp() instanceof InvokeExpr) {
				//in.copy(out);
				invokeExpr = (InvokeExpr) assignStmt.getRightOp();

				for (MethodInvokeFact inFact : in) {
					if (assignStmt.getLeftOp().equals(inFact.valuePair.getValue1())) {
						if (invokeExpr instanceof VirtualInvokeExpr) {
							VirtualInvokeExpr vInvokeExpr = (VirtualInvokeExpr) invokeExpr;
							if (vInvokeExpr.getBase() instanceof Local) {
								MethodInvokeFact newBaseFact = new MethodInvokeFact(new Pair<Value, Value>(inFact.valuePair.getValue1(), vInvokeExpr.getBase()), d, icfg.getMethodOf(d));
								out.add(newBaseFact);
								out.union(in);
							}
						}
						List<Value> localArgs = new ArrayList<Value>();
						for (Value arg : invokeExpr.getArgs()) {
							if (arg instanceof Local) {
								localArgs.add(arg);
							}
						}
						for (Value arg : localArgs) {
							MethodInvokeFact newFact = new MethodInvokeFact(new Pair<Value, Value>(inFact.valuePair.getValue1(), arg), d, icfg.getMethodOf(d));
							out.add(newFact);
							out.union(in);
						}
					}
				}
			} else {
				// in.copy(out);
			for (MethodInvokeFact inFact : in) {
				if (assignStmt.getLeftOp().equals(inFact.valuePair.getValue1())) {
					MethodInvokeFact newFact = new MethodInvokeFact(new Pair<Value,Value>(inFact.valuePair.getValue1(),assignStmt.getRightOp()), d, icfg.getMethodOf(d));
					out.add(newFact);
					out.union(in);
				}
				if (assignStmt.getLeftOp() instanceof ArrayRef) {
					ArrayRef arrayRef = (ArrayRef) assignStmt.getLeftOp();
					if (arrayRef.getBase().equals(inFact.valuePair.getValue1())) {
						MethodInvokeFact newFact = new MethodInvokeFact(new Pair<Value,Value>(inFact.valuePair.getValue1(),assignStmt.getRightOp()), d, icfg.getMethodOf(d));
						out.add(newFact);
						out.union(in);
					}
				}
			}
			out.union(in); 
			return;
			}
		} else if (d instanceof InvokeStmt) {
			InvokeStmt invokeStmt = (InvokeStmt) d;
			invokeExpr = invokeStmt.getInvokeExpr();
		} else {
			//in.copy(out);
			out.union(in);
			return;
		}
		if (invokeExpr.getMethod().getName().equals("invoke") && invokeExpr.getMethod().getDeclaringClass().getPackageName().equals("java.lang.reflect")) {
			List<Value> localArgs = new ArrayList<Value>();
			for (Value arg: invokeExpr.getArgs()) {
				if (arg instanceof Local) {
					localArgs.add(arg);
				}
			}
			
			for ( Value arg : localArgs ) {
				MethodInvokeFact newFact = new MethodInvokeFact(new Pair<Value,Value>(new JimpleLocal("<<zero>>", NullType.v()),arg), d, icfg.getMethodOf(d));
				out.add(newFact);
				//out.union(in);
			}
			
			if (invokeExpr instanceof VirtualInvokeExpr) {
				VirtualInvokeExpr vInvokeExpr = (VirtualInvokeExpr)invokeExpr;
				MethodInvokeFact newFact = new MethodInvokeFact(new Pair<Value,Value>(new JimpleLocal("<<zero>>", NullType.v()),vInvokeExpr.getBase()),d,icfg.getMethodOf(d));
				out.add(newFact);
				//out.union(in);
			}
		}
		out.union(in);
		if (logger.isTraceEnabled()) {
			logger.trace("flowThrough for method " + icfg.getMethodOf(d));
			logger.trace("after in set: ");
			printFlowSet(in);
			logger.trace("after out set: ");
			printFlowSet(out);
		}
	}
	
	@Override
    protected FlowSet<MethodInvokeFact> entryInitialFlow()
    {

    	FlowSet<MethodInvokeFact> fs = new ArraySparseSet<MethodInvokeFact>();
        return fs;
    }

	@Override
	protected FlowSet<MethodInvokeFact> newInitialFlow() {
		return new ArraySparseSet<MethodInvokeFact>(); 
	}

	@Override
	protected void merge(FlowSet<MethodInvokeFact> in1, FlowSet<MethodInvokeFact> in2, FlowSet<MethodInvokeFact> out) {
		in1.union(in2, out);
		
	}

	@Override
	protected void copy(FlowSet<MethodInvokeFact> source, FlowSet<MethodInvokeFact> dest) {
		source.copy(dest);
		
	}

}
