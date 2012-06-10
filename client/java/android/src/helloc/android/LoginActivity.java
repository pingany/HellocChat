package helloc.android;

import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnKeyListener;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import helloc.protocol.Message;
import helloc.core.*;

public class LoginActivity extends GenericActivity
		implements
			HellocClient.OnlineStatusListener
{

	EditText usernameEditor, passwordEditor;
	ProgressBar loginProgressBar;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		client.addOnlineStatusChangedListener(this);

    	requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

		setContentView(R.layout.login);

		usernameEditor = (EditText) findViewById(R.id.usernameEditor);
		passwordEditor = (EditText) findViewById(R.id.passwordEditor);
		loginProgressBar = (ProgressBar) findViewById(R.id.loginProgressBar);
		loginProgressBar.setIndeterminate(true);

		OnKeyListener keyListener = new OnKeyListener()
		{
			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event)
			{
				// If the event is a key-up event on the "enter" button
				if (v == (View) passwordEditor
						&& keyCode == KeyEvent.KEYCODE_ENTER)
				{
					if (event.getAction() == KeyEvent.ACTION_UP)
						login();
					return true;
				}
				return false;
			}

		};

		OnEditorActionListener editorActionListener = new OnEditorActionListener()
		{
			@Override
			public boolean onEditorAction(TextView v, int actionId,
					KeyEvent event)
			{
				if (actionId == EditorInfo.IME_ACTION_DONE)
				{
					if (event.getAction() != KeyEvent.ACTION_UP)
						return false;
					else
					{
						login();
						return true;
					}
				} else
					return false;
			}
		};

		usernameEditor.setImeOptions(EditorInfo.IME_ACTION_NEXT);

		passwordEditor.setOnKeyListener(keyListener);
		passwordEditor.setImeOptions(EditorInfo.IME_ACTION_DONE);
		passwordEditor.setOnEditorActionListener(editorActionListener);

		loadDefaultAccont();
	}

	public void onLogin(View view)
	{
		login();
	}

	public void onRegisterAccount(View view)
	{
		registerAccount();
	}

	void loadDefaultAccont()
	{
		HellocStorage.Account account = client.getStorage().loadDefaultAccount();
		if (account == null)
			return;
		usernameEditor.setText(account.username);
		passwordEditor.setText(account.password);
		if(account.autoLogin)
			login();
	}

	void login()
	{
		String username, password;
		username = usernameEditor.getText().toString().trim();
		password = passwordEditor.getText().toString().trim();
		if (username.equals("") || password.equals(""))
		{
			Toast.makeText(this, "username and password can't be empty",
					Toast.LENGTH_SHORT).show();
		} else
		{

			client.login(username, password);
			client.fetchFriends();
			client.sendChat(1, "Hello, I am from android");
			loginProgressBar.setVisibility(View.VISIBLE);
		}
	}

	void registerAccount()
	{

	}

	public void loginFailed(Message.Status status)
	{
		loginProgressBar.setVisibility(View.GONE);
		showDialog(GenericActivity.DIALOG_YES_NO_MESSAGE);
	}

	public void statusChanged(Message.OnlineStatus oldStatus,
			Message.OnlineStatus newStatus)
	{
		if (newStatus == Message.OnlineStatus.ONLINE)
		{
			loginProgressBar.setVisibility(View.GONE);
			if(((CheckBox)findViewById(R.id.autoLoginCheckBox)).isChecked())
			{
				HellocStorage.Account account = new HellocStorage.Account();
				account.autoLogin = true;
				account.username = usernameEditor.getText().toString().trim();
				account.password = passwordEditor.getText().toString().trim();
				client.getStorage().saveDefaultAccount(account);
			}
			Intent intent = new Intent(this, FriendsListActivity.class);
			startActivity(intent);
			this.finish();
		}
	}
}
