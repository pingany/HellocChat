package helloc.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import helloc.core.*;

public class GenericActivity extends Activity
{
	static final int DIALOG_YES_NO_MESSAGE = 1;

	HellocClient client;
	ActivityListener activityListener;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		HellocApplication app = (HellocApplication) getApplication();
		activityListener = app;

		client = app.getClient();
		assert client != null;
	}

	@Override
	protected void onResume()
	{
		activityListener.onShow(this);
		super.onResume();
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		activityListener.onHide(this);
	}

	@Override
	protected Dialog onCreateDialog(int id)
	{
		switch (id)
		{
			case DIALOG_YES_NO_MESSAGE :
				return new AlertDialog.Builder(this)
						.setTitle("Login failed")
						.setPositiveButton("OK",
								new DialogInterface.OnClickListener()
								{
									public void onClick(DialogInterface dialog,
											int whichButton)
									{

										/* User clicked OK so do some stuff */
									}
								})
						.setNegativeButton("Cancel",
								new DialogInterface.OnClickListener()
								{
									public void onClick(DialogInterface dialog,
											int whichButton)
									{

										/* User clicked Cancel so do some stuff */
									}
								}).create();
		}
		return null;
	}
}