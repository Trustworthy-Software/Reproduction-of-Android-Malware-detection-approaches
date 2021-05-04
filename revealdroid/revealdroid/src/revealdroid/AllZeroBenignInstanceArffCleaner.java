package revealdroid;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class AllZeroBenignInstanceArffCleaner {
	public static void main(String[] args) {
		String arffFilename = args[0];
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(arffFilename));
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	    try {
	        String line = br.readLine();
	        String cleanFilename = "removed_zero_instances_data.arff";
	        FileWriter writer = new FileWriter(cleanFilename);

	        int allZerosBenignInstancesCount = 0;
	        int nonZerosBenignInstancesCount = 0;
	        String skipStr = ",0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,Benign";
	        while (line != null) {
				if (line.toLowerCase().contains("benign")) {
					if (!line.contains(skipStr)) {
						System.out.println(line);
						writer.write(line + '\n');
						nonZerosBenignInstancesCount++;
					} else {

						allZerosBenignInstancesCount++;

					}
				}
	            line = br.readLine();
	        }
	        
	        writer.close();
	        br.close();
	        
	        int totalBenignInstancesCount = allZerosBenignInstancesCount+nonZerosBenignInstancesCount;
	        System.out.println("number of benign instances with all zeros: " + allZerosBenignInstancesCount);
	        System.out.println("number of benign instances with some non-zeros: " + nonZerosBenignInstancesCount);
	        System.out.println("total no. of benign instances: " + totalBenignInstancesCount);
	    } catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
}
