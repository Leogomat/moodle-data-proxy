package i5.las2peer.services.moodleDataProxyService.moodleData.MoodleDataPOJO;

import org.json.JSONObject;

public class MoodleDiscussion extends MoodleDataPOJO {
	private int id;
	private int userid;
	private long timecreated;
	private long timemodified;

  public MoodleDiscussion(JSONObject postData) {
    // TODO
  }

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public int getUserid() {
		return userid;
	}

	public void setUserid(int userid) {
		this.userid = userid;
	}

	public long getTimecreated() {
		return timecreated;
	}

	public void setTimecreated(long timecreated) {
		this.timecreated = timecreated;
	}

	public long getTimemodified() {
		return timemodified;
	}

	public void setTimemodified(long timemodified) {
		this.timemodified = timemodified;
	}
}
