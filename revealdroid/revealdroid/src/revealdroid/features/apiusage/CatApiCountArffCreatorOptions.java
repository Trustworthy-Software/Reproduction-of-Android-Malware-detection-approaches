package revealdroid.features.apiusage;

import com.beust.jcommander.Parameter;

public class CatApiCountArffCreatorOptions {
	@Parameter(names = {"-a","-apksDir"}, description = "directory where each subfolder is a family containing apks", required = true)
	public String apksDir;
	
	@Parameter(names = {"-c","-catApiCountDir"}, description = "directory with categorized api count text files, previously extracted", required = true)
	public String catApiCountDir;
	
	@Parameter(names = {"-r","-outArff"}, description = "filename of arff to construct from categorized api counts", required = true)
	public String outArffFileName;
}
