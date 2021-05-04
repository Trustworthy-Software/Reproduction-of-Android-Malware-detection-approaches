/**
 * File: src/MciaUtil/TryCatchWrapper.java
 * -------------------------------------------------------------------------------------------
 * Date			Author      Changes
 * -------------------------------------------------------------------------------------------
 * 07/05/13		hcai		created, add try...catch(Exception) blocks into a given program entity in Jimple 
 * 08/27/13		hcai		added wrapTryCatchAllBlocks to wrap try...catch(Throwable) and put logging facilities
 *							inside this extraenous catch block
 * 
*/
package iacUtil;

import java.util.ArrayList;
import java.util.List;

import profile.InstrumManager;
import profile.UtilInstrum;

import soot.*;
import soot.jimple.*;

public class TryCatchWrapper {
	public static boolean logThrowableCauses = false;
	public static boolean logStackTrace = true;
	/**
	 * wrap around <i>the whole body of a given method</i> the <u>outermost</u>
	 * try-catch blocks like try{ original body ...} catch(Throwable e) { throw e }
	 *
	 * wrap outermost try{...}catch(Throwable e){throw e} blocks to the given method so that we can always capture
	 * the returnedInto event triggered by the method even if an uncaught exception occurred in it
	 * 
	 * 1. we add the "Throwable e" catcher, in order to catch all possible exceptions uncaught;
	 * 2. we do nothing in the catch block but to rethrow the exception, in order to keep the original program semantics unchanged;
	 * 3. in order to make the code still compilable with the "throw e" statement added, we need make sure the method signature has
	 *     included "throws Throwable" declaration, we add this if it is not originally declared as that.
	 * @param m the method to be wrapped around the try-catch blocks
	 * @return 0 for success, and failure otherwise
	 *  
	 *  as an example, the method, after wrapped, would be like the following
	 *    
	public static void main(java.lang.String[]) throws java.lang.Throwable
    {
        java.lang.String[] r0;
        java.lang.Throwable r1, $r2;

        r0 := @parameter0: java.lang.String[];

     label0:
        staticinvoke <ScheduleClass: void _main(java.lang.String[])>(r0);

     label1:
        goto label3;

     label2:
        $r2 := @caughtexception;
        r1 = $r2;
        throw r1;

     label3:
        return;

        catch java.lang.Exception from label0 to label1 with label2;
    }
	 */
	public static int wrapTryCatchAllBlocks(SootMethod m) {
		return wrapTryCatchAllBlocks(m, false, false);
	}

