package Backend;

import java.io.IOException;

public interface INetworkServer
{
    public abstract void broadcastMessage (Object data)
        throws IOException, ClassNotFoundException, InterruptedException;

    public abstract void connectToPeer (String host, int port)
        throws IOException, ClassNotFoundException;
}
