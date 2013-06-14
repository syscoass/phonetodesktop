package net.xisberto.phonetodesktop;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.tasks.model.Task;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 */
public class GoogleTasksService extends IntentService {
	private static final int
			NOTIFICATION_SEND = 0,
			NOTIFICATION_ERROR = 1,
			NOTIFICATION_NEED_AUTHORIZE = 2;
	
	protected com.google.api.services.tasks.Tasks client;
	private GoogleAccountCredential credential;
	private HttpTransport transport;
	private JsonFactory jsonFactory;

	private Preferences preferences;
	private String list_id;

	public GoogleTasksService() {
		super("GoogleTasksService");
	}

	@Override
	public void onCreate() {
		super.onCreate();

		preferences = new Preferences(this);
		list_id = preferences.loadListId();

		credential = GoogleAccountCredential.usingOAuth2(this, Utils.scopes);
		credential.setSelectedAccountName(preferences.loadAccountName());

		transport = AndroidHttp.newCompatibleTransport();
		jsonFactory = new GsonFactory();

		client = new com.google.api.services.tasks.Tasks.Builder(transport,
				jsonFactory, credential).setApplicationName("PhoneToDesktop")
				.build();
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		if (intent != null) {
			final String action = intent.getAction();
			try {
				if (action.equals(Utils.ACTION_SEND_TASK)) {
					showNotification(NOTIFICATION_SEND);
					handleActionSend(intent.getStringExtra(Intent.EXTRA_TEXT));
					cancelNotification(NOTIFICATION_SEND);
				} else if (action.equals(Utils.ACTION_LIST_TASKS)) {
					handleActionList();
				} else if (action.equals(Utils.ACTION_REMOVE_TASK)) {
					handleActionRemove(intent.getStringExtra(Utils.EXTRA_TASK_ID));
				}
			} catch (UserRecoverableAuthIOException userRecoverableException) {
				cancelNotification(NOTIFICATION_SEND);
				showNotification(NOTIFICATION_NEED_AUTHORIZE);
			} catch (IOException ioException) {
				cancelNotification(NOTIFICATION_SEND);
				showNotification(NOTIFICATION_ERROR);
			} catch (NullPointerException npe) {
				cancelNotification(NOTIFICATION_SEND);
				showNotification(NOTIFICATION_NEED_AUTHORIZE);
			}
		}
	}
	
	private void showNotification(int notif_id) {
		NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
				.setWhen(System.currentTimeMillis());
		
		Intent intentContent = new Intent();
		PendingIntent pendingContent;
		
		switch (notif_id) {
		case NOTIFICATION_SEND:
			//Set an empty Intent for the notification
			pendingContent = PendingIntent.getActivity(
					this, 0, intentContent, PendingIntent.FLAG_CANCEL_CURRENT);
			builder.setContentIntent(pendingContent)
					.setSmallIcon(android.R.drawable.stat_sys_upload)
					.setTicker(getString(R.string.txt_sending))
					.setContentTitle(getString(R.string.txt_sending))
					.setOngoing(true);
			break;
		case NOTIFICATION_ERROR:
			//On error, we create an intent to retry the send
			intentContent.setClass(this, SendTasksActivity.class);
			intentContent.setAction(Intent.ACTION_SEND);
			intentContent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
			intentContent.putExtra(Intent.EXTRA_TEXT, preferences.loadLastSentText());
			PendingIntent pendingError = PendingIntent.getActivity(
					this, 0, intentContent, PendingIntent.FLAG_CANCEL_CURRENT);
			builder.setContentIntent(pendingError)
					.setAutoCancel(true)
					.setSmallIcon(android.R.drawable.stat_notify_error)
					.setTicker(getString(R.string.txt_error_sending))
					.setContentTitle(getString(R.string.txt_error_sending))
					.setContentText(getString(R.string.txt_error_try_again));
			break;
		case NOTIFICATION_NEED_AUTHORIZE:
			intentContent.setClass(this, PhoneToDesktopActivity.class);
			intentContent.setAction(Utils.ACTION_AUTHENTICATE);
			intentContent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
			PendingIntent pendingAuthorize = PendingIntent.getActivity(
					this, 0, intentContent, PendingIntent.FLAG_CANCEL_CURRENT);
			builder.setContentIntent(pendingAuthorize)
					.setAutoCancel(true)
					.setSmallIcon(android.R.drawable.stat_notify_error)
					.setTicker(getString(R.string.txt_error_sending))
					.setContentTitle(getString(R.string.txt_error_sending))
					.setContentText(getString(R.string.txt_need_authorize));
			break;

		default:
			break;
		}
		((NotificationManager)getSystemService(NOTIFICATION_SERVICE))
			.notify(notif_id, builder.build());
	}

	private void cancelNotification(int notif_id) {
		((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancel(notif_id);
	}

	private void handleActionSend(String text) throws IOException,
			UserRecoverableAuthIOException {
		Task new_task = new Task().setTitle(text);
		client.tasks().insert(list_id, new_task).execute();
		preferences.saveLastSentText("");
	}
	
	private void handleActionList() throws IOException,
			UserRecoverableAuthIOException {
		List<Task> list = client.tasks().list(list_id).execute().getItems();
		
		if (list != null) {
			ArrayList<String> ids = new ArrayList<String>();
			ArrayList<String> titles = new ArrayList<String>();
			for (Task task : list) {
				ids.add(task.getId());
				titles.add(task.getTitle());
			}
			
			Intent broadcast = new Intent(Utils.ACTION_LIST_TASKS);
			broadcast.putStringArrayListExtra(Utils.EXTRA_IDS, ids);
			broadcast.putStringArrayListExtra(Utils.EXTRA_TITLES, titles);
			LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
		}
	}
	
	private void handleActionRemove(String task_id) throws IOException,
			UserRecoverableAuthIOException {
		client.tasks().delete(list_id, task_id).execute();
		handleActionList();
	}
}