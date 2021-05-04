/**
 * File: src/reporter/reportOpts.java
 * -------------------------------------------------------------------------------------------
 * Date			Author      Changes
 * -------------------------------------------------------------------------------------------
 * 01/06/16		hcai		created; dealing with options in the statistics reporting
 * 01/12/16		hcai		added option giving taint source and sink list
 * 01/15/16		hcai		added option giving categorized taint source and sink list
 * 01/21/16		hcai		added option giving callback interface list (CallbackClasses.txt)
 * 01/27/16		hcai		added option giving categorized callback interfaces (events)
 * 05/13/16		hcai		added the 'calltree' option enabling which will lead to the construction of call tree from traces
*/
package reporters;

import java.util.ArrayList;
import java.util.List;

public class reportOpts {
	protected boolean debugOut = false;
	protected String traceFile = null;
	// simple uniform list of source and sinks
	protected String srcsinkFile = null;
	protected String callbackFile = null;
	
	// categorized sources and sinks
	protected String catsrc = null;
	protected String catsink = null;
	protected String catCallbackFile = null;

	protected String apkdir = null;
	protected String firstapk = null;
	protected String secondapk = null;

	// whether build dynamic calltree in addition to dynamic callgraph (always build)
	protected boolean calltree = false; 
	
	// just for collecting features for ML-based classification
	protected boolean featuresOnly = false;
	
	public String[] process(String[] args) {
		List<String> argsFiltered = new ArrayList<String>();
		boolean allowPhantom = true;
		
		for (int i = 0; i < args.length; ++i) {
			String arg = args[i];

			if (arg.equals("-debug")) {
				debugOut = true;
			}
			else if (arg.equals("-trace")) {
				traceFile = args[i+1];
				i++;
			}
			else if (arg.equals("-srcsink")) {
				srcsinkFile = args[i+1];
				i++;
			}
			else if (arg.equals("-catsrc")) {
				catsrc = args[i+1];
				i++;
			}
			else if (arg.equals("-catsink")) {
				catsink = args[i+1];
				i++;
			}
			else if (arg.equals("-callback")) {
				callbackFile = args[i+1];
				i++;
			}
			else if (arg.equals("-catcallback")) {
				catCallbackFile = args[i+1];
				i++;
			}
			else if (arg.equals("-apkdir")) {
				apkdir = args[i+1];
				i++;
			}
			else if (arg.equals("-firstapk")) {
				firstapk = args[i+1];
				i++;
			}
			else if (arg.equals("-secondapk")) {
				secondapk = args[i+1];
				i++;
			}
			else if (arg.equals("-nophantom")) {
				allowPhantom = false;
			}
			else if (arg.equals("-calltree")) {
				calltree = true;
			}
			else if (arg.equals("-featuresOnly")) {
				featuresOnly = true;
			}
			else {
				argsFiltered.add(arg);
			}
		}
		
		if (allowPhantom) {
			argsFiltered.add("-allowphantom");
		}
		
		String[] arrArgsFilt = new String[argsFiltered.size()];
		return (String[]) argsFiltered.toArray(arrArgsFilt);
	}
}

/* vim :set ts=4 tw=4 tws=4 */

