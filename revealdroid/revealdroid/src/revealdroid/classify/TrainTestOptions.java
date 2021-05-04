package revealdroid.classify;

import com.beust.jcommander.Parameter;

public class TrainTestOptions {
	@Parameter(names = {"-r","-trainarff"}, description = "arff file for training", required = true)
	public String trainArff;
	
	@Parameter(names = {"-e","-testarff"}, description = "arff file for testing", required = true)
	public String testArff;
	
	@Parameter(names = {"-c","-classifier"}, description = "classifier with the following possible arguments: svm, ib1, j48", required = true)
	public String classifier;
}
