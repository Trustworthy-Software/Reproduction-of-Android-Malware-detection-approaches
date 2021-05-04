/**
 * File: src/MciaUtil/utils.java
 * -------------------------------------------------------------------------------------------
 * Date			Author      Changes
 * -------------------------------------------------------------------------------------------
 * 07/05/13		hcai		created, miscellaneous utilities for the mcia project
 * 11/01/13		hcai		factored out some common utilities for instrumentation from EH instrumenter
 * 12/18/14		hcai		added removeGraphNode to remove a node from a directed graph
 * 10/19/15		hcai		added some utilities for android analysis
 * 
*/
package iacUtil;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import dua.method.CFGDefUses.Variable;
import dua.util.Pair;
import dua.util.Util;
import fault.StmtMapper;

import profile.UtilInstrum;

import soot.*;
import soot.jimple.AssignStmt;
import soot.jimple.Constant;
import soot.jimple.FieldRef;
import soot.jimple.IdentityStmt;
import soot.jimple.InstanceFieldRef;
import soot.jimple.IntConstant;
import soot.jimple.Jimple;
import soot.jimple.NewExpr;
import soot.jimple.ReturnStmt;
import soot.jimple.SpecialInvokeExpr;
import soot.jimple.StaticFieldRef;
import soot.jimple.StaticInvokeExpr;
import soot.jimple.Stmt;
//import soot.jimple.spark.pag.PAG;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.CallGraphBuilder;
import soot.jimple.toolkits.callgraph.ReachableMethods;
//import soot.options.SparkOptions;
//import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.graph.UnitGraph;
import soot.util.Chain;
import soot.util.queue.QueueReader;

public class utils {
	/**
	 * create a local of the particular type <i>t</i> with a unique <i>name</i> as specified in the given body <i>b</i>
	 * ensuring the uniqueness by suffixing, with indefinitely number of trials, a random number 
	 * @param b the body
	 * @param name the name of the target local
	 * @param t the type of the target local
	 * @return the Local created and added
	 */
	public static Local createUniqueLocal(Body b, String name, Type t) {
		String localName = name;
		final Random r = new Random();
		r.setSeed(System.currentTimeMillis());
		do {
			if (null == UtilInstrum.getLocal(b, localName)) {
				// unique name found
				break;
			}
			localName = name + r.nextInt();
		} while (true);
		
		// create the local with the unique name and add it to tbe body
		Local v = Jimple.v().newLocal(localName, t);
		b.getLocals().add(v);
		return v;
	}

	/**
	 * dump the Jimple code of the subject project under Soot analysis
	 * @param fObj the target file
	 */
	public static void writeJimple(File fObj) {
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(fObj));
			
