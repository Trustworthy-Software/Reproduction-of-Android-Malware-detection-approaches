package revealdroid.features.apiusage;

import com.beust.jcommander.Parameter;

public class ApiUsageFeaturesAdderOptions {	
	@Parameter(names = {"-a","-apiusagedir"},description = "directory with api usage files",required = true)
	public String apiUsageDir;
	
	@Parameter(names = {"-d","-dataarff"},description = "arff file with original features",required = true)
	public String arffFile;
	
	@Parameter(names = {"-p","-prefix"},description="prefix of feature file", required=false)
	public String prefix = null;
	
	@Parameter(names={"-s","-suffix"},description="suffix of feature file",required=false)
	public String suffix = null;

}
