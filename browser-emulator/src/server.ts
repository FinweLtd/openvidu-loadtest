import fs = require('fs');
import https = require('https');
import * as express from "express";

import { APPLICATION_MODE, EMULATED_USER_TYPE, SERVER_PORT } from "./config";
import { HackService } from "./services/hack.service";

import {app as ovBrowserController} from './controllers/openvidu-browser.controller';
import {app as webrtcStatsController} from './controllers/webrtc-stats.controller';

import { DockerService } from './services/docker.service';
import { InstanceService } from './services/instance.service';
import { ApplicationMode } from './types/config.type';


const app = express();

app.use(express.static('public'));

const options = {
	key: fs.readFileSync('public/key.pem', 'utf8'),
	cert: fs.readFileSync('public/cert.pem', 'utf8'),
};

app.use(express.json());
app.use(express.urlencoded({ extended: true }));

app.use('/', webrtcStatsController);
app.use('/openvidu-browser', ovBrowserController);

const server = https.createServer(options, app);

server.listen(SERVER_PORT, async () => {
	const hack = new HackService();
	hack.openviduBrowser();
	hack.webrtc();
	hack.websocket();
	hack.platform();
	hack.allowSelfSignedCertificate();

	createRecordingsDirectory();

	const instanceService = InstanceService.getInstance();
	await instanceService.cleanEnvironment();

	if(APPLICATION_MODE === ApplicationMode.PROD) {
		console.log("Pulling Docker images needed...");
		await instanceService.pullImagesNeeded();
	}

	process.env.ELASTICSEARCH_HOSTNAME = 'your-hostname';
	process.env.ELASTICSEARCH_USERNAME = 'your-user';
	process.env.ELASTICSEARCH_PASSWORD = 'your-secret';

	try {
		await instanceService.launchMetricBeat();
		console.log("metricbeat strarted");
	} catch (error) {
		console.log('Error starting metricbeat', error);
		if (error.statusCode === 409 && error.message.includes('Conflict')) {
			console.log('Retrying ...');
			await instanceService.removeContainer('metricbeat');
			await instanceService.launchMetricBeat();
		}
	}

	console.log("---------------------------------------------------------");
	console.log(" ");
	console.log(`App started in ${APPLICATION_MODE} mode`);
	console.log(`Emulated user type: ${EMULATED_USER_TYPE}`);
	console.log(`Listening in port ${SERVER_PORT}`);
	console.log(" ");
	console.log("---------------------------------------------------------");
});

function createRecordingsDirectory() {
	var dir = `${process.env.PWD}/recordings`;
	if (!fs.existsSync(dir)){
		fs.mkdirSync(dir);
	}
}
