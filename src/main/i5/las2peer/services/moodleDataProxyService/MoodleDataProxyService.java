package i5.las2peer.services.moodleDataProxyService;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.gson.Gson;

import i5.las2peer.api.Context;
import i5.las2peer.api.ManualDeployment;
import i5.las2peer.api.logging.MonitoringEvent;
import i5.las2peer.api.security.AnonymousAgent;
import i5.las2peer.logging.L2pLogger;
import java.util.logging.Level;
import i5.las2peer.restMapper.RESTService;
import i5.las2peer.restMapper.annotations.ServicePath;
import i5.las2peer.security.UserAgentImpl;
import i5.las2peer.services.moodleDataProxyService.moodleData.MoodleWebServiceConnection;
import i5.las2peer.services.moodleDataProxyService.moodleData.MoodleDataPOJO.MoodleAssignSubmission;
import i5.las2peer.services.moodleDataProxyService.moodleData.MoodleDataPOJO.MoodleUserData;
import i5.las2peer.services.moodleDataProxyService.moodleData.MoodleDataPOJO.MoodleUserGradeItem;
import i5.las2peer.services.moodleDataProxyService.moodleData.xAPIStatements.xAPIStatements;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Contact;
import io.swagger.annotations.Info;
import io.swagger.annotations.SwaggerDefinition;

@Api
@SwaggerDefinition(
		info = @Info(
				title = "Moodle Data Proxy Service",
				version = "1.0.0",
				description = "A proxy for requesting data from moodle",
				contact = @Contact(
						name = "Alexander Tobias Neumann",
						email = "neumann@rwth-aachen.de")))

/**
 *
 * This service is for requesting moodle data and creating corresponding xAPI statement. It sends REST requests to
 * moodle on basis of implemented functions in MoodleWebServiceConnection.
 *
 */
@ManualDeployment
@ServicePath("moodle")
public class MoodleDataProxyService extends RESTService {

	private String moodleDomain;
	private String moodleToken;

	private static HashSet<Integer> courseList = new HashSet<Integer>();
	private static ScheduledExecutorService dataStreamThread = null;

	private final static int MOODLE_DATA_STREAM_PERIOD = 60; // Every minute
	private static long lastChecked = 0;


	private final static L2pLogger logger = L2pLogger.getInstance(MoodleDataProxyService.class.getName());

	private static Context context = null;

	private static String email = "";

