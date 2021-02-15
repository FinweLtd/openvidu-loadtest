import { OpenVidu } from 'openvidu-browser';
import { Builder, By, Capabilities, until, WebDriver } from 'selenium-webdriver';
import chrome = require('selenium-webdriver/chrome');
import { TestProperties } from '../types/api-rest.type';
import { BrowserContainerInfo } from '../types/container-info.type';
import { OpenViduRole } from '../types/openvidu.type';
import { ErrorGenerator } from '../utils/error-generator';
import { DockerService } from './docker.service';

export class RealBrowserService {

	private readonly BROWSER_CONTAINER_HOSTPORT = 4000;
	private chromeOptions = new chrome.Options();
	private chromeCapabilities = Capabilities.chrome();
	private containerMap: Map<string, BrowserContainerInfo> = new Map();

	constructor(
		private dockerService: DockerService = new DockerService(),
		private errorGenerator: ErrorGenerator = new ErrorGenerator(),
	) {

		this.chromeOptions.addArguments(
			'--disable-dev-shm-usage',
			'--window-size=1440,1080',
			'--use-fake-ui-for-media-stream',
			'--use-fake-device-for-media-stream'
		);
		this.chromeCapabilities.setAcceptInsecureCerts(true);
	}

	public async createStreamManager(token: string, properties: TestProperties): Promise<string> {

		let containerId: string;
		if(!!properties.headless) {
			this.chromeOptions.addArguments('--headless');
		}

		const bindedPort = this.BROWSER_CONTAINER_HOSTPORT + this.containerMap.size;
		this.setSeleniumRemoteURL(bindedPort);
		const webappUrl = this.generateWebappUrl(token, properties);
		console.log(webappUrl);
		try {
			const containerName = 'container_' + properties.sessionName + '_' + new Date().getTime();
			containerId = await this.dockerService.startBrowserContainer(containerName, bindedPort);
			this.containerMap.set(containerId, {connectionRole: properties.role, bindedPort});

			if(!!properties.recording && !properties.headless) {
				console.log("Starting browser recording");
				await this.dockerService.startRecordingInContainer(containerId, containerName);
			}
			await this.launchBrowser(webappUrl, properties.role);
			return containerId;
		} catch (error) {
			console.error(error);
			if(!!properties.recording && !properties.headless) {
				await this.dockerService.stopRecordingInContainer(containerId);
			}
			await this.dockerService.stopContainer(containerId);
			this.containerMap.delete(containerId);
			return Promise.reject(new Error(error));
		}finally {
			// await driver.quit();
			//TODO: Just for test, remove it
			setTimeout(async () => {
				if(!!properties.recording && !properties.headless) {
					await this.dockerService.stopRecordingInContainer(containerId);
				}
				await this.dockerService.stopContainer(containerId);
			}, 15000);
		}
	}

	async deleteStreamManagerWithConnectionId(containerId: string): Promise<void> {
		await this.dockerService.stopContainer(containerId);
		this.containerMap.delete(containerId);
	}

	deleteStreamManagerWithRole(role: any): Promise<void> {
		return new Promise(async (resolve, reject) => {
			const containersToDelete: string[] = [];
			const promisesToResolve: Promise<void>[] = [];
			this.containerMap.forEach((info: BrowserContainerInfo, containerId: string) => {
				if(info.connectionRole === role) {
					containersToDelete.push(containerId);
				}
			});

			containersToDelete.forEach( (containerId: string) => {
				promisesToResolve.push(this.dockerService.stopContainer(containerId));
				this.containerMap.delete(containerId);
			});

			try {
				await Promise.all(promisesToResolve);
				resolve();
			} catch (error) {
				reject(error);
			}
		});
	}

	private async launchBrowser(webappUrl: string, role: OpenViduRole, timeout: number = 1000): Promise<void> {
		return new Promise((resolve, reject) => {
			setTimeout(async () => {
				try {

					let chrome = await this.getChromeDriver();
					await chrome.get(webappUrl);

					// Wait until connection has been created
					await chrome.wait(until.elementsLocated(By.id('local-connection-created')), 10000);
					if(role === OpenViduRole.PUBLISHER){
						// Wait until publisher has been published regardless of whether the videos are shown or not
						await chrome.wait(until.elementsLocated(By.id('local-stream-created')), 10000);
					}
					console.log("Browser works as expected");
					resolve();

				} catch(error){
					console.log(error);
					reject(this.errorGenerator.generateError(error));
				}
			}, timeout);
		});
	}

	private async getChromeDriver(): Promise<WebDriver> {
		return await new Builder()
						.forBrowser('chrome')
						.withCapabilities(this.chromeCapabilities)
						.setChromeOptions(this.chromeOptions)
						.build();
	}

	private setSeleniumRemoteURL(bindedPort: number): void {
		// Set the SELENIUM_REMOTE_URL to the ip where the selenium webdriver will be deployed
		process.env['SELENIUM_REMOTE_URL'] = 'http://localhost:' + bindedPort + '/wd/hub';
	}

	private generateWebappUrl(token: string, properties: TestProperties): string {
		const tokenParam = !!token ? `&token=${token}` : '';
		return `https://${process.env.LOCATION_HOSTNAME}/` +
			`?publicurl=${process.env.OPENVIDU_URL}` +
			`&secret=${process.env.OPENVIDU_SECRET}` +
			tokenParam +
			`&role=${properties.role}` +
			`&sessionId=${properties.sessionName}` +
			`&userId=${properties.userId}` +
			`&resolution=${properties.resolution || '640x480'}` +
			`&showVideoElements=${properties.showVideoElements || true}`;
	}

}