	/**
	 * by wrapping the outermost "catch (Throwable)" block, ensure that any type of exceptions and runtime errors can be
	 * caught, if thrown, in the given method
	 * @param m the source method where the catch block is added
	 * @param logCatch if insert probe monitoring whether an originally uncaught exception or runtime error really happened in runtime
	 * @param debugOut the verbose flag
	 * @return 0 for success and others for error code indicating type of failures
	 */
	public static int wrapTryCatchAllBlocks(SootMethod m, boolean logCatch, boolean debugOut) {
		// all probes for the try-catch blocks insertion
		List<Stmt> tcProbes = new ArrayList<Stmt>();
		Body b = m.retrieveActiveBody();
		PatchingChain<Unit> pchain = b.getUnits();
		
		// it is only safe to insert probes after all ID statements
		Stmt sFirstNonId = UtilInstrum.getFirstNonIdStmt(pchain);
		Stmt sLast = (Stmt) pchain.getLast();
		if (sLast instanceof ThrowStmt && b.getTraps().size()>=1) { // this happens when the whole body is nested in a synchronized block
			sLast = (Stmt) b.getTraps().getLast().getBeginUnit();
		}
		
		// Empty method won't cause any exception so we don't need instrument at all
		if (sFirstNonId == sLast) {
			return 0;
		}
		
		// add "throws Throwable" declaration if absent so that we add exception rethrowing statement in the catch block to be added
		//m.addExceptionIfAbsent(Scene.v().getSootClass("java.lang.Throwable"));
		SootClass sce = Scene.v().getSootClass("java.lang.Throwable");
		assert sce != null;
		if (!m.throwsException(sce)) {
			m.addException(sce);
			if (debugOut) {
				System.out.println("\n\"throws Throwable\" added to the method signature for " + m);
			}
		}
		
		// goto the original last statement
		Stmt gtstmt = Jimple.v().newGotoStmt(sLast);
		tcProbes.add(gtstmt);
		
		// two Locals of the Exception type to be inserted in the catch block
		Local er1 = utils.createUniqueLocal(b, "er1", RefType.v(sce));
		Local er2 = utils.createUniqueLocal(b, "$er2", RefType.v(sce));
		// the ID statement
		Stmt ids = Jimple.v().newIdentityStmt(er2, Jimple.v().newCaughtExceptionRef());
		// the assignment statement assigning the object for the throw statement
		Stmt ass = Jimple.v().newAssignStmt(er1, er2);
		// the throw statement rethrowing the exception caught in the block
		Stmt ths = Jimple.v().newThrowStmt(er1);
		// assemble the catch block
		tcProbes.add(ids);
		tcProbes.add(ass);
		if (logCatch) {
			SootClass clsSystem = Scene.v().getSootClass("java.lang.System");
			SootClass clsPrintStream = Scene.v().getSootClass("java.io.PrintStream");
			Type printStreamType = clsPrintStream.getType();
			SootField fldSysOut = clsSystem.getField("err", printStreamType);
			SootMethod mPrintln = clsPrintStream.getMethod("void println(java.lang.String)");
			
			// print out the basic record indicating that an uncaught exception is caught here within this hosting method
			Local str1out = utils.createUniqueLocal(b, "str1out", printStreamType);
			Stmt ssysout2str1out = Jimple.v().newAssignStmt(str1out, Jimple.v().newStaticFieldRef(fldSysOut.makeRef()));
			tcProbes.add(ssysout2str1out);
			List<StringConstant> sprintArgs = new ArrayList<StringConstant>();
			sprintArgs.add(StringConstant.v("Uncaught throwable caught in " + m.getSignature()/* + "\n Cause is the following:"*/));
			Stmt sprint = Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(str1out, mPrintln.makeRef(), sprintArgs));
			tcProbes.add(sprint);
			
			// print out the cause of the Throwable caught here that would otherwise not be caught in this method
			if (logThrowableCauses) {
				SootClass clsString = Scene.v().getSootClass("java.lang.String");
				Type strType = clsString.getType();
				Local str1 = utils.createUniqueLocal(b, "str1", strType);
				Local tcause = utils.createUniqueLocal(b, "tcause", RefType.v(sce));
				SootMethod mgetCause = sce.getMethod("java.lang.Throwable getCause()");
				Stmt sgetcause2tcause = Jimple.v().newAssignStmt(tcause, Jimple.v().newVirtualInvokeExpr(er1, mgetCause.makeRef()));
				tcProbes.add(sgetcause2tcause);
				SootMethod mtostring = sce.getMethod("java.lang.String getMessage()");
				Stmt causetostring = Jimple.v().newAssignStmt(str1, Jimple.v().newVirtualInvokeExpr(tcause, mtostring.makeRef()));
				tcProbes.add(causetostring);
				List<Local> sprintCauseArgs = new ArrayList<Local>();
				sprintCauseArgs.add(str1);
				Stmt sprintCause = Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(str1out, mPrintln.makeRef(), sprintCauseArgs));
				tcProbes.add(sprintCause);
			}
			
