package revealdroid.features.permission.used;

import edu.uci.seal.PermMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.xmlpull.v1.XmlPullParserException;
import revealdroid.StopWatch;
import revealdroid.Util;
import revealdroid.features.config.ApkConfig;
import soot.*;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.infoflow.android.manifest.ProcessManifest;
import soot.options.Options;
import soot.util.Chain;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by joshua on 5/2/17.
 * @author joshua
 */
public class ExtractPermissions extends SceneTransformer {

    boolean DEBUG = false;
    Logger logger = LoggerFactory.getLogger(ExtractPermissions.class);
    static String FW_MAP_PATH="res/pmaps/framework/framework-map-22.txt";
    static String PSCOUT_411_PATH="res/pscout_maps/mapping_4.1.1.csv";
    static String PSCOUT_ROOT_PATH="res/pscout_maps";

    static Map<String,String> fwMsigPerm = new LinkedHashMap<String,String>();
    private static PermMapper pm;

    static Set<String> invokedMethodSigs = new LinkedHashSet<String>();
    static Set<String> usedPermissions = new LinkedHashSet<String>();

    public ExtractPermissions(String apkFilePath) {
        soot.G.reset();
        ApkConfig.apkFilePath = apkFilePath;
    }

    public static void main(String[] args) {
        Logger logger = Util.setupSiftingLogger(ExtractPermissions.class);

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        String apkFilePath = args[0];

        try {

            ProcessManifest m = new ProcessManifest(apkFilePath);



            File apkFile = new File(apkFilePath);
            MDC.put("apkName", apkFile.getName());
            String apkFileName = apkFile.getName();
            String apkFileBase = apkFileName.substring(0, apkFileName.lastIndexOf("."));
            String usedPermFilename = "data" + File.separator + "used_perm" + File.separator + apkFile.getParentFile().getName() + "_" + apkFileBase + "_usedperm.txt";
            File usedPermFile = new File(usedPermFilename);

            String sigPermFilename = "data" + File.separator + "msig_perm" + File.separator + apkFile.getParentFile().getName() + "_" + apkFileBase + "_msigperm.txt";
            File sigPermFile = new File(sigPermFilename);

            fwMsigPerm = processAPIMappingFileAxplorer(new File(FW_MAP_PATH));

            pm = new PermMapper(PSCOUT_ROOT_PATH);
            //pm.processMappingFileCsv(PSCOUT_411_PATH);

            ExtractPermissions ep = new ExtractPermissions(apkFilePath);
            ep.run();


            System.out.println("Requested permissions:");
            for (String perm : m.getPermissions()) {
                System.out.println(perm);
            }


            FileWriter sigPermWriter = new FileWriter(sigPermFile);
            System.out.println("method signature and permission pairs:");
            for (String mSig : invokedMethodSigs) {
                if (pm.mApiPrmMap.containsKey(mSig)) {
                    Set<String> perms = pm.mApiPrmMap.get(mSig);
                    for (String perm : perms) {
                        String outPair = mSig + "#" + perm;
                        System.out.println(outPair);
                        sigPermWriter.write(outPair + "\n");
                    }
                }
            }
            sigPermWriter.close();

            FileWriter usedPermWriter = new FileWriter(usedPermFile);
            System.out.println("used permissions:");
            for (String usedPerm : usedPermissions) {
                System.out.println(usedPerm);
                usedPermWriter.write(usedPerm + "\n");
            }
            usedPermWriter.close();

        } catch (IOException e) {
            e.printStackTrace();
        } catch (XmlPullParserException e) {
            e.printStackTrace();
        }



        stopWatch.stop();
        String exitMsg = "Used permission extraction time: " + stopWatch.getElapsedTime() + " ms";
        logger.debug(exitMsg);
        System.out.println(exitMsg);
    }

    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {
        revealdroid.features.util.Util.setupSoot();

        System.out.println("no. of application classes: " + Scene.v().getApplicationClasses().size());
        logger.debug("no. of application classes: " + Scene.v().getApplicationClasses().size());

        for (SootClass sootClass : Scene.v().getApplicationClasses()) {
            if (DEBUG) {
                System.out.println(sootClass + " invokes methods from the following Android packages:");
            }
            List<InvokeStmt> invokeStmtsToAnalyze = new ArrayList<InvokeStmt>();
            for (SootMethod method : sootClass.getMethods() ) {
                if (method.isConcrete()) {
                    Body body = method.retrieveActiveBody();
                    PackManager.v().getPack("jtp").apply(body);
                    if( Options.v().validate() ) {
                        body.validate();
                    }
                }

                if (method.hasActiveBody()) {
                    Body body = method.getActiveBody();
                    Chain<Unit> units = body.getUnits();

                    for (Unit unit : units) {
                        if (unit instanceof InvokeStmt) {
                            invokeStmtsToAnalyze.add((InvokeStmt)unit);
                        }
                    }
                }
            }
            for (InvokeStmt stmt : invokeStmtsToAnalyze) {
                extractFeaturesFromStmt(stmt);
            }
        }
    }

    private synchronized void extractFeaturesFromStmt(InvokeStmt unit) {
        InvokeStmt invokeStmt = unit;
        InvokeExpr expr = invokeStmt.getInvokeExpr();
        String invokedPackageName = expr.getMethod().getDeclaringClass().getPackageName();
        if (invokedPackageName.startsWith("android.") || invokedPackageName.startsWith("com.android.")) {
            String fullMethodName = expr.getMethod().getDeclaringClass().getName() + "." + expr.getMethod().getName();
            if (DEBUG) {
                System.out.println("\t" + invokedPackageName);
                System.out.println("\t" + fullMethodName);
            }

            String mSig = expr.getMethod().getSignature();
            if ( pm.mApiPrmMap.containsKey(mSig) ) {
                invokedMethodSigs.add(mSig);
                Set<String> perms = pm.mApiPrmMap.get(mSig);
                for (String perm : perms) {
                    logger.trace(mSig + "#" + perm);
                    usedPermissions.add(perm);
                }
            }
        }
    }

    /*
     * @author alireza
     */
    private static Map<String, String> processAPIMappingFileAxplorer(File mappingFile) {
        Map<String, String> result = new HashMap<>();
        Pattern mapPattern = Pattern.compile("(.*)\\.(.*)\\((.*)\\)(.*) :: (.*)");
        String line;
        try (BufferedReader reader = new BufferedReader(new FileReader(mappingFile))) {
            while ((line = reader.readLine()) != null) {
                Matcher matcher = mapPattern.matcher(line);
                if (matcher.find()) {
                    String className = matcher.group(1);
                    String methodName = matcher.group(2);
                    String paramTypes = matcher.group(3);
                    List<String> paramTypesList = new ArrayList<>();
                    for (String paramType : paramTypes.split(",")) {
                        if (paramType.startsWith("[")){
                            paramType = paramType.substring(1).concat("[]");
                        }
                        paramTypesList.add(paramType);
                    }
                    String returnType = matcher.group(4);
                    String perm = matcher.group(5);
                    String methodSignature =  String.format("<%s: %s %s(%s)>", className, returnType,
                            methodName, String.join(",", paramTypesList));
                    result.put(methodSignature, perm);
                } else {
                    System.err.format("Method is invalid %s", line);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    public void run() {
        Options.v().set_whole_program(true);
        PackManager.v().getPack("wjtp").add(new Transform("wjtp.eup", this));
        PackManager.v().getPack("wjtp").apply();
    }
}
