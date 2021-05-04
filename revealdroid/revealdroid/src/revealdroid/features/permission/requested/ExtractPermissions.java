package revealdroid.features.permission.requested;

import org.slf4j.Logger;
import org.slf4j.MDC;
import org.xmlpull.v1.XmlPullParserException;
import revealdroid.StopWatch;
import revealdroid.Util;
import soot.jimple.infoflow.android.manifest.ProcessManifest;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Created by joshua on 4/19/17.
 */
public class ExtractPermissions {

    public static void main(String[] args) {
        Logger logger = Util.setupSiftingLogger(ExtractPermissions.class);

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        String apkFilePath = args[0];
        File apkFile = new File(apkFilePath);
        MDC.put("apkName", apkFile.getName());
        String apkFileName = apkFile.getName();
        String apkFileBase = apkFileName.substring(0,apkFileName.lastIndexOf("."));
        String outFileName = "data" + File.separator + "perm" + File.separator + apkFile.getParentFile().getName() + "_" + apkFileBase + "_perm.txt";
        File outFile = new File(outFileName);

        try {
            ProcessManifest m = new ProcessManifest(apkFilePath);
            FileWriter writer = new FileWriter(outFile);
            for (String p : m.getPermissions()) {
                writer.write(p + "\n");
            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (XmlPullParserException e) {
            e.printStackTrace();
        }

        stopWatch.stop();
        logger.debug("permission extraction has run for " + stopWatch.getElapsedTime() + " ms");
        System.out.println("permission extraction has run for " + stopWatch.getElapsedTime() + " ms");
    }


}
