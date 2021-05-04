/**
 * File: src/intentTracker/iccAPICom.java
 * -------------------------------------------------------------------------------------------
 * Date			Author      Changes
 * -------------------------------------------------------------------------------------------
 * 09/28/15		hcai		common Android ICC API resources and relevant functionalities 
 * 10/14/15		hcai		add monitoring of intent receipts
 * 01/29/16		hcai		added the routine for initializing the component classes (needed for analyzing two or more APKs
 * 							in one Soot process)
*/
package iacUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import soot.FastHierarchy;
import soot.Hierarchy;
import soot.Scene;
import soot.SootClass;
import soot.jimple.AssignStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;

public class iccAPICom {
	 final static String[] __IntentSendingAPIs = {
		        "startActivity",
		        "startActivities",
		        "startActivityForResult",
		        "startActivityFromChild",
		        "startActivityFromFragment",
		        "startActivityIfNeeded",
		        "startNextMatchingActivity",
		        "sendBroadcast",
		        "sendBroadcastAsUser",
		        "sendOrderedBroadcast",
		        "sendOrderedBroadcastAsUser",
		        "sendStickyBroadcast",
		        "sendStickyBroadcastAsUser",
		        "sendStickyOrderedBroadcast",
		        "sendStickyOrderedBroadcastAsUser",
		        "removeStickyBroadcast",
		        "removeStickyBroadcastAsUser",
		        "bindService",
		        "startService",
		        "stopService",
		        "startIntentSender",
		        "startIntentSenderForResult",
		        "startIntentSenderFromChild"
		    };
	 
	    final static List<String> g__IntentSendingAPIs = new ArrayList<String> (Arrays.asList(__IntentSendingAPIs));

		public static boolean is_IntentSendingAPI(Stmt u) {
			if (!u.containsInvokeExpr()) {
				return false;
			}
			InvokeExpr inv = u.getInvokeExpr();
			// simple and naive decision based on textual matching
			return g__IntentSendingAPIs.contains(inv.getMethod().getName());
		}
		
		//////////////////////////////////////////
	    final static String[] __IntentReceivingAPIs = {
	        "getIntent",
	        "getParentActivityIntent",
	    };
	    
	    final static List<String> g__IntentReceivingAPIs = new ArrayList<String> (Arrays.asList(__IntentReceivingAPIs));

		public static boolean is_IntentReceivingAPI(Stmt u) {
			if (!(u instanceof AssignStmt)) {
				return false;
			}
			if (!u.containsInvokeExpr()) {
				return false;
			}
			InvokeExpr inv = u.getInvokeExpr();
			// simple and naive decision based on textual matching
			return g__IntentReceivingAPIs.contains(inv.getMethod().getName());
		}
		
		public static boolean is_IntentReceivingAPI(String cs) {
			for (String s : g__IntentReceivingAPIs) {
				if (cs.contains(s)) return true;
			}
			return false;
		}
		
		public static boolean is_IntentSendingAPI(String cs) {
			for (String s : g__IntentSendingAPIs) {
				if (cs.contains(s)) return true;
			}
			return false;
		}
		
		public static SootClass COMPONENT_TYPE_ACTIVITY = Scene.v().getSootClass("android.app.Activity");
		public static SootClass COMPONENT_TYPE_SERVICE = Scene.v().getSootClass("android.app.Service");
		public static SootClass COMPONENT_TYPE_RECEIVER = Scene.v().getSootClass("android.content.BroadcastReceiver");
		public static SootClass COMPONENT_TYPE_PROVIDER = Scene.v().getSootClass("android.content.ContentProvider");
		public static SootClass COMPONENT_TYPE_UNKNOWN = Scene.v().getSootClass("java.lang.Object");
		
		public static SootClass COMPONENT_TYPE_APPLICATION = Scene.v().getSootClass("android.app.Application");
		public static SootClass COMPONENT_TYPE_GCMBASEINTENTSERVICECLASS = Scene.v().getSootClass("com.google.android.gcm.GCMBaseIntentService");
		public static SootClass COMPONENT_TYPE_GCMLISTENERSERVICECLASS = Scene.v().getSootClass("com.google.android.gms.gcm.GcmListenerService");
		
