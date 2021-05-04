package revealdroid.features.util;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.validators.PositiveInteger;

public class FeatureExtractionOptions {	
	@Parameter(names = {"-m","-maldir"}, description = "directory with apps samples to analyze", required = true)
	public String appsDir;
	
	@Parameter(names = {"-t","-timeout"}, description = "Specify a timeout in minutes. Must be an integer value.", validateWith = PositiveInteger.class, required = true)
	public Integer timeout;
	
	@Parameter(names = {"-a","-android-home"}, description = "android sdks platforms directory", required = true)
	public String androidPlatformsDir;
	
	@Parameter(names = {"-r","-arff"}, description = "arff file to store extracted features to", required = false)
	public String arffFileName;
	
	@Parameter(names = {"-i","-inarff"}, description = "arff file to read features from", required = false)
	public String inArffFileName = "data.arff";
	
	@Parameter(names = {"-k","-apksfile"}, description = "file with apks already processed---one per line and looks at substring of line", required = false)
	public String apksProcessedFile = "apks_processed.txt";
	
	@Parameter(names = {"-c","-checkinput"}, description = "only process files from input file", required = false)
	public boolean checkInputFile = false;

}
