package Backend;

import java.net.Socket;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;

public class MazewarClientConnectionHandler implements Runnable
{
    private final MazewarServer _Parent;
    private final Socket _Socket;
 
    MazewarClientConnectionHandler(MazewarServer parent, Socket incoming)
    {
        _Socket = incoming;
        _Parent = parent;
    }

	public void run ()
	{
        //Log.Write("Accepted incoming connection from " + _Socket.getRemoteSocketAddress().toString());

        try
		{
			ObjectInputStream oisClientRequest = new ObjectInputStream(_Socket.getInputStream());
			ObjectOutputStream oosClientResponse = new ObjectOutputStream(_Socket.getOutputStream());

			PeerMessage request;

			// Get a request from the client.
			while ( (request = (PeerMessage)(oisClientRequest.readObject())) != null )
			{
                oosClientResponse.writeObject(new PeerMessage(_Parent.Me, null, PeerMessageID.EMPTY));

                // Process the message in the background.
                (new Thread(new MazewarMessageHandler(_Parent, request))).start();
            }

			oisClientRequest.close();
			oosClientResponse.close();
			_Socket.close();
		}
		catch (IOException e)
        {
            e.printStackTrace();
            System.exit(-1);
        }
		catch (ClassNotFoundException e)
        {
            e.printStackTrace();
            System.exit(-1);
        }
	}
}
