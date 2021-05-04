package revealdroid.features.config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import soot.options.Options;

public class ApkConfig {
	public final static String androidJAR = "lib/android.jar";
	public static String apkFilePath;
	
	public static void applyNonWholeProgramSootOptions() {
		Options.v().set_whole_program(false);
		applyCommonSootOptions();
		
		// Setup dump  of method bodies
		/*List<String> dump = new ArrayList<String>();
		dump.add("ALL");
		Options.v().set_dump_cfg(dump);
		Options.v().set_dump_body(dump);*/
	}
	
	public static void applyWholeProgramSootOptions() {
		Options.v().set_whole_program(true);
		applyCommonSootOptions();
	}

	public static void applyCommonSootOptions() {
		Options.v().set_src_prec(Options.src_prec_apk);
		Options.v().set_output_format(Options.output_format_jimple);

		Options.v().set_allow_phantom_refs(true);
		Options.v().set_time(true);

		Options.v().set_no_bodies_for_excluded(true);
		Options.v().set_show_exception_dests(false);
		//Options.v().set_print_tags_in_output(true);
		Options.v().set_verbose(false);
		Options.v().set_android_jars(System.getenv("ANDROID_HOME"));
		Options.v()
				.set_soot_classpath(apkFilePath + File.pathSeparator + 
						androidJAR);
		List<String> processDirs = new ArrayList<String>();
		processDirs.add(apkFilePath);
		Options.v().set_process_dir(processDirs);

		Options.v().set_keep_line_number(true);
		Options.v().set_coffi(true);
		
		// Setup dump  of method bodies
		/*List<String> dump = new ArrayList<String>();
		dump.add("ALL");
		Options.v().set_dump_cfg(dump);
		Options.v().set_dump_body(dump);*/
	}
}
