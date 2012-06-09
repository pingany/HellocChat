package helloc.android;

import java.util.List;
import java.util.Vector;

import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnKeyListener;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import helloc.protocol.Message;
import helloc.core.*;

class ChatListAdapter extends ArrayAdapter<ChatItem>
{
	ChatListAdapter(Context context, List<ChatItem> list)
	{
		super(context, android.R.layout.simple_list_item_1, list);
	}
}

class ChatItem
{
	Message chat;
	String sender;
	HellocClient client;

	public ChatItem(HellocClient client, Message chat, boolean sentFromMe)
	{
		this.chat = chat;
		this.client = client;
		if (sentFromMe)
			sender = "Me";
		else
			sender = client.getUsernameById(chat.getChat().getUserid());

	}

	public String toString()
	{
		return String.format("%s: %s", sender, chat.getChat().getData()
				.toStringUtf8());
	}

	public String getFullMessageString()
	{
		return chat.toString();
	}
}

public class ChatActivity extends GenericActivity
		implements
			HellocClient.ChatMessageListener
{
	ChatListAdapter chatListAdapter;

	int friendId = -1;
	Friend friend;
	List<ChatItem> chatItems;
	ListView chatsListView;
	EditText chatEditor;
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		Bundle bundle = getIntent().getExtras();

		friendId = bundle.getInt("friendId", -1);

		friend = client.findFriendById(friendId);
		assert friendId >= 0 && friend != null;

		chatItems = new Vector<ChatItem>();
		List<Message> msgs = client.findChatsByFriendId(friendId);
		int userId = client.getUserid();
		for (Message m : msgs)
		{
			boolean sentFromMe = !m.getChat().hasUserid() || m.getChat().getUserid() == userId;
			chatItems.add(new ChatItem(client, m, sentFromMe));
		}
		chatListAdapter = new ChatListAdapter(this, chatItems);

		setContentView(R.layout.chat);
		chatsListView = (ListView) this.findViewById(R.id.chats_list);
		chatsListView.setOnItemClickListener(new ChatItemClickListener());

		chatsListView.setAdapter(chatListAdapter);

		chatEditor = (EditText) findViewById(R.id.chatEditor);
		chatEditor.setOnKeyListener(new OnKeyListener()
		{

			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event)
			{
				// If the event is a key-up event on the "enter" button
				if (keyCode == KeyEvent.KEYCODE_ENTER)
				{
					if (event.getAction() == KeyEvent.ACTION_UP)
						sendEditedText();
					return true;
				}
				return false;
			}

		});
		chatEditor.setOnEditorActionListener(new OnEditorActionListener()
		{

			public boolean onEditorAction(TextView v, int actionId,
					KeyEvent event)
			{
				if (actionId == EditorInfo.IME_ACTION_DONE)
				{
					if (event.getAction() != KeyEvent.ACTION_UP)
						return false;
					else
					{
						sendEditedText();
						return true;
					}
				} else
					return false;
			}
		});

		client.addChatMessageListener(this);
	}

	private class ChatItemClickListener implements OnItemClickListener
	{

		public void onItemClick(AdapterView<?> parent, View view, int position,
				long id)
		{
			assert parent == chatsListView;
			ChatItem f = (ChatItem) parent.getItemAtPosition(position);
			Toast.makeText(ChatActivity.this, f.getFullMessageString(),
					Toast.LENGTH_LONG).show();
		}
	}

	void sendEditedText()
	{
		client.sendChat(friendId, chatEditor.getText().toString());
		chatEditor.setText("");
	}

	public void chatMessageSent(Friend f, Message msg)
	{
		if (f.getUserid() == msg.getChat().getPeerId())
		{
			addMesage(msg, true);
		}
	}

	void addMesage(Message msg, boolean sentFromMe)
	{
		chatItems.add(new ChatItem(client, msg, sentFromMe));
		chatListAdapter.notifyDataSetChanged();
	}

	public void chatMessageReceived(Friend f, Message msg)
	{
		if (f.getUserid() == friendId)
		{
			addMesage(msg, false);
		}
	}
}