package helloc.core;

import java.util.List;

import helloc.protocol.Message;

public interface HellocStorage
{
	public void open(String username, String password);

	public void close();

	public void addChatMessage(Message msg, boolean toMe);

	public void addFriend(Friend f);

	public List<Friend> loadFriends();

	public List<Message> loadChats(int friendId, int limit);
}