package revealdroid.log.parse;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import revealdroid.Util;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

public class BatchFlowDroidLogFlowExtractor {

	private static final String SOURCE_PREFIX = "\t- ";
	private static final String PROCESSING = "Processing apk file";
	private static final String FOUND_FLOW = "Found a flow to sink ";
	private static final String FOUND_SUFFIX = ", from the following sources:";

	public static void main(String[] args) {
		LogFlowOptions opts = new LogFlowOptions();
		Util.parseCommandLineOpts(args, opts);
		
		String logFileName = opts.logFile;
		boolean DEBUG=false;

		// key: apk name, value: list of flows expressed as a string
		Map<String,List<String>> apkFlows = new LinkedHashMap<String,List<String>>();
		
		Pattern onLinePatt = Pattern.compile("on line [0-9]+");
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(logFileName), Charset.defaultCharset())) {
		    String line = null;
		    String prevApkName = null;
		    String lastApkName = null;
		    String lastSink = null;
		    boolean timedOut = false;
		    while ((line = reader.readLine()) != null) {
		    	if (line.contains(PROCESSING)) {
		    		prevApkName = lastApkName;
		    		
		    		if (prevApkName != null) {
		    			writeFlowsFor(opts, apkFlows, prevApkName, timedOut);
		    		}
		    		timedOut=false;
		    		
		    		String apkNameWithSlashes = line.substring(line.lastIndexOf(PROCESSING) + PROCESSING.length(), line.length()).trim();
		    		lastApkName = apkNameWithSlashes.substring(apkNameWithSlashes.lastIndexOf(File.separator)+1,apkNameWithSlashes.length());
		    		if (DEBUG) {
		    			//System.out.println(lastApkName);
		    		}
		    	}
		    	if (line.contains(FOUND_FLOW)) {
		    		String dirtySink = line.replaceAll(FOUND_SUFFIX, "").replaceAll(FOUND_FLOW, "");
		    		Matcher m = onLinePatt.matcher(dirtySink);
		    		String cleanSink = null;
		    		if (m.find()) {
		    			cleanSink = dirtySink.replaceAll(onLinePatt.pattern(), "");
		    			//System.out.println(dirtySink + "->" + cleanSink);
		    		}
		    		else {
		    			cleanSink = dirtySink;
		    		}
		    		lastSink=cleanSink;
		    		
		    		if (DEBUG) {
		    			//System.out.println(dirtySink);
		    		}
		    		
		    	}
		    	if (line.startsWith(SOURCE_PREFIX)) {
		    		String source = line.replaceAll(SOURCE_PREFIX, "").trim();
		    		if (DEBUG)
		    			System.out.println(source + " => " + lastSink);
		    		List<String> flows = null;
		    		if (apkFlows.containsKey(lastApkName)) {
		    			flows = apkFlows.get(lastApkName);
		    		}
		    		else {
		    			flows = new ArrayList<String>();
		    		}
		    		flows.add(source + " -> " + lastSink);
		    		apkFlows.put(lastApkName, flows);
		    	}
		    	if (line.contains("timed out")) {
		    		timedOut = true;
		    	}
		    }
		    writeFlowsFor(opts, apkFlows, lastApkName, timedOut);
		} catch (IOException e) {
		    e.printStackTrace();
		}
		
	}

	public static void writeFlowsFor(LogFlowOptions opts, Map<String, List<String>> apkFlows, String prevApkName, boolean timedOut) throws IOException {
		List<String> flows = apkFlows.get(prevApkName);
		if (flows != null && !timedOut) { 
			File fullOutDir = new File(opts.outDir + File.separator);
			if (!fullOutDir.exists()) {
				fullOutDir.mkdirs();
			}
			FileWriter writer = new FileWriter(opts.outDir + File.separator + prevApkName + "_flows.txt");
			for (String flow : flows) {
				writer.write(flow + "\n");
			}
			writer.write("\n");
			writer.close();
		}
		if (flows == null && !timedOut) {
			FileWriter writer = new FileWriter(opts.outDir + File.separator + prevApkName + "_flows.txt");
			writer.write("\n");
			writer.close();
		}
	}

}
