package helloc.core;

import helloc.protocol.Message;
import helloc.utils.Logger;
import helloc.net.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

import com.google.protobuf.ByteString;

public class HellocClient implements HellocConnection.ConnectionListener
{
    static final String LOCAL_HOST = "10.0.2.2";

    /* Incremental message IDs */
    int messageId = 0;

    HellocConnection con;
    SocketLooper looper;

    List<OnlineStatusListener> onlineStatuslistener = new Vector<OnlineStatusListener>(4);
    List<FriendsChangedListener> friendChangedListener = new Vector<FriendsChangedListener>(4);
    List<ChatMessageListener> chatMessageListener = new Vector<ChatMessageListener>(4);

    List<Friend> friendsList = new Vector<Friend>();
    List<Message> chatList = new Vector<Message>();
    List<Message> sentChatList = new Vector<Message>();

    Message.OnlineStatus currentStatus = Message.OnlineStatus.OFFLINE;
    int userid = -1;

    AsyncMessagePoster asyncMessagePoster;
    HellocStorage storage;

    String appDir = "/";

    public void setAppDir(String appDir)
    {
        assert appDir.endsWith("/");
        this.appDir = appDir;
    }

    String getPath(String filename)
    {
        return appDir + filename;
    }

    public void setAsyncMessagePoster(AsyncMessagePoster h)
    {
        asyncMessagePoster = h;
        if (con != null)
            con.setAsyncMessagePoster(h);
    }

    public int getUserid()
    {
        return userid;
    }

    void setUserid(int uid)
    {
        userid = uid;
    }

    public List<Friend> getFriends()
    {
        return friendsList;
    }

    // Get messages between me and the friend
    public List<Message> findChatsByFriendId(int friendId)
    {
        List<Message> list = new Vector<Message>();
        for (Message m : chatList)
        {
            if (m.getChat().getUserid() == friendId)
                list.add(m);
        }
        for (Message m : sentChatList)
        {
            if (m.getChat().getPeerId() == friendId)
                list.add(m);
        }
        return list;
    }

    public static interface OnlineStatusListener
    {
        void loginFailed(Message.Status status);
        void statusChanged(Message.OnlineStatus oldStatus,
                Message.OnlineStatus newStatus);
    }

    public static interface FriendsChangedListener
    {
        void friendChanged(List<Message.Friend> fs);
    }

    public static interface ChatMessageListener
    {
        void chatMessageSent(Friend f, Message msg);
        void chatMessageReceived(Friend f, Message msg);
    }

