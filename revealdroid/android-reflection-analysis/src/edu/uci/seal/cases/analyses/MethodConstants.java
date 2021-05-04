package edu.uci.seal.cases.analyses;


import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public class MethodConstants {

	public static String[] reflectiveGetMethods = {"getMethod","getDeclaredMethod"};
	public static Set<String> reflectiveGetMethodsSet = new LinkedHashSet<String>(Arrays.asList(reflectiveGetMethods));
	
	public static String[] reflectiveGetFieldMethods = {"getField","getDeclaredField"};
	public static Set<String> reflectiveGetFieldMethodsSet = new LinkedHashSet<String>(Arrays.asList(reflectiveGetFieldMethods));
	
	public static String[] reflectiveNewInstanceClasses = {"java.lang.Class","java.lang.reflect.Constructor"};
	public static Set<String> reflectiveNewInstanceClassesSet = new LinkedHashSet<String>(Arrays.asList(reflectiveNewInstanceClasses));
}
