import java.net.Socket;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;

public class MazewarServerRequestHandler implements Runnable
{
    private final MazewarServer _Parent;
    private final Socket _Socket;

    MazewarServerRequestHandler (MazewarServer parent, Socket incoming)
    {
        _Socket = incoming;
        _Parent = parent;
    }

	public void run ()
	{
		try
		{
			ObjectInputStream oisClientRequest = new ObjectInputStream(_Socket.getInputStream());
			ObjectOutputStream oosClientResponse = new ObjectOutputStream(_Socket.getOutputStream());

			PeerMessage request;

			// Get a request from the client.
			while ( (request = (PeerMessage)(oisClientRequest.readObject())) != null )
			{
                PeerMessage response = new PeerMessage(_Parent.Me, -1, PeerMessageID.EMPTY);

                // L-L-L-L-Lamport!
                _Parent.Clock.syncMessageTime(request.Timestamp);
                
                if (request.MessageID == PeerMessageID.GOODBYE)
                {
                    break;
                }
                else if (request.MessageID == PeerMessageID.HELLO)
                {
                    _Parent.processHello(request.Source);
                }
                else if (request instanceof NewPeerMessage)
                {
                    _Parent.processNewPeer((NewPeerMessage)request);
                }
                else if (request instanceof PeerClientMessage)
                {
                    _Parent.processClientMessage((PeerClientMessage)request);
                }
                else if (request instanceof PeerACK)
                {
                    _Parent.processAck((PeerACK)request);
                }

                oosClientResponse.writeObject(response);
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
