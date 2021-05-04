package revealdroid.features.apiusage;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import android.*;
import revealdroid.StopWatch;

public class ExtractAndroidPackageNames {
	public static void main(String[] args) {
		StopWatch stopWatch = new StopWatch();
		stopWatch.start();
		
		runAndroidPackagesTransformer();
		
		stopWatch.stop();
		System.out.println("Android API Usage Extraction has run for " + stopWatch.getElapsedTime() + " ms");
	}

	public static void runAndroidPackagesTransformer() {
		AndroidPackagesTransformer transformer = new AndroidPackagesTransformer();
		transformer.run();
	}
	
}
