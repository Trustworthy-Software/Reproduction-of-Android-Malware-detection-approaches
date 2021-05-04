package revealdroid.features.apiusage;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map.Entry;

import revealdroid.enduser.cli.AnalyzeSingleAppForReputationUsingCatApicount;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;

public class CatApiCountUtil {

	public static Instances buildInstancesForCatApiCount(List attributes) {
		Instances instances = new Instances("apps", (ArrayList) attributes, 2000);
		return instances;
	}

	public static Instance buildSingleCatApiCountInstance(String family, String apkName, Map<String, Integer> catApiCounts, String labelsFileName) {
		boolean DEBUG = false;
		List<String> labels = readLabelsFromFile(labelsFileName);
		List attributes = buildCatApiCountAttributes(labels);
		Instances instances = buildInstancesForCatApiCount(attributes);
		Instance instance = new DenseInstance(instances.numAttributes());
		instance.setValue(instances.attribute("apkName"), apkName + ".apk");
		if (family.equals("?")) {
			instance.setMissing(instances.attribute("class"));
		} else {
			instance.setValue(instances.attribute("class"), family);
		}

		// initialize numeric attributes to 0
		for (int i = 1; i < instance.numAttributes() - 1; i++) {
			instance.setValue(instances.attribute(i), 0);
		}

		for (Entry<String, Integer> entry : catApiCounts.entrySet()) {
			String category = entry.getKey();
			Integer count = entry.getValue();
			if (DEBUG)
				System.out.println("\t\t" + category + ", " + count);
			instance.setValue(instances.attribute(category), count);
		}
		return instance;
	}

	public static Instance buildCatApiCountInstance(Instances instances, String family, String apkName, Map<String, Integer> catApiCounts) {
		boolean DEBUG = false;
		Instance instance = new DenseInstance(instances.numAttributes());
		instance.setValue(instances.attribute("apkName"), apkName + ".apk");
		instance.setValue(instances.attribute("class"), family);

		// initialize numeric attributes to 0
		for (int i = 1; i < instance.numAttributes() - 1; i++) {
			instance.setValue(instances.attribute(i), 0);
		}

		for (Entry<String, Integer> entry : catApiCounts.entrySet()) {
			String category = entry.getKey();
			Integer count = entry.getValue();
			if (DEBUG)
				System.out.println("\t\t" + category + ", " + count);
			instance.setValue(instances.attribute(category), count);
		}
		return instance;
	}

	public static List<String> readLabelsFromDir(String apksDirName) {
		File apksDir = new File(apksDirName);
		File[] directories = apksDir.listFiles(File::isDirectory);
		System.out.println("Directories:");
		ArrayList<String> families = new ArrayList<String>();
		for (File dir : directories) {
			families.add(dir.getName());
		}
		return families;
	}

	public static List<String> readLabelsFromFile(String fileName) {
		boolean DEBUG = false;
		List<String> labels = new ArrayList<String>();
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(fileName), Charset.defaultCharset())) {
			String line = null;
			while ((line = reader.readLine()) != null) {
				if (DEBUG)
					System.out.println(line);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return labels;

	}

	public static List buildCatApiCountAttributes(List<String> labels) {
		boolean DEBUG = false;
		// key: method, value: category
		Map<String, String> methodCategories = new LinkedHashMap<String, String>();
		buildMethodCategories(methodCategories);

		Set<String> categories = new TreeSet<String>(methodCategories.values());

		if (DEBUG) {
			System.out.println("Categories:");
			for (String category : categories) {
				System.out.println(category);
			}
		}

		List attributes = new ArrayList<Attribute>();
		Attribute apkNameAttr = new Attribute("apkName", (FastVector) null);
		attributes.add(apkNameAttr);
		for (String category : categories) {
			Attribute attr = new Attribute(category);
			attributes.add(attr);
		}

		Collections.sort(labels);
		Attribute classAttr = new Attribute("class", labels);
		attributes.add(classAttr);
		return attributes;
	}

	public static void buildCatApiAcounts(Map<String, Integer> catApiCounts, String apiCountFileName) {
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(apiCountFileName), Charset.defaultCharset())) {
			String line = null;
			while ((line = reader.readLine()) != null) {
				String[] tokens = line.split(",");
				String category = tokens[0].trim();
				Integer count = Integer.parseInt(tokens[1]);

				catApiCounts.put(category, count);
			}
		} catch (IOException x) {
			x.printStackTrace();
		}
	}

	public static void buildMethodCategories(Map<String, String> methodCategories) {
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(AnalyzeSingleAppForReputationUsingCatApicount.revealdroidDir+"prmDomains.txt"), Charset.defaultCharset())) {
			String line = null;
			while ((line = reader.readLine()) != null) {
				if (!line.contains("%")) {
					continue;
				}
				String[] tokens = line.split("%");
				String category = tokens[0].trim();
				String method = tokens[1].trim();

				methodCategories.put(method, category);
			}
		} catch (IOException x) {
			x.printStackTrace();
		}
	}

}
