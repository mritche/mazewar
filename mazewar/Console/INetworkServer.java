import java.io.IOException;

public interface INetworkServer
{
    public abstract void setPeerListener (INetworkPeer listener);

    void broadcastMessage (Object data)
        throws IOException, ClassNotFoundException;
}
