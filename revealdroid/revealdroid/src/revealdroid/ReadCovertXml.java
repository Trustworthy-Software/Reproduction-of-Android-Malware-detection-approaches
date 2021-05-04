package revealdroid;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

public class ReadCovertXml extends DefaultHandler {
	
	boolean isActionsElem = true;
	static Set<String> actions;
	
	public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
		//System.out.println("Start Element: " + qName);
		if (qName.equalsIgnoreCase("actions")) {
			isActionsElem = true;
		}
	}

	public void endElement(String uri, String localName, String qName) throws SAXException {
		//System.out.println("End Element: " + qName);
	}

	public void characters(char ch[], int start, int length) throws SAXException {

		if (isActionsElem) {
			String actionsData = new String(ch, start, length);
			//System.out.println("actions : " + actionsData);
			isActionsElem = false;
			if (actionsData.startsWith("android.intent.action")) {
				actions.add(actionsData);
			}
		}
	}

	public static void main(String[] args) {
		processCovertXmlFile(args);
	}

	/**
	 * 
	 * processes a covert xml file, passed as the first element in {@code args}, to obtain its list of actions
	 * 
	 * @param args only takes first element as described above
	 * @return the list of actions found
	 */
	public static Set<String> processCovertXmlFile(String[] args) {
		actions = new LinkedHashSet<String>();
		String filename = args[0];
		SAXParserFactory spf = SAXParserFactory.newInstance();
	    spf.setNamespaceAware(true);
	    SAXParser saxParser;
		try {
			saxParser = spf.newSAXParser();
			XMLReader xmlReader = saxParser.getXMLReader();
		    xmlReader.setContentHandler(new ReadCovertXml());
		    xmlReader.parse(filename);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return actions;
	}

}
