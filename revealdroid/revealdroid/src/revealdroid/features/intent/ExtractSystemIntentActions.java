package revealdroid.features.intent;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import net.dongliu.apk.parser.ApkParser;
import net.dongliu.apk.parser.bean.ApkMeta;
import net.dongliu.apk.parser.exception.ParserException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.google.common.io.Files;

import android.content.Intent;
import revealdroid.StopWatch;
import weka.core.Attribute;

public class ExtractSystemIntentActions {

	private static String mainpackageName;
	private static String mainCompName;
	private static final String MAIN_ACTION = "android.intent.action.MAIN";
	private static Set<String> intentActions = new LinkedHashSet<String>();
	public static Set<String> manifestActions = new LinkedHashSet<String>();
	public static Set<String> programmaticActions = new LinkedHashSet<String>();
	
	public static void reset() {
		manifestActions =  new LinkedHashSet<String>();
		programmaticActions = new LinkedHashSet<String>();
	}

	public static void main(String[] args) {
		StopWatch stopWatch = new StopWatch();
		stopWatch.start();
		
		String apkFilePath = args[0];
		
		/*Class<Intent> IntentClass = Intent.class;
		Field[] intentFields = IntentClass.getDeclaredFields();

		System.out.println("action fields of Intent: ");
		for (Field field : intentFields) {
			if (field.toString().contains("android.content.Intent.ACTION_")) {
				String attrName = field.getName(); //.replace("ACTION_", "ACTION____");
				System.out.println(attrName);
				intentActions.add(attrName);
			}
		}*/
		
		extractApkMetadata(apkFilePath);
		System.out.println("actions from manifest:");
		for (String manifestAction : manifestActions) {
			System.out.println("\t" + manifestAction);
		}
		IntentActionExtractionTransformer transformer = new IntentActionExtractionTransformer(apkFilePath);
		transformer.run();
		
		System.out.println("Manifest actions:");
		for (String action : manifestActions) {
			System.out.println("\t" + action);
		}
		
		System.out.println("Programmatic actions:");
		for (String action : programmaticActions) {
			System.out.println("\t" + action);
		}
		
		String outputFileName = determineOutputFileName(apkFilePath);
		
		try {
			FileWriter writer = new FileWriter(outputFileName);
			for (String action : manifestActions) {
				writer.write(action.trim() + "\n");
			}
			for (String action : programmaticActions) {
				writer.write(action.trim() + "\n");
			}
			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		stopWatch.stop();
		System.out.println("Intent Action Extraction has run for " + stopWatch.getElapsedTime() + " ms");

	}

	public static String determineOutputFileName(String apkFilePath) {
		String apkFileNameWithoutExt = Files.getNameWithoutExtension(apkFilePath);
		String familyName = (new File(apkFilePath)).getParentFile().getName();
		//System.out.println(apkFileNameWithoutExt);
		//System.out.println(familyName);
		
		String outputFileName = "data" + File.separator + "actions" + File.separator + familyName + "_" + apkFileNameWithoutExt + "_actions.txt";
		return outputFileName;
	}
	
	public static void extractApkMetadata(String apkFilePath) {
		try {
			ApkParser apkParser = new ApkParser(new File(apkFilePath));
			apkParser.setPreferredLocale(Locale.ENGLISH);

			String xml = null;
			try {
				xml = apkParser.getManifestXml();

			} catch (Exception e) {
				e.printStackTrace();
				System.out.println("Error parsing manifest file...continuing to code analysis.");
				apkParser.close();
				return;
			}
			System.out.println(xml);
			
			manifestActions = new LinkedHashSet<String>();
			
			try {
				DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
				DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
				InputSource is = new InputSource(new StringReader(xml));
				Document doc = dBuilder.parse(is);
				
				System.out.println("Root element :" + doc.getDocumentElement().getNodeName());
				mainpackageName = doc.getDocumentElement().getAttribute("package");
				System.out.println("package: " + mainpackageName);
				
				NodeList intentFilters = doc.getElementsByTagName("intent-filter");
				for (int temp = 0; temp < intentFilters.getLength(); temp++) {
					 
					Node nNode = intentFilters.item(temp);
			 
					System.out.println("\nCurrent Element :" + nNode.getNodeName());
			 
					if (nNode.getNodeType() == Node.ELEMENT_NODE) {
			 
						Element elem = (Element) nNode;
						NodeList actionNodes = elem.getElementsByTagName("action");
						
						for (int actionIndex = 0; actionIndex < actionNodes.getLength(); actionIndex++) {
							Element actionElem = getElement(actionNodes.item(actionIndex));
							System.out.println("action android:name - " + actionElem.getAttribute("android:name"));
							
							if (actionElem.getAttribute("android:name").equals(MAIN_ACTION)) {
								Element compElem = getElement(actionElem.getParentNode().getParentNode());
								System.out.println("\tcomp android:name - " + compElem.getAttribute("android:name"));
								mainCompName = compElem.getAttribute("android:name");
							}
							
							manifestActions.add(actionElem.getAttribute("android:name"));
						}
						
						NodeList categoryNodes = elem.getElementsByTagName("category");
						
						for (int categoryIndex = 0; categoryIndex < categoryNodes.getLength(); categoryIndex++) {
							Element categoryElem = getElement(categoryNodes.item(categoryIndex));
							System.out.println("category android:name - " + categoryElem.getAttribute("android:name"));
						}
			 
					}
				}
			} catch (ParserConfigurationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (SAXException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			
			
			
			ApkMeta apkMeta = apkParser.getApkMeta();
			System.out.println(apkMeta);
			
			apkParser.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ParserException e) {
			e.printStackTrace();
		}
	}
	
	private static Element getElement(Node node) {
		if (node.getNodeType() == Node.ELEMENT_NODE) {
			 
			Element elem = (Element) node;
			return elem;
		}
		return null;
	}

}
