package revealdroid.enduser.cli;

import edu.uci.seal.cases.analyses.ReflectUsageTransformer;
import revealdroid.features.apiusage.ExtractApiUsageFeatures;

import java.io.File;

/**
 * Created by joshua on 11/12/16.
 */
public class AnalyzeAppPRN {

    public static final String revealdroidDir=System.getenv("RD_HOME")+ File.separator;

    public static void main(String[] args) {
        String apkFileName = args[0];
        //ExtractApiUsageFeatures.skipIfFeatureFileExists=false;
        //ExtractApiUsageFeatures.main(new String[]{apkFileName});

        ReflectUsageTransformer.main(new String[]{apkFileName});
    }
}
