package revealdroid.features.util;

import java.io.PrintWriter;

import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.unsupervised.instance.SparseToNonSparse;


public class ConvertSparseArffToDense {

	public static void main(String[] args) {
		String arffPath = args[0];
		SparseToNonSparse filter = new SparseToNonSparse();
		Instances data = revealdroid.classify.Util.buildInstancesFromFile(arffPath);
		data.setClassIndex(data.numAttributes()-1);
		try {
			filter.setInputFormat(data);
			Instances newData = Filter.useFilter(data, filter);
			
			String[] tokens = arffPath.split("\\.(?=[^\\.]+$)");
			String base = tokens[0];
			String ext = tokens[1];
			String newArffPath = base + "_nonsparse." + ext; 
			
			System.out.println("Writing non-sparse arff " + newArffPath);
			PrintWriter writer = new PrintWriter(newArffPath,"UTF-8");
			writer.write(newData.toString());
			writer.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
