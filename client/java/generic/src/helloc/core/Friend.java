package helloc.core;

import helloc.protocol.Message;

public class Friend
{
	int userid;
	String username;
	Message.OnlineStatus status;

	public Friend(int userid)
	{
		this.userid = userid;
		this.username = "No Name";
		this.status = Message.OnlineStatus.OFFLINE;
	}

	public void setOnlineStatus(Message.OnlineStatus s)
	{
		status = s;
	}

	public void setUsername(String s)
	{
		username = s;
	}

	public int getUserid()
	{
		return userid;
	}

	public String getUsername()
	{
		return username;
	}

	public Message.OnlineStatus getStatus()
	{
		return status;
	}

	public String toString()
	{
		return String.format("Friend %s %s", username, status);
	}
}