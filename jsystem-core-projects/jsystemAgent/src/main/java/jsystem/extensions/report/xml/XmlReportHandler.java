/*
 * Created on Nov 30, 2005
 *
 * Copyright 2005-2010 Ignis Software Tools Ltd. All rights reserved.
 */

package jsystem.extensions.report.xml;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import jsystem.framework.FrameworkOptions;
import jsystem.framework.JSystemProperties;
import jsystem.runner.agent.reportdb.TestInfo;

import org.apache.commons.digester.SetTopRule;
import org.apache.xpath.XPathAPI;
import org.jfree.util.Log;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Give information about the run from the xml files
 * 
 * @author guy.arieli
 * 
 */
public class XmlReportHandler implements ReportInformation {
	private static Logger log = Logger.getLogger(XmlReportHandler.class.getName());

	File xmlDirectory;

	int numberOfTests = 0;

	int numberOfTestsPass = 0;

	int numberOfTestsFail = 0;

	int numberOfTestsWarning = 0;

	String version = null;

	String build = null;

	String userName = null;

	String scenarioName = null;

	String sutName = null;

	String station = null;

	long startTime = 0;

	Vector<TestInfo> tests;

	Vector<File> files;

	private static XmlReportHandler instance;

	/**
	 * This constructor is package protected for unit testing purpose.
	 * 
	 * @param xmlDirectory
	 * @throws Exception
	 */
	XmlReportHandler(File xmlDirectory) throws Exception {
		this.xmlDirectory = xmlDirectory;
		readXmlFiles();
	}

	public static XmlReportHandler getInstance() {
		if (null == instance) {
			if (null == JSystemProperties.getInstance()) {
				// Can't find the log folder
				return null;
			}
			File logFolder = new File(JSystemProperties.getInstance().getPreference(FrameworkOptions.LOG_FOLDER),
					"current");
			try {
				instance = new XmlReportHandler(logFolder);
			} catch (Exception e) {
				log.log(Level.SEVERE, "Failed to create instance of XmlReporter", e);
			}
		}
		if (instance != null) {
			instance.refresh();
		}
		return instance;

	}

	@Override
	public void refresh() {
		try {
			readXmlFiles();
		} catch (Exception e) {
			log.log(Level.WARNING, "Failed to refresh XML files", e);
		}

	}

	private Document initDocument(final File f) throws ParserConfigurationException, SAXException, IOException {
		files.addElement(f);
		DocumentBuilder db;
		db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		return db.parse(f);
	}

	private File getReportFile(final int number) throws FileNotFoundException {
		return new File(xmlDirectory, "reports." + number + ".xml");
	}

	private void readXmlFiles() throws Exception {
		tests = new Vector<TestInfo>();
		files = new Vector<File>();

		int i = 0;

		while (true) {
			final File f = getReportFile(i);
			if (!f.exists()) {
				if (i == 0) {
					throw new Exception("File not found: " + f.getPath());
				}
				return;
			}

			final Document doc = initDocument(f);

			if (i == 0) {
				sutName = ((Element) doc.getFirstChild()).getAttribute(XmlReporter.SETUP);
				version = ((Element) doc.getFirstChild()).getAttribute(XmlReporter.VERSION);
				userName = ((Element) doc.getFirstChild()).getAttribute(XmlReporter.USER);
				build = ((Element) doc.getFirstChild()).getAttribute(XmlReporter.BUILD);
				scenarioName = ((Element) doc.getFirstChild()).getAttribute(XmlReporter.SCENARIO_NAME);
				station = ((Element) doc.getFirstChild()).getAttribute(XmlReporter.STATION);
				startTime = Long.parseLong(((Element) doc.getFirstChild()).getAttribute(XmlReporter.START_TIME));
			}
			i++;

			/**
			 * Convert the xmls files to vector of TestInfo
			 */
			NodeList elements = (NodeList) XPathAPI.selectNodeList(doc, "/reports/test");
			for (int index = 0; index < elements.getLength(); index++) {
				if (elements.item(index) instanceof Element) {
					numberOfTests++;

					Element e = (Element) elements.item(index);
					// Element p = (Element) e.getParentNode();

					String status = e.getAttribute("status");
					if (status.equals("true")) {
						numberOfTestsPass++;
					} else if (status.equals("false")) {
						numberOfTestsFail++;
					} else {
						numberOfTestsWarning++;
					}
					String startTime = e.getAttribute("startTime");
					if (startTime != null) {
						// info.setStartTime(Long.parseLong(startTime));
					}
					String endTime = e.getAttribute("endTime");
					if (endTime != null) {
						try {
							// //// patch for runner halt bug ////////////

							if (endTime != "") {
								// info.setEndTime(Long.parseLong(endTime));
							} else {
								// info.setEndTime(1111111111111L);
							}
							// ////////////////////////////////////////////

						} catch (Throwable t) {
							log.log(Level.WARNING, "no endTime for test: " + e.getAttribute("name"), t);
						}
					}
				}
			}
		}
	}

	@Override
	public int getNumberOfTests() {
		return numberOfTests;
	}

