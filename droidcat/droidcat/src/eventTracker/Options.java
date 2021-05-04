/**
 * File: src/eventTracker/Options.java
 * -------------------------------------------------------------------------------------------
 * Date			Author      Changes
 * -------------------------------------------------------------------------------------------
 * 02/04/17		hcai		created; for instrumentation that inserts probes for monitoring all events
*/
package eventTracker;

import java.util.ArrayList;
import java.util.List;

public class Options {
	public boolean debugOut = false;
	public boolean dumpJimple = false;
	public boolean instr3rdparty = false; // whether dig into third-party libraries for the instrumentation
	
	protected boolean instrlifecycle = false; // whether tracking lifecycle events also
	
	public String catCallbackFile = null; // if this argument is given, then instrument for monitoring events also
	
	public boolean debugOut() { return debugOut; }
	public boolean dumpJimple() { return dumpJimple; }
	public boolean instr3rdparty() { return instr3rdparty; }
	public boolean instrlifecycle() { return instrlifecycle; }
	
	public String[] process(String[] args) {
		//args = super.process(args);
		boolean allowPhantom = true;
		
		List<String> argsFiltered = new ArrayList<String>();
		for (int i = 0; i < args.length; ++i) {
			String arg = args[i];

			if (arg.equals("-debug")) {
				debugOut = true;
			}
			else if (arg.equals("-dumpJimple")) {
				dumpJimple = true;
			}
			else if (arg.equals("-catcallback")) {
				catCallbackFile = args[i+1];
				i++;
			}
			else if (arg.equals("-instr3rdparty")) {
				instr3rdparty = true;
			}
			else if (arg.equals("-instrlifecycle")) {
				instrlifecycle = true;
			}
			else {
				argsFiltered.add(arg);
			}
		}
		if (allowPhantom) {
			argsFiltered.add("-allowphantom");
		}
		
		String[] arrArgsFilt = new String[argsFiltered.size()];
		//return super.process( (String[]) argsFiltered.toArray(arrArgsFilt) );
		return (String[]) argsFiltered.toArray(arrArgsFilt);
	}
}

/* vim :set ts=4 tw=4 tws=4 */

