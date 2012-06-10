package helloc.android;

import helloc.core.Friend;
import helloc.core.HellocStorage;
import helloc.protocol.Message;

import java.util.List;
import java.util.Vector;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.preference.PreferenceActivity;

import com.google.protobuf.InvalidProtocolBufferException;

class HellocAndroidStorage implements HellocStorage
{
	Context context;
	String database_name;
	private static final int CURRENT_DATABASE_VERSION = 1;
	private SQLiteDatabase database;
	private SQLiteStatement addFriendStmt, addChatStmt;

	HellocAndroidStorage(Context context)
	{
		this.context = context;
	}

	public void open(String username, String password)
	{
		database_name = username + ".db";

		database = context.openOrCreateDatabase(database_name,
				Context.MODE_PRIVATE, null);
		database.setVersion(CURRENT_DATABASE_VERSION);

		database.execSQL("CREATE TABLE IF NOT EXISTS friends (uid INTEGER PRIMARY KEY ASC "
				+ ", username TEXT NOT NULL, info BLOB DEFAULT ''); ");
		database.execSQL("CREATE TABLE IF NOT EXISTS chats (friendId INTEGER NOT NULL, "
				+ "	toMe INTEGER, content BLOB NOT NULL, time INTEGER); ");

		addFriendStmt = database
				.compileStatement("INSERT INTO friends(uid, username, info) "
						+ "			VALUES (?, ?, ?);");

		addChatStmt = database
				.compileStatement("INSERT INTO chats VALUES (?, ?, ?, ?);");
	}

	public void close()
	{
		if (addFriendStmt != null)
			addFriendStmt.close();
		if (addChatStmt != null)
			addChatStmt.close();
		if (database != null)
			database.close();
	}

	public void addChatMessage(Message msg, boolean toMe)
	{
		addChatStmt.bindLong(1, msg.getChat().getPeerId());
		addChatStmt.bindLong(2, toMe ? 1 : 0);
		addChatStmt.bindBlob(3, msg.toByteArray());
		addChatStmt.bindLong(4, System.currentTimeMillis() / 1000);
		addChatStmt.execute();
	}

	public void addFriend(Friend f)
	{
		addFriendStmt.bindLong(1, f.getUserid());
		addFriendStmt.bindString(2, f.getUsername());
		addFriendStmt.bindNull(3);
		addFriendStmt.execute();
	}

	public static final String[] loadFriendsColumns = new String[]{"uid",
			"username"};

	public List<Friend> loadFriends()
	{
		Cursor cursor = database.query("friends", loadFriendsColumns, null,
				null, null, null, database_name);
		Vector<Friend> friends = new Vector<Friend>(cursor.getCount());
		while (cursor.moveToNext())
		{
			Friend f = new Friend(cursor.getInt(cursor.getColumnIndex("uid")));
			f.setUsername(cursor.getString(cursor.getColumnIndex("username")));
			friends.add(f);
		}
		cursor.close();
		assert friends.size() == friends.capacity();
		return friends;
	}

	// public static final String[] loadChatsColumns = new String[]{""};
	public List<Message> loadChats(int friendId, int limit)
	{
		Cursor cursor = database.query(false, "chats", // table
				null, // colomns
				"friendId = ?", // selection
				new String[]{String.valueOf(friendId)}, // selection args
				null, // group by
				null, // having
				"time desc", // order by
				String.valueOf(limit) // limit
				);
		Vector<Message> chats = new Vector<Message>(cursor.getCount());
		while (cursor.moveToNext())
		{
			Message msg;
			try
			{
				msg = Message.parseFrom(cursor.getBlob(cursor
						.getColumnIndex("content")));
				chats.add(msg);
			} catch (InvalidProtocolBufferException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		cursor.close();
		assert chats.size() == chats.capacity();
		return chats;
	}

	private SharedPreferences getPrefs() {
        return context.getSharedPreferences("helloc", PreferenceActivity.MODE_PRIVATE);
    }

	@Override
	public void saveDefaultAccount(Account account)
	{
		//FIXME, encryption here.
		SharedPreferences.Editor editor = getPrefs().edit();
		editor.putString("default_username", account.username);
		editor.putString("default_password", account.password);
		editor.putBoolean("default_autologin", account.autoLogin);
		editor.commit();
	}

	@Override
	public Account loadDefaultAccount()
	{
		//FIXME, encryption here.
		SharedPreferences refs = getPrefs();
		Account account = new Account();
		account.username = refs.getString("default_username", "");
		if (account.username.equals(""))
			return null;
		account.password = refs.getString("default_password", "");
		account.autoLogin = refs.getBoolean("default_autologin", false);
		return account;
	}

}
