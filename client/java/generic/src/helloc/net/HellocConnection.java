package helloc.net;

import helloc.core.AsyncMessagePoster;
import helloc.protocol.Message;
import helloc.utils.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Vector;

import com.google.protobuf.InvalidProtocolBufferException;

public class HellocConnection implements SocketLooper.Listener
{
    public final static int MAX_MESSAGE_LENGTH = 2048;

    public static interface ConnectionListener
    {
        void handleMessage(Message msg);

        void handleSocketClosed();
    }

    class SendBuf extends Vector<ByteBuffer>
    {
        SendBuf()
        {
            super();
        }

        SendBuf(int init_size)
        {
            super(init_size);
        }

        synchronized ByteBuffer[] getData()
        {
            ByteBuffer[] bs = new ByteBuffer[size()];
            this.copyInto(bs);
            return bs;
        }

        synchronized int getByteRemaining()
        {
            int sum = 0;
            for (int i = 0; i < size(); i++)
                sum += get(i).remaining();
            return sum;
        }

        synchronized int getByteSize()
        {
            int sum = 0;
            for (int i = 0; i < size(); i++)
                sum += get(i).position();
            return sum;
        }

        synchronized void removeSent()
        {
            int i = 0, len = size();
            while (i < len && get(i).remaining() == 0)
                ++i;
            removeRange(0, i);
        }

        synchronized void insertAtFront(ByteBuffer buffer)
        {
            removeSent();
            /* insert the message at front, but skip messages which have been sent partly. */
            int i = 0, len = size();
            while (i < len && get(i).position() > 0)
                ++i;
            insertElementAt(buffer, i);
        }

        @Override
        public synchronized boolean add(ByteBuffer buffer)
        {
            int remain = getByteRemaining();
            boolean ret = super.add(buffer);
            if (ret && remain == 0)
                looper.enableDetectingWritable(sc, HellocConnection.this, true);
            return ret;
        }
    }

    class RecvBuffer
    {
        ByteBuffer lenBuffer;
        ByteBuffer msgBuffer;
        SocketChannel sc;

        RecvBuffer(SocketChannel sc)
        {
            this.sc = sc;
            lenBuffer = ByteBuffer.allocate(4);
            lenBuffer.order(ByteOrder.BIG_ENDIAN);
            msgBuffer = ByteBuffer.allocate(MAX_MESSAGE_LENGTH);
        }

        // Return whether we can read again.
        boolean read() throws IOException
        {
            if (lenBuffer.remaining() > 0)
            {
                if (sc.read(lenBuffer) < 0)
                {
                    handleClose();
                    return false;
                }
                if (lenBuffer.remaining() == 0)
                {
                    int len = getInteger(lenBuffer.array(), 0, 4);
                    resetMsgBuffer(len);
                } else
                    return false;
            }
            if (sc.read(msgBuffer) < 0)
            {
                handleClose();
                return false;
            }
            if (msgBuffer.remaining() == 0)
            {
                Message msg;
                try
                {
                    msg = Message.parseFrom(Arrays.copyOfRange(msgBuffer
                            .array(), msgBuffer.arrayOffset(), msgBuffer
                            .arrayOffset()
                            + msgBuffer.position()));
                    callMessageHandler(msg);
                } catch (InvalidProtocolBufferException e)
                {
                    // TODO
                    e.printStackTrace();
                }
                lenBuffer.clear();
                msgBuffer.clear();
                return true;
            }
            return false;
        }

        void resetMsgBuffer(int limit)
        {
            assert msgBuffer.position() == 0;
            if (limit > msgBuffer.capacity())
                msgBuffer = ByteBuffer.allocate(Math.max(limit, msgBuffer
                        .capacity() * 2));
            msgBuffer.clear();
            msgBuffer.limit(limit);
        }
    }

    ConnectionListener listener;
    SocketChannel sc;
    SocketLooper looper;
    String host;
    int port;

    SendBuf sendbuf = new SendBuf();
    RecvBuffer recvbuf = null;

    static final int MAX_CONNECT_RETRIES = 5;   
    int triedBeforeConnected = 0;
    boolean connected = false;

    /* Remember the login message client sent before, resent it after reconnect server. */
    Message loginMsg;
    AsyncMessagePoster asyncMessagePoster;

