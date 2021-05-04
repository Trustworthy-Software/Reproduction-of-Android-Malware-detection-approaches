/**
 * File: src/intentTracker/Options.java
 * -------------------------------------------------------------------------------------------
 * Date			Author      Changes
 * -------------------------------------------------------------------------------------------
 * 09/21/15		hcai		deal with all sorts of Soot options
*/
package intentTracker;

import java.util.ArrayList;
import java.util.List;

public class Options {
	protected boolean debugOut = false;
	protected boolean dumpJimple = false;
	protected boolean perfDUAF = false;	// whether going through DUAF baseline data-flow analysis before running its extension
	protected boolean modelAndroidLC = false; // whether modeling android life-cycle by creating a dummy main before analysis
	protected boolean instr3rdparty = false;
	protected boolean debugOut() { return debugOut; }
	protected boolean dumpJimple() { return dumpJimple; }
	protected boolean perfDUAF() { return perfDUAF; }
	protected boolean modelAndroidLC() { return modelAndroidLC; }
	protected boolean instr3rdparty() { return instr3rdparty; }
	
	public String[] process(String[] args) {
		//args = super.process(args);
		
		List<String> argsFiltered = new ArrayList<String>();
		for (int i = 0; i < args.length; ++i) {
			String arg = args[i];

			if (arg.equals("-debug")) {
				debugOut = true;
			}
			else if (arg.equals("-dumpJimple")) {
				dumpJimple = true;
			}
			else if (arg.equals("-perfDUAF")) {
				perfDUAF = true;
			}
			else if (arg.equals("-modelAndroidLC")) {
				modelAndroidLC = true;
			}
			else if (arg.equals("-instr3rdparty")) {
				instr3rdparty = true;
			}
			else {
				argsFiltered.add(arg);
			}
		}
		
		String[] arrArgsFilt = new String[argsFiltered.size()];
		//return super.process( (String[]) argsFiltered.toArray(arrArgsFilt) );
		return (String[]) argsFiltered.toArray(arrArgsFilt);
	}
}

/* vim :set ts=4 tw=4 tws=4 */

