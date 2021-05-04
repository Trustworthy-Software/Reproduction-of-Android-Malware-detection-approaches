package revealdroid.features.intent;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.validators.PositiveInteger;

public class IntentFeatureExtractionOptions {	
	@Parameter(names = {"-m","-maldir"}, description = "directory with malware samples to analyze", required = true)
	public String malDir;
	
	@Parameter(names = {"-t","-timeout"}, description = "Specify a timeout in minutes. Must be an integer value.", validateWith = PositiveInteger.class, required = true)
	public Integer timeout;
}
