/**
 * File: src/dynCG/Options.java
 * -------------------------------------------------------------------------------------------
 * Date			Author      Changes
 * -------------------------------------------------------------------------------------------
 * 10/19/15		hcai		created; for command-line argument processing for DynCG Instrumenter
 * 02/04/17		hcai		added option for monitoring events (triggers of event-handling callbacks)
 *
*/
package dynCG;

import java.util.ArrayList;
import java.util.List;

public class Options {
	protected boolean debugOut = false;
	protected boolean dumpJimple = false;
	protected boolean sclinit = false;
	protected boolean wrapTryCatch = false;
	protected boolean dumpFunctionList = false;
	protected boolean statUncaught = false;
	
	protected boolean instr3rdparty = false;
	
	protected boolean monitorICC = true;
	protected boolean monitorAllCalls = true;
	protected boolean monitorEvents = false;

	public boolean debugOut() { return debugOut; }
	public boolean dumpJimple() { return dumpJimple; }
	public boolean sclinit() { return sclinit; }
	public boolean wrapTryCatch() { return wrapTryCatch; }
	public boolean dumpFunctionList() { return dumpFunctionList; }
	public boolean statUncaught() { return statUncaught; }

	protected boolean instr3rdparty() { return instr3rdparty; }
	
	public boolean monitorICC() { return monitorICC; }
	protected boolean monitorAllCalls() { return monitorAllCalls; }
	protected boolean monitorEvents() { return monitorEvents; }
	
	public final static int OPTION_NUM = 7;
	
	public String[] process(String[] args) {
		List<String> argsFiltered = new ArrayList<String>();
		boolean allowPhantom = true;
		
		for (int i = 0; i < args.length; ++i) {
			String arg = args[i];

			if (arg.equals("-debug")) {
				debugOut = true;
			}
			else if (arg.equals("-dumpJimple")) {
				dumpJimple = true;
			}
			else if (arg.equals("-sclinit")) {
				sclinit = true;
			}
			else if (arg.equals("-wrapTryCatch")) {
				wrapTryCatch = true;
			}
			else if (arg.equals("-statUncaught")) {
				statUncaught = true;
			}
			else if (arg.equals("-dumpFunctionList")) {
				dumpFunctionList = true;
			}
			else if (arg.equals("-noMonitorICC")) {
				monitorICC = false; 
			}
			else if (arg.equals("-noMonitorCalls")) {
				monitorAllCalls = false; 
			}
			else if (arg.equals("-monitorEvents")) {
				monitorEvents = true; 
			}
			else if (arg.equals("-instr3rdparty")) {
				instr3rdparty = true;
			}
			else if (arg.equals("-nophantom")) {
				allowPhantom = false;
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

