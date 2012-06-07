package helloc.core;

public interface AsyncMessagePoster
{
	void postAsyncMessage(Runnable runnable);
}
