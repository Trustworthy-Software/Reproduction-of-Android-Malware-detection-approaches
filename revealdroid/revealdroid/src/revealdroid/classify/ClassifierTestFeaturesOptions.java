package revealdroid.classify;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.validators.PositiveInteger;

public class ClassifierTestFeaturesOptions {	
	@Parameter(names = {"-r","-trainarff"}, description = "arff file for training", required = true)
	public String trainArff;
	
	@Parameter(names = {"-e","-testarff"}, description = "arff file for testing", required = true)
	public String testArff;
	
	@Parameter(names = {"-d", "-testdir"}, description = "directory for test apks", required = true)
	public String testDir;

}
