package revealdroid.virustotal;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;

import revealdroid.classify.ZeroDayClassifierTest;

public class MalgenomeFamilyOrganizer {

	public static void main(String[] args) {
		String familiesFile = args[0];
		String virusShareDir = args[1];
		String outputDir = args[2];
		
		// key: hash, value: family
		Map<String,String> hashFamilyMap = new LinkedHashMap<String,String>();
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(familiesFile), Charset.defaultCharset())) {
		    String line = null;
		    int lineCount = 1;
		    while ((line = reader.readLine()) != null) {
		        //System.out.println(line);
		        String[] arrowTokens = line.split(">");
		        String familyName = arrowTokens[0].trim();
		        String[] colonTokens = arrowTokens[1].split(":");
		        String virusTotalFamilyName = colonTokens[0].trim();
		        String malwareHash = colonTokens[1].trim();
		        System.out.println(lineCount + ", " + familyName + ", " + virusTotalFamilyName + ", " + malwareHash);
		        lineCount++;
		        hashFamilyMap.put(malwareHash, familyName);
		    }
		    
		    Path outputPath = Paths.get(outputDir);
		    if (!Files.exists(outputPath)) {
		    	Files.createDirectory(outputPath);
		    	for (String familyName : hashFamilyMap.values()) {
		    		Path familyPath = Paths.get(outputPath + File.separator + familyName);
		    		if (!Files.exists(familyPath)) {
		    			Files.createDirectory(familyPath);
		    		}
		    	}
		    }
		    
	        File virusShareDirFile = new File(virusShareDir);
		    for (File file : FileUtils.listFiles(virusShareDirFile, new String[] {"apk"}, true) ) {
		    	Path path = Paths.get(file.getAbsolutePath());
		    	if (hasKnownFamily(path,hashFamilyMap.keySet())) {
		    		String matchingHash = findMatchingHash(path,hashFamilyMap.keySet());
		    		String familyName = hashFamilyMap.get(matchingHash);
		    		String familyPath = outputPath + File.separator + familyName + "/";
					System.out.println("Copy " + file + " to " + familyPath);
					FileUtils.copyFileToDirectory(file, new File(familyPath));
				 }
		    }
		    
		    
		} catch (IOException x) {
		    x.printStackTrace();
		    
		}
		
		

	}

	private static boolean hasKnownFamily(Path f, Set<String> malwareHashes) {
		for (String malwareHash : malwareHashes) {
			if (f.getFileName().toString().contains(malwareHash)) {
				return true;
			}
		}
		return false;
	}
	
	private static String findMatchingHash(Path f, Set<String> malwareHashes) {
		for (String malwareHash : malwareHashes) {
			if (f.getFileName().toString().contains(malwareHash)) {
				return malwareHash;
			}
		}
		return "";
	}

}
