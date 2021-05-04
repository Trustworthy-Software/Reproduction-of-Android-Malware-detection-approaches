package revealdroid.features.apiusage;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import revealdroid.features.config.AndroidJarConfig;
import soot.PackManager;
import soot.Scene;
import soot.SceneTransformer;
import soot.SootClass;
import soot.Transform;
import soot.options.Options;

public class AndroidPackagesTransformer extends SceneTransformer {
	
	public Set<String> androidPkgs = new LinkedHashSet<String>();

	@Override
	protected void internalTransform(String phaseName, Map<String, String> options) {
		androidPkgs = new LinkedHashSet<String>();
		System.out.println("No. of library classes: " + Scene.v().getLibraryClasses().size());
		System.out.println("No. of application classes: " + Scene.v().getApplicationClasses().size());
		Set<String> androidPkgNames = new LinkedHashSet<String>();
		for (SootClass sootClass : Scene.v().getApplicationClasses()) {
			if (sootClass.getPackageName().startsWith("android")) {
				androidPkgNames.add(sootClass.getPackageName());
			}
		}
		
		System.out.println("Android package names:");
		for (String pkgName : androidPkgNames) {
			System.out.println("\t" + pkgName);
			androidPkgs.add(pkgName);
		}
	}

	public void run() {
		//Options.v().set_whole_program(true);
		// Options.v().set_verbose(true);

		// Options.v().set_output_format(Options.v().output_format_jimple);

		// Setup dump of method bodies
		/*
		 * List<String> dump = new ArrayList<String>(); dump.add("ALL");
		 * Options.v().set_dump_cfg(dump); Options.v().set_dump_body(dump);
		 */
		
		AndroidJarConfig.applySootOptions();
		
		Scene.v().loadNecessaryClasses();

		PackManager.v().getPack("wjtp").add(new Transform("wjtp.pkgnames", this));

		PackManager.v().getPack("wjtp").apply();
		// PackManager.v().writeOutput();
		// PackManager.v().getPack("wjap").apply();
	}

}
