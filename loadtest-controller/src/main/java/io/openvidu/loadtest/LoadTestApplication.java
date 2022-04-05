package io.openvidu.loadtest;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import io.openvidu.loadtest.config.LoadTestConfig;
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
	private LoadTestConfig loadTestConfig;

	@Autowired
	private DataIO io;

	public static void main(String[] args) {
		SpringApplication.run(LoadTestApplication.class, args);
	}

	public void start() throws Exception {

		String envOpenViduUrl = System.getenv("OPENVIDU_LOADTEST_OPENVIDU_URL");
		if (null != envOpenViduUrl && !envOpenViduUrl.isEmpty()) {
			this.loadTestConfig.setOpenViduUrl(envOpenViduUrl);
		}
		String envOpenViduSecret = System.getenv("OPENVIDU_LOADTEST_OPENVIDU_SECRET");
		if (null != envOpenViduSecret && !envOpenViduSecret.isEmpty()) {
			this.loadTestConfig.setOpenViduSecret(envOpenViduSecret);
		}
		String envAwsAccessKey = System.getenv("OPENVIDU_LOADTEST_AWS_ACCESS_KEY");
		if (null != envAwsAccessKey && !envAwsAccessKey.isEmpty()) {
			this.loadTestConfig.setAwsAccessKey(envAwsAccessKey);
		}
		String envAwsSecretAccessKey = System.getenv("OPENVIDU_LOADTEST_AWS_SECRET_ACCESS_KEY");
		if (null != envAwsSecretAccessKey && !envAwsSecretAccessKey.isEmpty()) {
			this.loadTestConfig.setAwsSecretAccessKey(envAwsSecretAccessKey);
		}

		List<TestCase> testCasesList = io.getTestCasesFromJSON();
		if (testCasesList.size() > 0) {

			loadTestController.startLoadTests(testCasesList);
			log.info("Finished");
		} else {
			log.error(
					"Test cases file not found or it is empty. Please, add test_cases.json file in resources directory");
		}
	}

	@EventListener(ApplicationReadyEvent.class)
	public void whenReady() throws Exception {
		this.start();
	}

}
