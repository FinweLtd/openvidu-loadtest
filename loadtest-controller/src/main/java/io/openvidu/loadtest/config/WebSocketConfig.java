package io.openvidu.loadtest.config;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@ClientEndpoint
public class WebSocketConfig extends Endpoint{
	private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

	private static WebSocketContainer wsClient = ContainerProvider.getWebSocketContainer();
	private static final int RETRY_TIME_MS = 4000;
	private static final int MAX_ATTEMPT = 5;
	private static String wsEndpoint = "";
	private static AtomicInteger attempts = new AtomicInteger(1);
	
	public void connect(String endpointURI) {
		wsEndpoint = endpointURI;

		try {
			wsClient.connectToServer(this, new URI(endpointURI));

		} catch (DeploymentException | IOException e) {
			log.error(e.getMessage());
			log.info("Retrying ...");
			try {
				Thread.sleep(RETRY_TIME_MS);
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}
			if(attempts.getAndIncrement() < MAX_ATTEMPT) {
				connect(endpointURI);
			} else {
				attempts.set(1);
				log.error("Could not (re)connect to {} endpoint", getEndpoint());
			}
		} catch (URISyntaxException e1) {
			e1.printStackTrace();
			System.exit(1);
		}
	}
	
	public String getEndpoint() {
		return wsEndpoint;
	}
	
	
	@OnClose
	public void onClose(Session session, CloseReason reason) {
		log.info("closing websocket {}", session.getRequestURI());
		if(reason.getCloseCode().equals(CloseCodes.CLOSED_ABNORMALLY)) {
			log.error("Websocket {} closed abnormally", getEndpoint());
			log.error("Reconnecting ...");
			attempts.set(1);
			connect(getEndpoint());
		} 
	}
	
	 @Override
     public void onError(Session session, Throwable thr) {
         super.onError(session, thr);
         System.out.println("checkerWindow.DownloadMessages().new Endpoint() {...}.onError()");
         thr.printStackTrace();
     }

	@OnOpen
	public void onOpen(Session session, EndpointConfig config) {
		log.info("opening websocket {}", session);
	}

	@OnMessage
	public void onMessage(String message) {
		log.info("Received message: {}", message);
	}
	
	
}
