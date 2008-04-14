import java.io.Serializable;


public class NetworkMessage implements Serializable{
	public enum MessageTypes {ClientAdded, ClientEvent, ClientState};
	
	public MessageTypes type;
	public String name;
	public Point point;
	public Direction orientation;
	public ClientEvent event;
	public int score;
	
	
	public static NetworkMessage createStateMessage(Client c, int score)
	{
		NetworkMessage m = new NetworkMessage(c);
		m.type = MessageTypes.ClientState;
		m.score = score;
		return m;
	}	


	private  NetworkMessage(Client c) {		
		this.name = c.getName();
		this.point = c.getPoint();
		this.orientation = c.getOrientation();
	}


	public static NetworkMessage createClientUpdate(Client c, ClientEvent clientevent) {
		NetworkMessage m = new NetworkMessage(c);
		m.type = MessageTypes.ClientEvent;
		m.event = clientevent;
		return m;
		
	}
}