    public HellocConnection(SocketLooper looper, ConnectionListener listener,
            String host, int port) throws IOException
    {
        this.looper = looper;
        this.listener = listener;
        this.host = host;
        this.port = port;
        reconnectServer();
    }

    public void reconnectServer() throws IOException
    {
        if(triedBeforeConnected > MAX_CONNECT_RETRIES)
        {
            return;
        }
        triedBeforeConnected++;
        if (sc != null)
        {
            looper.removeConnection(this, sc);
        }
        looper.addConnection(this, host, port);
        if(loginMsg != null)
            sendMessage(loginMsg);
    }

    public void setAsyncMessagePoster(AsyncMessagePoster h)
    {
        asyncMessagePoster = h;
    }

    void callSocketClosed()
    {
        if(asyncMessagePoster != null)
        {
            asyncMessagePoster.postAsyncMessage(new Runnable()
            {
                public void run()
                {
                    listener.handleSocketClosed();
                }
            }, 0);
        }
        else
        {
            listener.handleSocketClosed();
        }   
    }

    void callMessageHandler(final Message msg)
    {
        if(asyncMessagePoster != null)
        {
            asyncMessagePoster.postAsyncMessage(new Runnable()
            {
                public void run()
                {
                    listener.handleMessage(msg);
                }
            }, 0);
        }
        else
        {
            listener.handleMessage(msg);
        }
    }

    public static int toUnsigned(byte b) {  
        return ((int) b) & 0xFF;  
        } 
    static int getInteger(byte[] b, int offset, int len)
    {
        int sum = 0;
        for (int i = offset; i < offset + len; i++)
            sum = sum * 256 + toUnsigned(b[i]);
        return sum;
    }

    static void writeInteger(int integer, byte[] b, int offset, int len)
    {
        for (int i = offset + len - 1; i >= offset; i--)
        {
            b[i] = (byte) (integer % 256);
            integer /= 256;
        }
        assert integer == 0;
    }

    public void sendMessage(Message msg)
    {
        byte[] b = msg.toByteArray();
        byte[] bwithlen = new byte[b.length + 4];
        writeInteger(b.length, bwithlen, 0, 4);
        System.arraycopy(b, 0, bwithlen, 4, b.length);
        ByteBuffer bb = ByteBuffer.wrap(bwithlen);
        if (msg.getType() == Message.Type.LOGIN_REQ)
        {
            loginMsg = msg;
            sendbuf.insertAtFront(bb);
        }
        else    
        {
            /* Normal messages */
            sendbuf.add(bb);
        }
    }

    public void handleConnected()
    {
        connected = true;
        triedBeforeConnected = 0;
    }

    public void handleRead() throws IOException
    {
        assert recvbuf != null;
        while (recvbuf.read())
            ; // Currently recv data until we can't read
    }

    /* following functions are running in socket looper thread. */
    public void handleWrite() throws IOException
    {
        synchronized (sendbuf)
        {
            if (sendbuf.getByteRemaining() > 0)
            {
                // use sendbuf.size(), but not sendbuf.getData().length,
                // because maybe sendbuf.getData().length is equal to
                // sendbuf.capacity()
                sc.write(sendbuf.getData(), 0, sendbuf.size());
                if (sendbuf.getByteRemaining() == 0)
                    looper.enableDetectingWritable(sc, this, false);
            }
            sendbuf.removeSent();
        }
    }

    public void handleClose()
    {
        try
        {
            sc.close();
            sc = null;
            connected = false;
            sendbuf.clear();
            if (recvbuf != null)
                recvbuf.clear();
            callSocketClosed();
        } catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void handleError(Exception e, String msg)
    {
        Logger.w(String.format(
                "HellocConnection.handleError called e:%s, msg:%s", e, msg));
        handleClose();
        if(asyncMessagePoster != null)
        {
            asyncMessagePoster.postAsyncMessage(new Runnable()
            {
                public void run()
                {
                    try
                            {
                                reconnectServer();
                            } catch (IOException e1)
                            {
                                // TODO Auto-generated catch block
                                e1.printStackTrace();
                            }
                }
            }, 1000 * 30);
        }

    }

    public void socketCreated(SocketChannel sc)
    {
        this.sc = sc;
        this.recvbuf = new RecvBuffer(sc);
    }

}
