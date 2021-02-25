package io.openvidu.loadtest.infrastructure;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.gson.JsonObject;

import io.openvidu.loadtest.config.LoadTestConfig;
import io.openvidu.loadtest.models.testcase.OpenViduRole;
import io.openvidu.loadtest.models.testcase.RequestBody;
import io.openvidu.loadtest.utils.CustomHttpClient;
import io.openvidu.loadtest.utils.JsonUtils;

@Service
public class BrowserEmulatorClient {
	
	private static final Logger log = LoggerFactory.getLogger(BrowserEmulatorClient.class);
	
	private static List<String> workerUrlList = new ArrayList<String>();
	private static AtomicInteger lastWorkerIndex = new AtomicInteger(-1);
	private static final int HTTP_STATUS_OK = 200;

	
	@Autowired
	private LoadTestConfig loadTestConfig;
	
	@Autowired
	private CustomHttpClient httpClient;
	
	@Autowired
	private JsonUtils jsonUtils;

	
	@PostConstruct
	public void init() {
		workerUrlList = this.loadTestConfig.getWorkerUrlList();
	}
	
	public boolean createPublisher(int userNumber, int sessionNumber) {
		String workerUrl = "";
		RequestBody body = this.generateRequestBody(userNumber, sessionNumber, OpenViduRole.PUBLISHER);

		try {
			workerUrl = getNextWorkerUrl();
			log.info("Worker selected address: {}", workerUrl);
			log.info("Connecting user: '{}' into session: '{}'", body.getUserId(), body.getSessionName());
			HttpResponse<String> response = this.httpClient.sendPost(workerUrl + "/openvidu-browser/streamManager", body.toJson(), null, getHeaders());
			return processResponse(response);
		} catch (IOException | InterruptedException e) {
			if(e.getMessage().equalsIgnoreCase("Connection refused")) {
				log.error("Error trying connect with worker on {}: {}", workerUrl, e.getMessage());
				System.exit(1);
			}
			e.printStackTrace();
		}
		return false;
	}
	
	public boolean createSubscriber(int userNumber, int sessionNumber) {
		String workerUrl = "";
		RequestBody body = this.generateRequestBody(userNumber, sessionNumber, OpenViduRole.SUBSCRIBER);

		try {
			workerUrl = getNextWorkerUrl();
			HttpResponse<String> response = this.httpClient.sendPost(workerUrl + "/openvidu-browser/streamManager", body.toJson(), null, getHeaders());
			return processResponse(response);
		} catch (IOException | InterruptedException e) {
			if(e.getMessage().equalsIgnoreCase("Connection refused")) {
				log.error("Error trying connect with worker on {}: {}", workerUrl, e.getMessage());
				System.exit(1);
			}
			e.printStackTrace();
		}
		return false;
	}
	
	public void disconnectAll() {
		for (String workerUrl : workerUrlList) {
			try {
				log.info("Deleting all participants from worker {}", workerUrl);
				Map<String, String> headers = new HashMap<String, String>();
				headers.put("Content-Type", "application/json");
				this.httpClient.sendDelete(workerUrl + "/openvidu-browser/streamManager", headers);
			} catch (Exception e) {
				if(e.getMessage().equalsIgnoreCase("Connection refused")) {
					log.error("Error trying connect with worker on {}: {}", workerUrl, e.getMessage());
					System.exit(1);
				}
				e.printStackTrace();
			}
		}
	}
	
private boolean processResponse(HttpResponse<String> response) {
		
		if(response == null) {
			log.error("Http Status Response {} ", response);
			return false;
		}

		if (response.statusCode() == HTTP_STATUS_OK) {
			JsonObject jsonResponse = jsonUtils.getJson(response.body());
			String connectionId = jsonResponse.get("connectionId").getAsString();
			String workerCpu = jsonResponse.get("workerCpuUsage").getAsString();
			log.info("Connection {} created", connectionId);
			log.info("Worker CPU USAGE: {}% ", workerCpu);
			System.out.print("\n");
			return true;
		}
		log.error("Http Status Response {} ", response.statusCode());
		log.error("Response message {} ", response.body());
		return false;
	}
	
//	public void deleteAllStreamManagers(String role) {
//		
//		if(role.equalsIgnoreCase("PUBLISHER") || role.equalsIgnoreCase("SUBSCRIBER")) {
//			for (String workerUrl : workerUrlList) {
//				try {
//					log.info("Deleting all '{}' from worker {}", role.toUpperCase(), workerUrl);
//					Map<String, String> headers = new HashMap<String, String>();
//					headers.put("Content-Type", "application/json");
//					this.httpClient.sendDelete(workerUrl + "/openvidu-browser/streamManager/role/" + role.toUpperCase(), headers);
//				} catch (IOException | InterruptedException e) {
//					if(e.getMessage().equalsIgnoreCase("Connection refused")) {
//						log.error("Error trying connect with worker on {}: {}", workerUrl, e.getMessage());
//					}
//					e.printStackTrace();
//				}
//			}
//		}
//	}
	

	
//	public int getCapacity(String typology, int participantsPerSession) {
//		int capacity = 0;
//		try {
//			HttpResponse<String> response = this.httpClient.sendGet(WORKER_URL + "/browser-emulator/capacity?typology=" + typology);
//			JsonObject convertedObject = new Gson().fromJson(response.body().toString(), JsonObject.class);
//			capacity = convertedObject.get("capacity").getAsInt();
//		} catch (IOException | InterruptedException e) {
//			e.printStackTrace();
//		}
//		return capacity;
//	}
	
	private String getNextWorkerUrl() {
		int workerInstances = workerUrlList.size();
		int nextWorkerIndex = lastWorkerIndex.incrementAndGet();
		
		if(nextWorkerIndex > workerInstances - 1) {
			lastWorkerIndex.set(0); 
			return workerUrlList.get(0);
		}
		return workerUrlList.get(nextWorkerIndex);
		
	}
	
	private RequestBody generateRequestBody(int userNumber, int sessionNumber, OpenViduRole role) {

		return new RequestBody().openviduUrl(this.loadTestConfig.getOpenViduUrl())
				.openviduSecret(this.loadTestConfig.getOpenViduSecret())
//				.elasticSearchHost(this.loadTestConfig.getElasticsearchHost())
//				.elasticSearchUserName(this.loadTestConfig.getElasticsearchUserName())
//				.elasticSearchPassword(this.loadTestConfig.getElasticsearchPassword())
				.userId(this.loadTestConfig.getUserNamePrefix() + userNumber)
				.sessionName(this.loadTestConfig.getSessionNamePrefix() + sessionNumber).audio(true).video(true)
				.role(role).build();

	}
	
	private Map<String, String> getHeaders() {
		Map<String, String> headers = new HashMap<String, String>();
		headers.put("Content-Type", "application/json");
		return headers;
	}

}
