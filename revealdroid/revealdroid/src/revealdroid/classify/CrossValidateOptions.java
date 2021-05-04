package revealdroid.classify;

import com.beust.jcommander.Parameter;

public class CrossValidateOptions {	
	@Parameter(names = {"-r","-arff"}, description = "arff file for cross-validation", required = true)
	public String arff;
	
	@Parameter(names = {"-c","-classifier"}, description = "classifier with the following possible arguments: svm, ib1, j48", required = true)
	public String classifier;
	
	@Parameter(names = {"-f","-folds"}, description = "number of folds", required = false)
	public int folds=10;

}
