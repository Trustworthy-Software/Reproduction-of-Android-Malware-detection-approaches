package revealdroid.classify;

import com.beust.jcommander.Parameter;

public class TrainTestOnSelectionOptions {
	@Parameter(names = {"-rs","-trainselect"}, description = "file with selected hashes for training", required = true)
	public String trainSelectFile;
	
	@Parameter(names = {"-es","-testselect"}, description = "file with selected hashes for testing", required = true)
	public String testSelectFile;
	
	@Parameter(names = {"-ra","-trainarff"}, description = "arff file for training", required = true)
	public String trainArff;
	
	@Parameter(names = {"-ea","-testarff"}, description = "arff file for testing", required = true)
	public String testArff;
	
	@Parameter(names = {"-c","-classifier"}, description = "classifier with the following possible arguments: svm, ib1, j48", required = true)
	public String classifier;
}
