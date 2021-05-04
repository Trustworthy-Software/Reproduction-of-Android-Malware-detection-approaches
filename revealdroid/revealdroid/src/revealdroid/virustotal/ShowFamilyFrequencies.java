package revealdroid.virustotal;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

public class ShowFamilyFrequencies {

	public static void main(String[] args) {
		String familyFileName = args[0];

		// key: family name, value: list of hashes belonging to the family
		Map<String,List<String>> familyHashes = new LinkedHashMap<String,List<String>>();
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(familyFileName), Charset.defaultCharset())) {
		    String line = null;
		    int lineCount = 1;
		    while ((line = reader.readLine()) != null) {
		    	if (!line.contains(":")) {
		    		continue;
		    	}
		    	String[] tokens = line.split(":");
		    	String family = tokens[0].trim();
		    	String hash = tokens[1].trim();
		    	
		    	List<String> hashes = null;
		    	if (familyHashes.containsKey(family)) {
		    		hashes = familyHashes.get(family);
		    	}
		    	else {
		    		hashes = new ArrayList<String>();
		    	}
		    	hashes.add(hash);
		    	familyHashes.put(family,hashes);
		    }
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		
		Map<Integer,List<String>> countsFamilies = new TreeMap<Integer,List<String>>();
		for (Entry<String,List<String>> entry : familyHashes.entrySet()) {
			String family = entry.getKey();
			List<String> hashes = entry.getValue();
			
			List<String> families = null;
			if (countsFamilies.containsKey(hashes.size())) {
				families = countsFamilies.get(hashes.size());
			}
			else {
				families = new ArrayList<String>();
			}
			families.add(family);
			countsFamilies.put(hashes.size(),families);
		}
		
		for (Integer hashesCount : countsFamilies.keySet()) {
			List<String> families = countsFamilies.get(hashesCount);
			for (String family : families) {
				System.out.println(family + " : " + hashesCount);
			}
		}

	}

}
