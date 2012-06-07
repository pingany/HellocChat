package helloc.android;

import java.io.IOException;

import android.app.*;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import helloc.protocol.*;
import helloc.utils.*;
import helloc.core.*;

interface ActivityListener
{
	void onShow(Activity a);
	void onHide(Activity a);
}

class AndroidLogger implements Logger.Loggable
{
	public void log(Logger.Level level, String category, String msg)
	{
		Log.i(category, String.format("%s: %s\n", level, msg));
	}
}

public class HellocApplication extends Application
		implements
			ActivityListener,
			HellocClient.OnlineStatusListener,
			HellocClient.ChatMessageListener,
			AsyncMessagePoster
{
	HellocClient client;
	Activity showingActivity;

	Handler messageHandler;

	@Override
	public void onCreate()
	{
		Logger.setLogVendor(new AndroidLogger());

		messageHandler = new Handler();

		client = new HellocClient();
		client.addOnlineStatusChangedListener(this);
		client.addChatMessageListener(this);
		client.setAsyncMessagePoster(this);

		client.startLooper();
		try
		{
			client.createConnection();
		} catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public HellocClient getClient()
	{
		return client;
	}

	@Override
	public void onTerminate()
	{
		client.removeOnlineStatusChangedListener(this);
		client.removeChatMessageListener(this);
		client.stop();
		super.onTerminate();
	}

	public void onShow(Activity a)
	{
		showingActivity = a;
	}

	public void onHide(Activity a)
	{
		if (a == showingActivity)
			showingActivity = null;
	}

	public void loginFailed(Message.Status status)
	{
		// if (showingActivity != null)
		// {
		// 	showingActivity.showDialog(GenericActivity.DIALOG_YES_NO_MESSAGE);
		// }
	}
	public void statusChanged(Message.OnlineStatus oldStatus,
			Message.OnlineStatus newStatus)
	{
		if (showingActivity != null)
		{
			Toast.makeText(
					showingActivity,
					String.format("Online status changed from %s to %s",
							oldStatus, newStatus), Toast.LENGTH_LONG).show();
		}
	}

	public void chatMessageReceived(Friend f, Message msg)
	{
		if (showingActivity != null)
		{
			Toast.makeText(
					showingActivity,
					String.format("Chat message (%s) received from (%s)",
							f.toString(), msg.toString()), Toast.LENGTH_LONG)
					.show();
		}
	}

	public void postAsyncMessage(Runnable runnable)
	{
		messageHandler.post(runnable);
	}

	@Override
	public void chatMessageSent(Friend f, Message msg)
	{
		// TODO Auto-generated method stub

	}

}