			// print out the tracing stack at this moment
			if (logStackTrace) {
				SootMethod mPrintStackTrace = sce.getMethod("void printStackTrace(java.io.PrintStream)");
				Stmt sprintstack = Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(er1, mPrintStackTrace.makeRef(), str1out));
				tcProbes.add(sprintstack);
			}
		}
		tcProbes.add(ths);
		
		// add the catch block to the patching chain of the given method's body
		if (sLast instanceof IdentityStmt) {
			System.out.println("wrapTryCatchAllBlocks: special case encountered in method " + m.getSignature() + " stmt: " + sLast);
			/*
			Stmt tgt = (Stmt)utils.getFirstNonIdUnit(pchain);
			if (tgt != null) tgt = (Stmt)pchain.getPredOf(tgt);
			if (tgt == null) tgt = sLast; 
			InstrumManager.v().insertAtProbeBottom(pchain, tcProbes, sLast);
			*/
			return 0;
		}
		else {
			InstrumManager.v().insertRightBeforeNoRedirect(pchain, tcProbes, sLast);
		}
		// finally, we add the trap associated with the newly added catch block
		Trap trap = Jimple.v().newTrap(sce, sFirstNonId, gtstmt, ids);
				
		b.getTraps().add(trap);
		
		return 0;
	}
	
	/**
	 * wrap around <i>the whole body of a given method</i> the <u>outermost</u>
	 * try-catch blocks like try{ original body ...} catch(Exception e) { throw e }
	 *
	 * wrap outermost try{...}catch(Exception e){throw e} blocks to the given method so that we can always capture
	 * the returnedInto event triggered by the method even if an uncaught exception occurred in it
	 * 
	 * 1. we add the "Exception e" catcher, in order to catch all possible exceptions uncaught;
	 * 2. we do nothing in the catch block but to rethrow the exception, in order to keep the original program semantics unchanged;
	 * 3. in order to make the code still compilable with the "throw e" statement added, we need make sure the method signature has
	 *     included "throws Exception" declaration, we add this if it is not originally declared as that.
	 * @param m the method to be wrapped around the try-catch blocks
	 * @return 0 for success, and failure otherwise
	 *  
	 *  as an example, the method, after wrapped, would be like the following
	 *    
	public static void main(java.lang.String[]) throws java.lang.Exception
    {
        java.lang.String[] r0;
        java.lang.Exception r1, $r2;

        r0 := @parameter0: java.lang.String[];

     label0:
        staticinvoke <ScheduleClass: void _main(java.lang.String[])>(r0);

     label1:
        goto label3;

     label2:
        $r2 := @caughtexception;
        r1 = $r2;
        throw r1;

     label3:
        return;

        catch java.lang.Exception from label0 to label1 with label2;
    }
	 */
	public static int wrapTryCatchBlocks(SootMethod m) {
		return wrapTryCatchBlocks(m, false);
	}

	public static int wrapTryCatchBlocks(SootMethod m, boolean debugOut) {
		// all probes for the try-catch blocks insertion
		List<Stmt> tcProbes = new ArrayList<Stmt>();
		Body b = m.retrieveActiveBody();
		PatchingChain<Unit> pchain = b.getUnits();
		
		// it is only safe to insert probes after all ID statements
		Stmt sFirstNonId = UtilInstrum.getFirstNonIdStmt(pchain);
		Stmt sLast = (Stmt) pchain.getLast();
		
		// Empty method won't cause any exception so we don't need instrument at all
		if (sFirstNonId == sLast) {
			return 0;
		}
		
		// add "throws Exception" declaration if absent so that we add exception rethrowing statement in the catch block to be added
		//m.addExceptionIfAbsent(Scene.v().getSootClass("java.lang.Exception"));
		SootClass sce = Scene.v().getSootClass("java.lang.Exception");
		assert sce != null;
		if (!m.throwsException(sce)) {
			m.addException(sce);
			if (debugOut) {
				System.out.println("\n\"throws Exception\" added to the method signature for " + m);
			}
		}
		
		// goto the original last statement
		Stmt gtstmt = Jimple.v().newGotoStmt(sLast);
		tcProbes.add(gtstmt);
		
		// two Locals of the Exception type to be inserted in the catch block
		Local er1 = utils.createUniqueLocal(b, "er1", RefType.v(sce));
		Local er2 = utils.createUniqueLocal(b, "$er2", RefType.v(sce));
		// the ID statement
		Stmt ids = Jimple.v().newIdentityStmt(er2, Jimple.v().newCaughtExceptionRef());
		// the assignment statement assigning the object for the throw statement
		Stmt ass = Jimple.v().newAssignStmt(er1, er2);
		// the throw statement rethrowing the exception caught in the block
		Stmt ths = Jimple.v().newThrowStmt(er1);
		// assemble the catch block
		tcProbes.add(ids);
		tcProbes.add(ass);
		tcProbes.add(ths);
		
		// add the catch block to the patching chain of the given method's body
		InstrumManager.v().insertRightBeforeNoRedirect(pchain, tcProbes, sLast);
		
		// finally, we add the trap associated with the newly added catch block
		Trap trap = Jimple.v().newTrap(sce, sFirstNonId, gtstmt, ids);
		b.getTraps().add(trap);
		
		return 0;
	}
} 

/* vim :set ts=4 tw=4 tws=4 */