			/* traverse all classes */
			Iterator<SootClass> clsIt = Scene.v().getClasses().iterator();
			while (clsIt.hasNext()) {
				SootClass sClass = (SootClass) clsIt.next();
				if ( sClass.isPhantom() ) {
					// skip phantom classes
					continue;
				}
				if ( !sClass.isApplicationClass() ) {
					// skip library classes
					continue;
				}
				
				/* traverse all methods of the class */
				Iterator<SootMethod> meIt = sClass.getMethods().iterator();
				while (meIt.hasNext()) {
					SootMethod sMethod = (SootMethod) meIt.next();
					if ( !sMethod.isConcrete() ) {
						// skip abstract methods and phantom methods, and native methods as well
						continue; 
					}
					if ( sMethod.toString().indexOf(": java.lang.Class class$") != -1 ) {
						// don't handle reflections now either
						continue;
					}
					
					// cannot instrument method event for a method without active body
					if ( !sMethod.hasActiveBody() ) {
						continue;
					}
					
					//Body body = sMethod.getActiveBody();
					Body body = sMethod.retrieveActiveBody();
					writer.write("\t"+sClass.getName()+"\n");
					writer.write(body + "\n");
				}
			}
			writer.flush();
			writer.close();
		}
		catch (FileNotFoundException e) { System.err.println("Couldn't write Jimple file: " + fObj + e); }
		catch (SecurityException e) { System.err.println("Couldn't write Jimple file: " + fObj + e); }
		catch (IOException e) { System.err.println("Couldn't write Jimple file: " + fObj + e); }
	}
	public static int getFunctionList(Set<String> mels) {
		return getFunctionLists(mels, null);
	}
	public static int getAllMethods(Set<SootMethod> allm) {
		return getFunctionLists(null, allm);
	}
	public static int getFunctionLists(Set<String> mels, Set<SootMethod> allm) {
		/* traverse all classes */
		Iterator<SootClass> clsIt = Scene.v().getClasses().iterator();
		int cnt = 0;
		while (clsIt.hasNext()) {
			SootClass sClass = (SootClass) clsIt.next();
			if ( sClass.isPhantom() ) {
				// skip phantom classes
				continue;
			}
			if ( !sClass.isApplicationClass() ) {
				// skip library classes
				continue;
			}
			
			/* traverse all methods of the class */
			Iterator<SootMethod> meIt = sClass.getMethods().iterator();
			while (meIt.hasNext()) {
				SootMethod sMethod = (SootMethod) meIt.next();
				if ( !sMethod.isConcrete() ) {
					// skip abstract methods and phantom methods, and native methods as well
					continue; 
				}
				if ( sMethod.toString().indexOf(": java.lang.Class class$") != -1 ) {
					// don't handle reflections now either
					continue;
				}
				
				// cannot instrument method event for a method without active body
				if ( !sMethod.hasActiveBody() ) {
					continue;
				}
				
				if (mels != null) {
					mels.add(sMethod.getSignature());
				}
				if (allm != null) {
					allm.add(sMethod);
				}
				cnt ++;
			}
		}
		
		return cnt;
	}
	
	/**
	 * dump function list of the subject project under Soot analysis
	 * @param the literal name of the target file
	 */
	public static void dumpFunctionList(String fn) {
		Set<String> mels = new LinkedHashSet<String>();
		if (getFunctionList(mels) < 1) {
			// nothing to dump
			return;
		}
		File fObj = new File(fn);
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(fObj));
			
			for (String m : mels) {
				//writer.write(sClass.getName()+"::"+sMethod.getName()+"\n");
				writer.write(m+"\n");
			}
			writer.flush();
			writer.close();
		}
		catch (FileNotFoundException e) { System.err.println("Couldn't write file: " + fObj + e); }
		catch (SecurityException e) { System.err.println("Couldn't write file: " + fObj + e); }
		catch (IOException e) { System.err.println("Couldn't write file: " + fObj + e); }
	}
	
	public static void dumpEntryReachableFunctionList(String fn) {
		File fObj = new File(fn);
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(fObj));
			
			for (SootMethod m : utils.getReachableMethodsFromEntries(true)) {
				//writer.write(utils.getFullMethodName(m)+"\n");
				writer.write(m.getSignature()+"\n");
			}
			writer.flush();
			writer.close();
		}
		catch (FileNotFoundException e) { System.err.println("Couldn't write file: " + fObj + e); }
		catch (SecurityException e) { System.err.println("Couldn't write file: " + fObj + e); }
		catch (IOException e) { System.err.println("Couldn't write file: " + fObj + e); }
	}
	
	/**
	 * a convenient helper determining if the given value is an instance field of "this" object
	 * @param v the value to determine
	 * @param sClass the sootClass the sootField associated with the given value belongs to
	 * @return the decision
	 */
	public static boolean isInstanceVarOfThis(Value v, SootClass sClass) {
		if ( !(v instanceof InstanceFieldRef) ) {
			return false;
		}
		SootField uf = ((FieldRef)v).getField();
		if (!sClass.getFields().contains(uf)) {
			return false;
		}
		Value base = ((InstanceFieldRef)v).getBase();
		if (!base.toString().equalsIgnoreCase("this") ||
				!base.getType().toString().equalsIgnoreCase(sClass.getName()) ) {
			/* not a InstanceFeildRef to the current class's field */
			return false;
		}
		return true;
	}
	
	/**
	 * combine class name and field to produce a name distinguish class fields
	 * @param v a variable that is supposed to be a field reference
	 * @return the canonical name for the field; 
	 * 		- static class variable/class field: className::fieldName
	 * 		- instance variable/class field: className.fieldName
	 */
	public static String getCanonicalFieldName(Variable v) {
		// StdVariable holding a Constant Value is not supported in CFGDefUses for now
		if (v.getValue() instanceof Constant) {
			return v.getValue().toString();
		}
		if (!v.isFieldRef()) {
			return v.toString();
		}
		String fieldName;
		FieldRef fr = (FieldRef)v.getValue();
		if (!fr.getField().isStatic()) {
			assert v.getValue() instanceof InstanceFieldRef;
			fieldName = fr.getField().getDeclaringClass().getName()+".F"+fr.getField().getName();
		}
		else {
			assert v.getValue() instanceof StaticFieldRef;
			fieldName = fr.getField().getDeclaringClass().getName()+"::F"+fr.getField().getName();
		}
		return fieldName;
	}
	
	/**
	 * combine the class name of the declaring class and the method name 
	 * @param m the soot method for which the name is retrieved
	 * @return the full method name that distinguishes the declaring class
	 */
	public static String getFullMethodName(SootMethod m) {
		if (m.isDeclared()) {
			return m.getDeclaringClass().getName() + "::" + m.getName();
		}
		// no declaring class, just use the "global" prefix to indicate that lack 
		return "global::"+m.getName();
	}
	
	/**
	 * a flexible representation of Jimple stmt Id encoded by DUAForensics that is specific to current mcia uses; namely
	 * it could be the id recognizable by DUAF's analysis, or the id encoded by StaticValueTransferGraph's serialization 
	 * which always uses a returnStmt to encode the stmt id that is obtained from the real Stmt in the StaticValueTransferGraph's
	 * graph nodes
	 * @param s the target Jimple statement
	 * @return the Integer representation of the statement id
	 */
	public static Integer getFlexibleStmtId(Stmt s) {
		String sid = "";
		if (null != s) {
			try {
				sid += StmtMapper.getGlobalStmtId(s);
			}
			catch(Exception e) {
				if (s instanceof ReturnStmt && ((ReturnStmt)s).getOp() instanceof IntConstant) {
					/** this is for the makeshift during Serialization of the "Stmt" field of SVTNode ONLY */
					sid += ( (IntConstant) ((ReturnStmt)s).getOp() ).toString();
				}
				else {
					sid = "unknown";
				}
			}
		}
		if (sid.equalsIgnoreCase("unknown") || sid.length() < 1) {
			return -1; // -1 indicates "unknown" or "invalid"
		}
		return Integer.valueOf(sid);
	}
	
	public static boolean isAppConcreteMethod(SootMethod m) {
		if (m.isAbstract()) return false;
		if (!m.isConcrete()) return false;
		//if (!Scene.v().getApplicationClasses().contains(m.getDeclaringClass())) return false;
		if (!getAppMethods().contains(m)) return false;
		return m.toString().indexOf(": java.lang.Class class$") == -1;
	}
	
	/**
	 * retrieve methods that are not control dependent on any methods (or control independent methods - CID methods) 
	 * using the legacy Soot call-graph facilities 
	 * @return the list of entry methods
	 */
	public static List<SootMethod> getCIDMethods(boolean appMethodOnly) {
		List<SootMethod> CIDMethods = new LinkedList<SootMethod>();
		PointsToAnalysis pa = Scene.v().getPointsToAnalysis();//new PAG(new SparkOptions(new LinkedHashMap()));
		CallGraphBuilder cgBuilder = new CallGraphBuilder(pa);
		cgBuilder.build();
		CallGraph cg = cgBuilder.getCallGraph();
		Iterator<MethodOrMethodContext> mIt = cg.sourceMethods();
		while (mIt.hasNext()) {
			SootMethod m = mIt.next().method();
			if (appMethodOnly && !isAppConcreteMethod(m)) {
				// search for application methods only
				continue;
			}
			// methods having no incoming edges are regarded as entry methods
			if (!cg.edgesInto(m).hasNext()) {
				CIDMethods.add(m);
			}
		}
		return CIDMethods;
	}
	
	public static Set<SootMethod> getEntryMethods(boolean appMethodOnly) {
		Set<SootMethod> entryMethods = new LinkedHashSet<SootMethod>();
		for (SootMethod m : EntryPoints.v().all()) {
			if (appMethodOnly && !isAppConcreteMethod(m)) {
				// search for application methods only
				continue;
			}
			entryMethods.add(m);
		}
		return entryMethods;
	}
	
	public static List<SootMethod> getAppMethods() {
		return EntryPoints.v().methodsOfApplicationClasses();
	}
	
	public static Set<SootMethod> getReachableMethods(SootMethod mSrc, boolean appMethodOnly) {
		Set<SootMethod> reachableMethods = new LinkedHashSet<SootMethod>();
		
		PointsToAnalysis pa = Scene.v().getPointsToAnalysis(); //new PAG(new SparkOptions(new LinkedHashMap()));
		CallGraphBuilder cgBuilder = new CallGraphBuilder(pa);
		
		cgBuilder.build();
		CallGraph cg = cgBuilder.getCallGraph();
		List<MethodOrMethodContext> sources = new LinkedList<MethodOrMethodContext>();
		sources.add(mSrc);
		ReachableMethods rms = new ReachableMethods(cg, sources);
		QueueReader<MethodOrMethodContext> queueReader = rms.listener();
		while (queueReader.hasNext()) {
			SootMethod m = queueReader.next().method();
			if (appMethodOnly && !isAppConcreteMethod(m)) {
				// search for application methods only
				continue;
			}
			
			reachableMethods.add(m);
			rms.update();
		}
				
		return reachableMethods;
	}
	
	public static Set<SootMethod> getReachableMethodsFromEntries(boolean appMethodOnly) {
		Set<SootMethod> reachableMethods = new LinkedHashSet<SootMethod>();
		for (SootMethod m : getEntryMethods(appMethodOnly)) {
			reachableMethods.addAll(getReachableMethods(m, appMethodOnly));
		}
		//Scene.v().getReachableMethods();		
		return reachableMethods;
	}
	
	/**
	 * get the first normal, namely non-Identity unit in the given chain
	 * @param <N> generic element type of the chain
	 * @param chn the given chain in which the search is to perform
	 * @return the first non-ID unit
	 */
	public static <N extends Unit> N getFirstNonIdUnit(Chain<N> chn) {
		N nn = chn.getFirst(); 
		
		while (null != nn && nn instanceof IdentityStmt) {
			nn = chn.getSuccOf(nn);
		}
		return nn;
	}
	
	public static int isUnitInBoxes(List<UnitBox> boxes, Unit u) {
		int loc = -1;
		for (UnitBox ub : boxes) {
			if (ub.getUnit().equals(u)) {
				loc ++;
				break;
			}
		}
		return loc;
	}
	
	public static void dumpUnitGraph(UnitGraph cg) {
		SootMethod sMethod = cg.getBody().getMethod();
		System.out.println("=== the CFG of method " + sMethod + "===");
		
		if (cg.getHeads().size()>1) {
			System.out.println("!!! The complete CFG of method " + sMethod + " has " + cg.getHeads().size() + " heads!!");
		}
		System.out.println("\tHead nodes [size=" + cg.getHeads().size()+"]:");
		for(Unit h: cg.getHeads()) {
			System.out.println("\t\t"+ h);
		}
		Iterator<Unit> iter = cg.iterator();
		while (iter.hasNext()) {
			Unit curu = iter.next();
			System.out.println("\t" + curu + " has " + cg.getSuccsOf(curu).size()+" descendants:");
			for (Unit u : cg.getSuccsOf(curu)) {
				System.out.println("\t\t"+ u + "");
			}
		}
		System.out.println("\tTail nodes [size=" + cg.getTails().size()+"]:");
		for(Unit t: cg.getTails()) {
			System.out.println("\t\t"+ t);
		}
		System.out.println("================ END =========================");
	}
	
	/** adapt to Jimple requirements for instrumenting an access to the given variable (w.r.t its underlying Soot Value */
	public static Value makeBoxedValue(SootMethod m, Value v, List probe, Type tLocalObj) {
		Body b = m.retrieveActiveBody();
		Local lobj = /*utils.createUniqueLocal(*/UtilInstrum.getCreateLocal(b, "<loc_object>", tLocalObj);
		Value vfinal = v;
		if (!(v instanceof Constant || v instanceof Local)) {
			Local lValCopy = /*utils.createUniqueLocal(*/UtilInstrum.getCreateLocal(b, "<loc_box_" + v.getType() + ">", v.getType());
			Stmt sCopyToLocal = Jimple.v().newAssignStmt(lValCopy, v);
			probe.add(sCopyToLocal);
			vfinal = lValCopy;
		}
		
		if (v.getType() instanceof PrimType) {
			Pair<RefType, SootMethod> refTypeAndCtor = Util.getBoxingTypeAndCtor((PrimType) v.getType());
			Stmt sNewBox = Jimple.v().newAssignStmt(lobj,	Jimple.v().newNewExpr(refTypeAndCtor.first()));
			Stmt sInitBox = Jimple.v().newInvokeStmt(Jimple.v().newSpecialInvokeExpr(lobj,refTypeAndCtor.second().makeRef(), vfinal));
			probe.add(sNewBox);
			probe.add(sInitBox);
		} else {
			assert v.getType() instanceof RefLikeType || (v instanceof StaticInvokeExpr);
			Stmt sCopyRef = Jimple.v().newAssignStmt(lobj, vfinal);
			probe.add(sCopyRef);
		}
		
		return lobj;
	}
	public static Value makeBoxedValue(SootMethod m, Value v, List probe) {
		return makeBoxedValue(m, v, probe, Scene.v().getSootClass("java.lang.Object").getType());
	}
	
	public static Stmt getFirstSafeNonIdStmt(Value val, SootMethod m) {
		Body b = m.retrieveActiveBody();
		PatchingChain<Unit> pchain = b.getUnits();
		Stmt s = UtilInstrum.getFirstNonIdStmt(pchain);
		if (m.getName().equals("<init>") && val == b.getLocals().getFirst()) {
			while (!(s.containsInvokeExpr() && s.getInvokeExpr() instanceof SpecialInvokeExpr
					&& ((SpecialInvokeExpr) s.getInvokeExpr()).getMethod().getName().equals("<init>") 
					&& ((SpecialInvokeExpr) s.getInvokeExpr()).getBase() == val)) {
				s = (Stmt) pchain.getSuccOf(s);
			}
			s = (Stmt) pchain.getSuccOf(s);
		}
		return s;
	}
	
	public static Stmt getSuccAfterNextSpecialInvokeStmt(PatchingChain<Unit> pchain, Stmt s) {
		// if s is new expr, then get past <init> specialinvoke
		if (s instanceof AssignStmt && ((AssignStmt) s).getRightOp() instanceof NewExpr) { // exclude newarray case
				Local lNew = (Local) ((AssignStmt) s).getLeftOp();
				do {
					s = (Stmt) pchain.getSuccOf(s);
				} // get past new expr
				while (!(s.containsInvokeExpr() && s.getInvokeExpr() instanceof SpecialInvokeExpr 
						&& ((SpecialInvokeExpr) s.getInvokeExpr()).getBase() == lNew));
		}
		return (Stmt) pchain.getSuccOf(s); // get successor, whether s is <init> specialinvoke or not
	}
	
	public static Stmt getAfterSpecialInvokeStmt(PatchingChain<Unit> pchain, Stmt s) {
		Stmt ret = (s.containsInvokeExpr() && s.getInvokeExpr() instanceof SpecialInvokeExpr) ? (Stmt) pchain.getSuccOf(s) : s;
		if (ret == null) ret = s;
		return ret;
	}
	
	public static boolean isAnonymousName (String name) {
		int idx = name.lastIndexOf('$');
		if (-1 == idx) return false;
		
		String nstr = name.substring(idx+1, name.length()-1); 
		for (Character c : nstr.toCharArray()) {
			if (!Character.isDigit(c)) return false;
		}
	
		return true;
	}
	public static boolean isAnonymousClass (SootClass cls) {
		String clsName = cls.getName();
		return isAnonymousName(clsName);
	}
	
	// remove a vertex from a directed graph
	public static SootMethod pickCallee(Stmt s) {
		if (!s.containsInvokeExpr()) return null;
		
		return s.getInvokeExpr().getMethod();
	}
	
	public static String getAPKName() {
		String inapk = soot.options.Options.v().process_dir().get(0);
        return inapk.substring(inapk.lastIndexOf('/'), inapk.lastIndexOf('.'));
	}
	public static String getFullAPKPath() {
		String inapk = soot.options.Options.v().process_dir().get(0);
        return inapk.substring(0, inapk.lastIndexOf('.'));
	}
	public static String getInputAPK() {
		return soot.options.Options.v().process_dir().get(0);
	}

	public static String getMAC() {
        try {
            InetAddress ip = InetAddress.getLocalHost();
            NetworkInterface network = NetworkInterface.getByInetAddress(ip);
            byte[] mac = network.getHardwareAddress();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mac.length; i++) {
                sb.append(String.format("%02X%s", mac[i],
                        (i < mac.length - 1) ? "-" : ""));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
	public static String getProcessID() {
		return ManagementFactory.getRuntimeMXBean().getName();
	}
}

/* vim :set ts=4 tw=4 tws=4 */