	/**
	 *
	 * Constructor of the Service. Loads the database values from a property file
	 * and initiates values for a moodle connection.
	 *
	 */
	public MoodleDataProxyService() {
		/*setFieldValues(); // This sets the values of the configuration file
		if (lastChecked == 0) {
			// Get current time
			TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
			Timestamp timestamp = new Timestamp(System.currentTimeMillis());
			Instant instant = timestamp.toInstant();
			lastChecked = instant.getEpochSecond();
			L2pLogger.setGlobalConsoleLevel(Level.WARNING);
		}

		MoodleWebServiceConnection moodle = new MoodleWebServiceConnection(moodleToken, moodleDomain);

		if (email.equals("")) {
			try {
				String siteInfoRaw = moodle.core_webservice_get_site_info();
				JSONObject siteInfo = new JSONObject(siteInfoRaw);
				int userId = siteInfo.getInt("userid");
				String currentUserInfoRaw = moodle.core_user_get_users_by_field("id", userId);
				JSONArray currentUserInfo = new JSONArray(currentUserInfoRaw);
				JSONObject u = currentUserInfo.getJSONObject(0);
				email = u.getString("email");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		if (courseList == null || courseList.isEmpty()) {
			try {
				String courses = moodle.core_course_get_courses();
				JSONArray jsonCourse = new JSONArray(courses);
				courseList = new HashSet<Integer>();
				for (Object o : jsonCourse) {
					JSONObject course = (JSONObject) o;
					courseList.add(course.getInt("id"));
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	@POST
	@Path("/")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "Moodle connection is initiaded") })
//	@RolesAllowed("authenticated")
	public Response initMoodleProxy() {
		// if (Context.getCurrent().getMainAgent() instanceof AnonymousAgent) {
		// 	return Response.status(Status.UNAUTHORIZED).entity("Authorization required.").build();
		// }

		// UserAgentImpl u = (UserAgentImpl) Context.getCurrent().getMainAgent();
		// String email = u.getEmail();
		//
		// if (!email.equals(email)) {
		// 	return Response.status(Status.FORBIDDEN).entity("Access denied").build();
		// }
		if (dataStreamThread == null) {
			context = Context.get();
			dataStreamThread = Executors.newSingleThreadScheduledExecutor();
			dataStreamThread.scheduleAtFixedRate(new DataStreamThread(), 0, MOODLE_DATA_STREAM_PERIOD,
					TimeUnit.SECONDS);
			return Response.status(Status.OK).entity("Thread started.").build();
		} else {
			return Response.status(Status.BAD_REQUEST).entity("Thread already running.").build();
		}
	}

	/**
	 * Thread which periodically checks all courses for new quiz attempts,
	 * creates xAPI statements of new attempts and sends them to Mobsos.
	 *
	 * @return void
	 *
	 */
/*	private class DataStreamThread implements Runnable {
		@Override
		public void run() {
			Gson gson = new Gson();
			TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

			// Get current time
			Timestamp timestamp = new Timestamp(System.currentTimeMillis());
			long now = timestamp.toInstant().getEpochSecond();

			for (Integer courseId : courseList) {
				try {
					JSONArray jsonUserInfo = new JSONArray(moodle.core_enrol_get_enrolled_users(courseId));
					JSONObject userGradesObject = new JSONObject(moodle.gradereport_user_get_grade_items(courseId));
					JSONArray userGrades = userGradesObject.getJSONArray("usergrades");

					for (Object uGradesObject : userGrades) {
						JSONObject uGrades = (JSONObject) uGradesObject;
						MoodleUserData moodleUserData = moodle.getMoodleUserData(jsonUserInfo, uGrades);
						JSONArray gradeItems = uGrades.getJSONArray("gradeitems");

						for (Object gradeItemObject : gradeItems) {
							JSONObject gradeItem = (JSONObject) gradeItemObject;
							MoodleUserGradeItem gItem = gson.fromJson(gradeItem.toString(), MoodleUserGradeItem.class);
							gItem.setCourseid(uGrades.getInt("courseid"));

							if (gItem.getGradedategraded() != null && gItem.getGradedategraded() > lastChecked) {

								// Get duration for quiz
								if (gItem.getItemtype().equals("quiz")) {
									JSONObject quizReview = new JSONObject(moodle.mod_quiz_get_attempt_review(gItem.getId()));
									JSONObject quizReviewAttempt = quizReview.getJSONObject("attempt");
									long start = quizReviewAttempt.getLong("timestart");
									long finish = quizReviewAttempt.getLong("timefinish");
									gItem.setDuration(finish - start);
								}
								else if (gItem.getItemtype().equals("assign")) {
									JSONArray assignSubmissions = new JSONArray(
										moodle.mod_assign_get_submissions(gItem.getIteminstance()));
									MoodleAssignSubmission mas = moodle.getUserSubmission(assignSubmissions,
											moodleUserData.getUserId());
									long start = mas.getTimecreated();
									long finish = mas.getTimemodified();
									gItem.setDuration(finish - start);
								}

								context.monitorEvent(MonitoringEvent.SERVICE_CUSTOM_MESSAGE_2,
										xAPIStatements.createXAPIStatementGrades(moodleUserData, gItem,
												moodle.getDomainName()) + '*' + moodle.getUserToken());

								logger.info("New grading item " + gItem.getId());
							}
						}
					}
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}

			lastChecked = now;
		}*/
	}

//	private void

}
