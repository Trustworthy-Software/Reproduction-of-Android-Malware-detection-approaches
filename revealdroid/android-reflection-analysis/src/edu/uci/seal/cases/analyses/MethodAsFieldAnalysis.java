package edu.uci.seal.cases.analyses;


import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.Type;
import edu.uci.seal.Config;
import edu.uci.seal.StopWatch;
import edu.uci.seal.Utils;

public class MethodAsFieldAnalysis {
	
	Logger logger = LoggerFactory.getLogger(MethodAsFieldAnalysis.class);

	public static void main(String[] args) {
		StopWatch allPhaseStopWatch = new StopWatch();
		allPhaseStopWatch.start();
		
		String apkFilePath = args[0];
		File apkFile = new File(apkFilePath);
		
		System.out.println("Analyzing apk " + apkFilePath);
		
		Logger logger = Utils.setupLogger(MethodAsFieldAnalysis.class,apkFile.getName());
		
		Config.apkFilePath = apkFilePath;
		Config.applyBodySootOptions();
		
		Scene.v().loadNecessaryClasses();
		
		logger.debug("Application classes with java.lang.reflect.Method fields:");
		for (SootClass appClass : Scene.v().getApplicationClasses()) {
			for (SootField field : appClass.getFields()) {
				Type fieldType = field.getType();
				if (appClass.getPackageName().startsWith("android.support")) {
					logger.debug("Ignoring android.support.* class: " + appClass);
					continue;
				}
				if (fieldType.toString().equals("java.lang.reflect.Method")) {
					logger.debug(appClass.getName());
					logger.debug("\thas field of type java.lang.reflect.Method: "+fieldType + " " + field.getName());
				}
			}
		}

	}

}
