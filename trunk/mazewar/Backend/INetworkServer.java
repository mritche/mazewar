package Backend;

import java.io.IOException;

public interface INetworkServer
{
    public abstract void broadcastMessage (Object data)
        throws IOException, ClassNotFoundException, InterruptedException;
}
