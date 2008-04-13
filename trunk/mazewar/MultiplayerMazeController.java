import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


public class MultiplayerMazeController implements MazeListener, ClientListener {
	private Client ownClient;
	private MulticastQueue queue;
	private MazeImpl maze;
	private Map<String, RemoteClient> clients = new HashMap<String, RemoteClient>();
	public MultiplayerMazeController(Client client, MulticastQueue queue, MazeImpl maze)
	{
		ownClient = client;
		ownClient.addClientListener(this);
		this.queue = queue;
		this.maze = maze;
	}

	@Override
	public void clientAdded(Client client) {
		try {
			if(client == ownClient || client instanceof RobotClient)
			{
				System.out.println("Sending client added");
				queue.broadcast(NetworkMessage.createAddedMessage(client));
			}
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		
	}

	@Override
	public void clientFired(Client client) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void clientKilled(Client source, Client target) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void clientRemoved(Client client) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mazeUpdate() {
		// TODO Auto-generated method stub
		
	}

	public synchronized void deliverMessage(NetworkMessage message) {
		switch(message.type)
		{
		case ClientAdded:
			addNewRemoteClient(message);
			break;
		case ClientEvent:
			fireClientEvent(message);
			break;
		default:
			System.out.println("Unknown network message");
		}
		
	}

	private void fireClientEvent(NetworkMessage message) {
		RemoteClient client = clients.get(message.name);
		client.fireEvent(message.event);
		
	}

	private void addNewRemoteClient(NetworkMessage message) {
		RemoteClient client = new RemoteClient(message.name);
		clients.put(message.name, client);
		maze.addClient(client, message.point, message.orientation);
		System.out.println("Added new remote client");
		
	}

	@Override
	public void clientUpdate(Client client, ClientEvent clientevent) {
		try {
			System.out.println("Sending client update");
			queue.broadcast(NetworkMessage.createClientUpdate(client, clientevent));
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

}
