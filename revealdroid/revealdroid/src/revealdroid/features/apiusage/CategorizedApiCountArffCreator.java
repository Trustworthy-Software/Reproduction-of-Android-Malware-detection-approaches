package revealdroid.features.apiusage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;

public class CategorizedApiCountArffCreator {

	public static void main(String[] args) {
		
		JCommander cmd = null;
		CatApiCountArffCreatorOptions opts = new CatApiCountArffCreatorOptions();
		try {
			cmd = new JCommander(opts);
			cmd.parse(args);
	 	} catch (ParameterException ex) {
	        System.out.println(ex.getMessage());
	        cmd.usage();
	        System.exit(1);
	    }
		
		String catApiCountDirName = opts.catApiCountDir;
		File catApiCountDir = new File(catApiCountDirName);
		
		String outArffFileName = opts.outArffFileName;
		File outArffFile = new File(outArffFileName);
		
		List<String> labels = CatApiCountUtil.readLabelsFromDir(opts.apksDir);
		List attributes = CatApiCountUtil.buildCatApiCountAttributes(labels);
		
		System.out.println("Attribute list:");
		for (Object obj : attributes) {
			Attribute attr = (Attribute)obj;
			System.out.println(obj);
		}
		Instances instances = CatApiCountUtil.buildInstancesForCatApiCount(attributes);
		
		File[] catApiCountFiles = catApiCountDir.listFiles(new FilenameFilter() {
		    public boolean accept(File dir, String name) {
		        return name.toLowerCase().endsWith("categorized_apicount.txt");
		    }
		});
		
		System.out.println("catapicount files:");
		for (File file : catApiCountFiles) {
			System.out.println(file.getName());
			
			String family = null;
			String apkName = null;
			
			Pattern p = Pattern.compile("([0-9a-zA-Z\\.]+)_(.+)_categorized_apicount.txt");
			
			Matcher m = p.matcher(file.getName());
			
			if (!m.find()) {
				throw new RuntimeException("Could not find match for " + file.getName());
			}
			
			family = m.group(1);
			apkName = m.group(2);

			System.out.println("\t\t" + family + ", " + apkName);
			Map<String,Integer> catApiCounts = new LinkedHashMap<String,Integer>();
			CatApiCountUtil.buildCatApiAcounts(catApiCounts,file.getAbsolutePath());
			
			Instance instance = CatApiCountUtil.buildCatApiCountInstance(instances, family, apkName, catApiCounts);
			instances.add(instance);
		}
		
		instances.sort(instances.attribute("class"));
		//System.out.println(instances.toString());
		
		try {
			FileWriter writer = new FileWriter(outArffFile);
			writer.write(instances.toString());
			writer.write("\n");
			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}
