package io.openvidu.loadtest;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import io.openvidu.loadtest.controller.LoadTestController;
import io.openvidu.loadtest.models.testcase.TestCase;
import io.openvidu.loadtest.utils.DataIO;

/**
 * @author Carlos Santos
 *
 */

@SpringBootApplication
public class LoadTestApplication {

	private static final Logger log = LoggerFactory.getLogger(LoadTestApplication.class);

	@Autowired
	private LoadTestController loadTestController;

	@Autowired
	private DataIO io;

	@Value("${TEST_CASE_FILE_PATH}")
	private String testCaseFilePath;

	public static void main(String[] args) {
		SpringApplication.run(LoadTestApplication.class, args);
	}

	public void start() throws Exception {
		if (null == testCaseFilePath || testCaseFilePath.isEmpty()) {
			testCaseFilePath = DataIO.DEFAULT_TEST_CASES_JSON_FILE;
		}
		log.info("Loading test cases from file {}", testCaseFilePath);
		List<TestCase> testCasesList = io.getTestCasesFromJSON(testCaseFilePath);
		if (testCasesList.size() > 0) {
			loadTestController.startLoadTests(testCasesList);
			log.info("Finished");
		} else {
			log.error(
					"Test cases file not found or it is empty. Please, add {} file in resources directory", testCaseFilePath);
		}
	}

	@EventListener(ApplicationReadyEvent.class)
	public void whenReady() throws Exception {
		this.start();
	}

}
