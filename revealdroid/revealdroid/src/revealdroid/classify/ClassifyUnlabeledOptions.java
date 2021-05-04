package revealdroid.classify;

import com.beust.jcommander.Parameter;

public class ClassifyUnlabeledOptions {	
	@Parameter(names = {"-r","-trainarff"}, description = "arff file for training", required = true)
	public String trainArff;
	
	@Parameter(names = {"-u","-unlabeledarff"}, description = "arff file with instances to label", required = true)
	public String unlabeledArff;

}
