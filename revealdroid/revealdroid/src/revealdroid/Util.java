package revealdroid;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import revealdroid.log.parse.LogFlowOptions;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

public class Util {

	public static void parseCommandLineOpts(String[] args, LogFlowOptions opts) {
		JCommander cmd = null;
		try {
			cmd = new JCommander(opts);
			cmd.parse(args);
	 	} catch (ParameterException ex) {
	        System.out.println(ex.getMessage());
	        cmd.usage();
	        System.exit(1);
	    }
	}
	
	/**
	 * @param fileName file name to be split into base name and extension
	 * @return a string array with the base name first and extension second
	 */
	public static String[] extractBaseNameAndExtension(String fileName) {
		File file = new File(fileName);
		String[] tokens = file.getName().split("\\.(?=[^\\.]+$)");
		return tokens;
	}
	
	public static Logger setupSiftingLogger(@SuppressWarnings("rawtypes") Class toolClass) {
		LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
	    JoranConfigurator configurator = new JoranConfigurator();
	    lc.reset();
	    lc.putProperty("tool-name", toolClass.toString().replaceAll(" ", "_"));
	    configurator.setContext(lc);
	    try {
			configurator.doConfigure("logback-siftingappender.xml");
		} catch (JoranException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    
	    Logger logger = LoggerFactory.getLogger(toolClass);
	    return logger;
	}
	
	public static Logger setupThreadLogger(@SuppressWarnings("rawtypes") Class inClass) {
		LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
	    JoranConfigurator configurator = new JoranConfigurator();
	    lc.reset();
	    lc.putProperty("toolName", inClass.getName());
	    configurator.setContext(lc);
	    try {
			configurator.doConfigure("logback-fileAppender.xml");
		} catch (JoranException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    
	    Logger logger = LoggerFactory.getLogger(inClass);
	    return logger;
	}
}