    protected HellocClient()
    {
        try
        {
            looper = new SocketLooper();
        } catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static HellocClient open(AsyncMessagePoster poster)
    {
        HellocClient client = new HellocClient();
        client.setAsyncMessagePoster(poster);
        client.startLooper();
        try
        {
            client.createConnection();
        } catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return client;
    }

    public void setStorage(HellocStorage storage)
    {
        this.storage = storage;
    }

    public HellocStorage getStorage()
    {
        return storage;
    }

    void save()
    {
        assert storage != null;
    }

    void load()
    {
        assert storage != null;
    }

    Message.OnlineStatus getOnlineStatus()
    {
        return currentStatus;
    }

    void setOnlineStatus(Message.OnlineStatus s)
    {
        if(s == currentStatus)
            return;
        Message.OnlineStatus oldStatus = currentStatus;
        currentStatus = s;
        for (OnlineStatusListener l : onlineStatuslistener)
            l.statusChanged(oldStatus, s);
    }

    public void addOnlineStatusChangedListener(OnlineStatusListener l)
    {
        onlineStatuslistener.add(l);
    }

    public void removeOnlineStatusChangedListener(OnlineStatusListener l)
    {
        onlineStatuslistener.remove(l);
    }

    public void addFriendsChangedListener(FriendsChangedListener l)
    {
        friendChangedListener.add(l);
    }

    public void removeFriendsChangedListener(FriendsChangedListener l)
    {
        friendChangedListener.remove(l);
    }

    public void addChatMessageListener(ChatMessageListener l)
    {
        chatMessageListener.add(l);
    }

    public void removeChatMessageListener(ChatMessageListener l)
    {
        chatMessageListener.remove(l);
    }

    public void startLooper()
    {
        looper.start();
    }

    public void stop()
    {
        looper.stop();
    }

    void joinLooper()
    {
        looper.join();
    }

    public void close()
    {
        if (null != storage)
            storage.close();
    }

    public void createConnection() throws IOException
    {
        con = new HellocConnection(looper, this, LOCAL_HOST, 8080);
        if (asyncMessagePoster != null)
            con.setAsyncMessagePoster(asyncMessagePoster);
    }

    int newId()
    {
        return ++messageId;
    }

    public void handleMessage(Message msg)
    {
        Logger.i("HellocClient: message received: %s", msg.toString());
        switch (msg.getType())
        {
            case LOGIN_RESPONSE :
            {
                Message.Status status = msg.getLoginResponse().getStatus();
                if (getOnlineStatus() != Message.OnlineStatus.OFFLINE
                        && status == Message.Status.OK)
                    break;
                {

                    if (status == Message.Status.OK)
                    {
                        setUserid(msg.getLoginResponse().getUserid());
                        setOnlineStatus(Message.OnlineStatus.ONLINE);
                    } else
                    {
                        for (OnlineStatusListener l : onlineStatuslistener)
                            l.loginFailed(status);
                    }
                }
            }
                break;
            case FRIENDS_LIST :
            {
                updateFriendsFromMessage(msg);
                for (FriendsChangedListener l: friendChangedListener)
                {
                    l.friendChanged(msg.getFriendsList()
                            .getFriendsList());
                }
            }
                break;
            case CHAT :
            {
                assert msg.getChat().hasUserid();
                if (getUserid() != msg.getChat().getPeerId())
                {
                    Logger.w("Receive a message not to me: %s", msg.toString());
                    // Discard messages that don't send to us
                    break;
                }
                Friend f = findFriendById(msg.getChat().getUserid());
                if (f == null)
                {
                    Logger.w("Receive a message from a unknown people: %s",
                            msg.toString());
                    break;
                }
                chatList.add(msg);
                for (ChatMessageListener l : chatMessageListener)
                {
                    l.chatMessageReceived(f, msg);
                }
            }
                break;
        }
    }

    public Friend findFriendById(int userid)
    {
        for (Friend f : friendsList)
            if (f.getUserid() == userid)
                return f;
        return null;
    }

    public String getUsernameById(int userid)
    {
        Friend f = findFriendById(userid);
        if (f != null)
            return f.getUsername();
        else
            return "Unknown people";
    }

    void updateFriendsFromMessage(Message msg)
    {
        assert msg.getType() == Message.Type.FRIENDS_LIST;
        List<Message.Friend> mflist = msg.getFriendsList().getFriendsList();
        for (Message.Friend mf : mflist)
        {
            int userid = mf.getUserid();
            Friend f = findFriendById(userid);
            if (f == null)
            {
                f = new Friend(userid);
                friendsList.add(f);
            }
            if (mf.hasUsername())
                f.setUsername(mf.getUsername());
            if (mf.hasOnlineStatus())
                f.setOnlineStatus(mf.getOnlineStatus());

        }

    }

    public void handleSocketClosed()
    {
        Logger.i("HellocClient: socket closed");
        setOnlineStatus(Message.OnlineStatus.OFFLINE);
    }

    public void login(String username, String password)
    {
        Message msg = Message
                .newBuilder()
                .setId(newId())
                .setType(Message.Type.LOGIN_REQ)
                .setLogin(
                        Message.Login.newBuilder().setUsername(username)
                                .setPassword(password)).build();
        con.sendMessage(msg);
    }

    public void fetchFriends()
    {
        Message msg = Message.newBuilder().setId(newId())
                .setType(Message.Type.GET_FRIENDS).build();
        con.sendMessage(msg);
    }

    public void sendChat(int userid, String text)
    {
        Friend friend = findFriendById(userid);
        assert friend != null;

        Message msg = Message
                .newBuilder()
                .setId(newId())
                .setType(Message.Type.CHAT)
                .setChat(
                        Message.Chat.newBuilder()
                                .setType(Message.Chat.Type.TEXT)
                                .setUserid(getUserid())
                                .setPeerId(userid)
                                .setData(ByteString.copyFromUtf8(text)))
                .build();
        con.sendMessage(msg);
        sentChatList.add(msg);
        for(ChatMessageListener l:chatMessageListener)
            l.chatMessageSent(friend, msg);
    }

    ByteString getFileContent(File f)
    {
        try
        {
            FileInputStream s = new FileInputStream(f);
            byte[] b = new byte[(int) f.length()];
            s.read(b);
            s.close();
            return ByteString.copyFrom(b);
        } catch (FileNotFoundException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    void sendFile(int userid, String filepath)
    {
        File f = new File(filepath);
        Message msg = Message
                .newBuilder()
                .setId(newId())
                .setType(Message.Type.CHAT)
                .setChat(
                        Message.Chat.newBuilder()
                                .setType(Message.Chat.Type.FILE)
                                .setPeerId(userid).setFilename(f.getName())
                                .setData(getFileContent(f))).build();
        con.sendMessage(msg);
        // TODO, record it at chatList ?
    }

    void test()
    {
        login("user1", "helloc");
        while (true)
        {

            // fetchFriends();
            // sendChat(1, "Helloc 1");
            sendFile(1, "G:\\helloc\\protocol\\test-data\\WelcomeScan.jpg");
            try
            {
                Thread.sleep(5 * 1000);
            } catch (InterruptedException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    public static void main(String args[])
    {
        HellocClient client = new HellocClient();
        client.startLooper();
        try
        {
            client.createConnection();
        } catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        client.test();

        client.joinLooper();
    }
}
