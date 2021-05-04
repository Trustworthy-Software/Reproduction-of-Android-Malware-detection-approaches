package revealdroid.log.parse;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.validators.PositiveInteger;

public class LogFlowOptions {	
	@Parameter(names = {"-l","-logfile"}, description = "file with FlowDroid flow output", required = true)
	public String logFile;
	
	@Parameter(names = {"-o","-outdir"}, description = "directory to output flows files", required = false)
	public String outDir = "data/flows";
}
