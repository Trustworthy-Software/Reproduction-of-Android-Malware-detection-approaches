package revealdroid.features.intent;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.validators.PositiveInteger;

public class IntentFeaturesAdderOptions {	
	@Parameter(names = {"-a","-actionsdir"}, description = "directory with Intent actions files", required = true)
	public String actionsDir;
	
	@Parameter(names = {"-d","-dataarff"}, description = "arff file with original features", required = true)
	public String arffFile;

	@Parameter(names = {"-p","-prefix"},description="prefix of feature file", required=false)
	public String prefix = null;
}
