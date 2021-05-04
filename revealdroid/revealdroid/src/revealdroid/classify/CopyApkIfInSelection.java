package revealdroid.classify;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.io.FileUtils;

public class CopyApkIfInSelection {

	public static void main(String[] args) {
		String selectionFileName = args[0];
		String srcDirName = args[1];
		String dstDirName = args[2];
		
		Set<String> selectedHashes = TrainTestOnSelection.readHashesFromFile(selectionFileName);
		File srcDir = new File(srcDirName);
		
		Set<File> selectedFiles = new LinkedHashSet<File>();
		for (File file : FileUtils.listFiles(srcDir, new String[] {"apk"}, true) ) {
			String hashFromFile = TrainTestOnSelection.extractHashFromName(file.getName());
			if (selectedHashes.contains(hashFromFile)) {
				System.out.println(file);
				selectedFiles.add(file);
			}
		}
		
		System.out.println("No. of selected files: " + selectedFiles.size());
		
		for (File file : selectedFiles) {
			String currParentName = file.getParentFile().getName();
			String dstStr = dstDirName + File.separator + currParentName; 
			String cpyStr = "Copying " + file.getName() + " of " + file.getParent() + " to " + dstStr;
			System.out.println(cpyStr);
			try {
				FileUtils.copyFileToDirectory(file, new File(dstStr));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

}