	@Override
	public int getNumberOfTestsPass() {
		return numberOfTestsPass;
	}

	@Override
	public int getNumberOfTestsFail() {
		return numberOfTestsFail;
	}

	@Override
	public int getNumberOfTestsWarning() {
		return numberOfTestsWarning;
	}

	@Override
	public String getVersion() {
		return version;
	}

	@Override
	public String getBuild() {
		return build;
	}

	@Override
	public String getUserName() {
		return userName;
	}

	@Override
	public String getScenarioName() {
		return scenarioName;
	}

	@Override
	public String getSutName() {
		return sutName;
	}

	private void setRootAttributeValue(String attributeName, String attributeNewValue) {
		Document doc = null;
		File reportFile0 = null;
		try {
			reportFile0 = getReportFile(0);
			doc = initDocument(getReportFile(0));
		} catch (Exception e) {
			Log.error("Failed to update sut name", e);
			return;
		}

		((Element) doc.getFirstChild()).setAttribute(attributeName, attributeNewValue);

		writeToFile(doc, reportFile0);

	}

	private void writeToFile(final Document doc, final File reportFile) throws TransformerFactoryConfigurationError {
		try {
			Transformer transformer = TransformerFactory.newInstance().newTransformer();
			Result output = new StreamResult(reportFile);
			Source input = new DOMSource(doc);
			transformer.transform(input, output);
		} catch (Exception e) {
			Log.error("Failed to update sut name", e);
		}
		refresh();
	}

	public void setSutName(String sutName) {
		setRootAttributeValue(XmlReporter.SETUP, sutName);
	}

	public void setScenarioName(String scenarioName) {
		setRootAttributeValue(XmlReporter.SCENARIO_NAME, scenarioName);
	}

	public void setStation(String station) {
		setRootAttributeValue(XmlReporter.STATION, station);
	}

	public void setBuild(String build) {
		setRootAttributeValue(XmlReporter.BUILD, build);
	}

	public void setVersion(String version) {
		setRootAttributeValue(XmlReporter.VERSION, version);
	}

	public void setUser(String user) {
		setRootAttributeValue(XmlReporter.USER, user);
	}

	public void setExecutionPropertiesMap(Map<String, String> executionPropertiesMap) {
		if (executionPropertiesMap == null || executionPropertiesMap.size() == 0) {
			return;
		}
		StringBuilder propsSb = new StringBuilder();
		for (String key : executionPropertiesMap.keySet()) {
			propsSb.append(key).append("=").append(executionPropertiesMap.get(key)).append("/SEP/");
		}
		setRootAttributeValue(XmlReporter.EXECUTION_PROPERTIES, propsSb.toString());

	}

	@Override
	public long getStartTime() {
		return startTime;
	}

	@Override
	public String getTestClassName(int testIndex) {
		return ((TestInfo) tests.elementAt(testIndex)).getPackageName();
	}

	@Override
	public String getTestName(int testIndex) {
		return ((TestInfo) tests.elementAt(testIndex)).getName();
	}

	public String getParams(int testIndex) {
		return ((TestInfo) tests.elementAt(testIndex)).getParams();
	}

	public int getCount(int testIndex) {
		return ((TestInfo) tests.elementAt(testIndex)).getCount();
	}

	@Override
	public int getTestStatus(int testIndex) {
		return ((TestInfo) tests.elementAt(testIndex)).getStatus();
	}

	public String getTestGraphXml(int testIndex) {
		return ((TestInfo) tests.elementAt(testIndex)).getGraphXml();
	}

	@Override
	public String getTestSteps(int testIndex) {
		return ((TestInfo) tests.elementAt(testIndex)).getSteps();
	}

	@Override
	public String getTestFailCause(int testIndex) {
		return ((TestInfo) tests.elementAt(testIndex)).getFailCause();
	}

	public File[] getXmlFiles() {
		Object[] os = files.toArray();
		File[] fs = new File[os.length];
		System.arraycopy(os, 0, fs, 0, os.length);
		return fs;
	}

	public String getTestInfo(int testIndex) {
		return ((TestInfo) tests.elementAt(testIndex)).toString();
	}

	public static void main(String[] args) {
		try {
			XmlReportHandler xml = new XmlReportHandler(new File(
					"C:\\work\\projects\\automation\\jsystem\\log\\current"));
			for (int i = 0; i < xml.getNumberOfTests(); i++) {
				log.log(Level.INFO, xml.getTestInfo(i));
			}
		} catch (Exception e) {
			log.log(Level.WARNING, "fail to publish to DB");
		}
	}

	@Override
	public long getTestStartTime(int testIndex) {
		return ((TestInfo) tests.elementAt(testIndex)).getStartTime();
	}

	@Override
	public long getTestEndTime(int testIndex) {
		return ((TestInfo) tests.elementAt(testIndex)).getEndTime();
	}

	@Override
	public String getStation() {
		return station;
	}

	@Override
	public String getTestDocumentation(int testIndex) {
		return ((TestInfo) tests.elementAt(testIndex)).getDocumentation();
	}

}
