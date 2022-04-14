package io.openvidu.loadtest.models.testcase;

public enum Resolution {

	DEFAULT("640x480", "640x480"),
	QVGA("320x240", "320x240"),
	VGA("640x480", "640x480"),
	HD("1280x720", "1280x720"),
	FULLHD("1920x1080", "1920x1080"),
	UHD("3840x2160", "3840x2160");

	private String label;
	private String value;

	Resolution(String label, String string) {
		this.label = label;
		this.value = string;
	}

	public String getValue() {
		return this.value;
	}

	public String getLabel() {
		return this.label;
	}

	public String toString() {
		return this.getLabel();
	}

}