		// necessary for cases in which more than one Soot analysis session is needed
		public static void reInitializeComponentTypeClasses() {
			COMPONENT_TYPE_ACTIVITY = Scene.v().getSootClass("android.app.Activity");
			COMPONENT_TYPE_SERVICE = Scene.v().getSootClass("android.app.Service");
			COMPONENT_TYPE_RECEIVER = Scene.v().getSootClass("android.content.BroadcastReceiver");
			COMPONENT_TYPE_PROVIDER = Scene.v().getSootClass("android.content.ContentProvider");
			COMPONENT_TYPE_UNKNOWN = Scene.v().getSootClass("java.lang.Object");
			
			COMPONENT_TYPE_APPLICATION = Scene.v().getSootClass("android.app.Application");
			COMPONENT_TYPE_GCMBASEINTENTSERVICECLASS = Scene.v().getSootClass("com.google.android.gcm.GCMBaseIntentService");
			COMPONENT_TYPE_GCMLISTENERSERVICECLASS = Scene.v().getSootClass("com.google.android.gms.gcm.GcmListenerService");
		}
		
		public static final SootClass[] component_type_classes = 
			{COMPONENT_TYPE_ACTIVITY, COMPONENT_TYPE_SERVICE,  COMPONENT_TYPE_RECEIVER, COMPONENT_TYPE_PROVIDER, 
			COMPONENT_TYPE_APPLICATION, COMPONENT_TYPE_GCMBASEINTENTSERVICECLASS, COMPONENT_TYPE_GCMLISTENERSERVICECLASS,
			COMPONENT_TYPE_UNKNOWN};
		public static final String[] component_type_names = {"Activity", "Service", "BroadcastReceiver", "ContentProvider", "Application"};

		public static FastHierarchy fhar = null;
		public static String getComponentType(SootClass cls) {
			try {
				if (fhar==null) {
					fhar = Scene.v().getOrMakeFastHierarchy();
				}
				if (fhar.isSubclass(cls, iccAPICom.COMPONENT_TYPE_ACTIVITY))
					return "Activity";
				if (fhar.isSubclass(cls, iccAPICom.COMPONENT_TYPE_SERVICE) ||
					fhar.isSubclass(cls, iccAPICom.COMPONENT_TYPE_GCMBASEINTENTSERVICECLASS) ||
					fhar.isSubclass(cls, iccAPICom.COMPONENT_TYPE_GCMLISTENERSERVICECLASS))
					return "Service";
				if (fhar.isSubclass(cls, iccAPICom.COMPONENT_TYPE_RECEIVER))
					return "BroadcastReceiver";
				if (fhar.isSubclass(cls, iccAPICom.COMPONENT_TYPE_PROVIDER))
					return "ContentProvider";
				if (fhar.isSubclass(cls, iccAPICom.COMPONENT_TYPE_APPLICATION))
					return "Application";
				return "Unknown";
			}
			catch (Exception e) {
				e.printStackTrace();
				return "Unknown";
			}
		}

		public static String getComponentTypeActive(SootClass cls) {
			final Hierarchy har = Scene.v().getActiveHierarchy();
			if (har.isClassSubclassOf(cls, iccAPICom.COMPONENT_TYPE_ACTIVITY))
				return "Activity";
			if (har.isClassSubclassOf(cls, iccAPICom.COMPONENT_TYPE_SERVICE) ||
				har.isClassSubclassOf(cls, iccAPICom.COMPONENT_TYPE_GCMBASEINTENTSERVICECLASS) ||
				har.isClassSubclassOf(cls, iccAPICom.COMPONENT_TYPE_GCMLISTENERSERVICECLASS))
				return "Service";
			if (har.isClassSubclassOf(cls, iccAPICom.COMPONENT_TYPE_RECEIVER))
				return "BroadcaseReceiver";
			if (har.isClassSubclassOf(cls, iccAPICom.COMPONENT_TYPE_PROVIDER))
				return "ContentProvider";
			if (har.isClassSubclassOf(cls, iccAPICom.COMPONENT_TYPE_APPLICATION))
				return "Application";
			return "Unknown";
		}
		
		public enum EVENTCAT {
			// all categories
			ALL,
			// SYSTEM
			APPLICATION_MANAGEMENT, SYSTEM_STATUS, LOCATION_STATUS, HARDWARE_MANAGEMENT, NETWORK_MANAGEMENT,
			// UI
			APP_BAR, DIALOG, MEDIA_CONTROL, VIEW, WIDGET
		}
	
}

/* vim :set ts=4 tw=4 tws=4 */
