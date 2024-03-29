package helloc.net;

import helloc.utils.Logger;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Vector;

public class SocketLooper implements Runnable
{
	interface Listener
	{
		void handleConnected();

		void handleRead() throws IOException;

		void handleWrite() throws IOException;

		void handleClose();

		void handleError(Exception e, String msg);

		void socketCreated(SocketChannel sc);
	}

	Vector<Listener> listeners = new Vector<Listener>();
	boolean runnable = true;
	Selector stor;
	Thread thread;
	int errors = 0;

	public SocketLooper() throws IOException
	{
		stor = Selector.open();
	}

	void addConnection(final Listener l, final String server, final int port)
			throws IOException
	{
		final SocketChannel sc = SocketChannel.open();
		sc.configureBlocking(false);
		listeners.add(l);
		l.socketCreated(sc);
		if (sc.connect(new InetSocketAddress(server, port)))
			l.handleConnected();
		if (stor != null)
		{
			synchronized (this)
			{
				stor.wakeup();
				sc.register(stor, sc.validOps() /* & ~SelectionKey.OP_WRITE */,
						l);

			}
		}
	}

	void removeConnection(final Listener l, SocketChannel sc)
	{
		listeners.remove(l);
		try
		{
			SelectionKey key = sc.keyFor(stor);
			key.cancel();
			sc.close();
		} catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	void enableDetectingWritable(final SocketChannel sc, final Listener l,
			final boolean enable)
	{
		// synchronized (this)
		// {
		// stor.wakeup();
		// if (sc.isOpen())
		// {
		// try
		// {
		// sc.register(stor,
		// enable ? (sc.validOps() | SelectionKey.OP_WRITE)
		// : (sc.validOps() & ~SelectionKey.OP_WRITE),
		// l);
		// } catch (final ClosedChannelException e)
		// {
		// e.printStackTrace();
		// }
		// }
		// }
	}

	public void stop()
	{
		runnable = false;
	}

	public void start()
	{
		if (runnable)
		{
			thread = new Thread(this);
			thread.start();
		}
	}

	public void join()
	{
		if (thread != null)
		{
			try
			{
				thread.join();
			} catch (final InterruptedException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	void error(final Exception e, final String msg)
	{
		for (final Listener l : listeners)
			l.handleError(e, msg);
	}

	public void run()
	{

		for (; runnable;)
		{
			int channelNum = 0;

			try
			{
				synchronized (this)
				{
					// FIXME, may this synchronization be optimized to nothing,
					// if that, deadlock may occur ?
				}

				channelNum = stor.select(2000);
			} catch (final InterruptedIOException e)
			{
				Logger.w("Looper intterrupted:");
				error(e, "");
				e.printStackTrace();
				break;
			} catch (final ClosedSelectorException e)
			{
				Logger.e("Selector closed, what happened ?");
				error(e, "");
				e.printStackTrace();
				break;
			} catch (final IOException e)
			{
				Logger.e("Selector.select error, what happened ?");
				error(e, "");
				++errors;
				if (errors > 5)
				{
					break;
				}
				e.printStackTrace();
			}
			if (channelNum > 0)
			{
				try
				{
					final Iterator<SelectionKey> it = stor.selectedKeys()
							.iterator();
					while (it.hasNext())
					{
						final SelectionKey key = it.next();
						if (!key.isValid())
							continue;
						final SocketChannel sc = (SocketChannel) key.channel();
						final Listener listener = ((Listener) key.attachment());
						if (key.isConnectable())
						{
							Logger.i("Connectable");
							if (sc.isConnectionPending())
							{
								boolean connected = false;
								try
								{
									connected = sc.finishConnect();
								} catch (final IOException e)
								{
									e.printStackTrace();
								}
								if (connected)
								{
									assert sc.isConnected();
									listener.handleConnected();
								} else
								{
									listener.handleError(null,
											"Finish connect error");
								}
							}

						}
						/* Maybe key is cancelled on above operation */
						if (key.isValid() && key.isReadable()
								&& sc.isConnected())
						{
							Logger.i("Readable");
							try
							{
								listener.handleRead();
							} catch (final Exception e)
							{
								listener.handleError(e,
										"listener.handleRead error");
								e.printStackTrace();
							}
						}
						/* Maybe key is cancelled on above operation */
						if (key.isValid() && key.isWritable()
								&& sc.isConnected())
						{
							Logger.i("Writable");
							try
							{
								listener.handleWrite();
							} catch (final Exception e)
							{
								listener.handleError(e,
										"listener.handleWrite error");
								e.printStackTrace();
							}
							try
							{
								Thread.sleep(1000);
							} catch (InterruptedException e)
							{
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
						it.remove();
					}
				} catch (Exception e)
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		try
		{
			stor.close();
		} catch (final IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
