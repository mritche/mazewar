import java.io.IOException;

public interface INetworkServer
{
    void broadcastMessage (Object data)
        throws IOException, ClassNotFoundException, InterruptedException;

    void connectToPeer (String host, int port)
        throws IOException, ClassNotFoundException;
}
