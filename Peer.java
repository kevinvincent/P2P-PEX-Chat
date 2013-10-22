import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import javax.swing.JComboBox;
import javax.swing.JOptionPane;

/*
 * Represents a Node - Ip and Port
 */
class Node{
	public String ip;
	public int port;
	
	//Create Node object from ip and port
	public Node(String ip, int port) {
		this.ip = ip;
		this.port = port;
	}
	
	//Convert string representation to Node Object
	public Node(String full) {
		String[] peerDetails = full.split("/");
		ip = peerDetails[0];
		port = Integer.parseInt(peerDetails[1]);
	}
	
	//Return string representation
	public String toString() {
		return ip+"/"+port;
	}
}

/*
 * Represent a Message - Sender (Node), Type, Content
 */
class Message{
	public Node sender;
	public String type;
	public String content;
	public Node recipient;
	
	//Create message object with passed in info
	public Message(Node sender, String type, String data, Node recipient) {
		this.sender = sender;
		this.type = type;
		this.content = data;
		this.recipient = recipient;
	}
	
	//Create message object from serialized string
	public Message(String serialized) {
		List<String> mess = ChatUtils.readRawMessage(serialized);
		sender = new Node(mess.get(0));
		type = mess.get(1);
		content = mess.get(2);
		recipient = new Node(mess.get(3));
	}
	
	//Serialize to send out
	public String serialized() {
		return sender.toString() + "," + type + "," + content + "," + recipient;
	}
}


/*
 * A ConnHandler is spawned to respond to an
 * incoming message and deal with the contents
 */
class ConnHandler extends Thread {
	 private Datastore state;
	 private final Socket socketToHandle;

	 //Store current app state and socket that we're gonna communicate on
	 public ConnHandler(Datastore state, Socket socketToHandle) {
		 this.state = state;
		 this.socketToHandle = socketToHandle;
	 }
	 
	 public void run() {
		 try {
			 
			 /* Receive Message */
	         DataInputStream in = new DataInputStream(socketToHandle.getInputStream());
	         String raw = in.readUTF();	         
	         
	         /* Process Message */
	         Message message = new Message(raw);
	         
	         
        	 /*
        	  * Respond with Peer List and relay REGISTER message to everyone else
        	  */
	         if(message.type.equals("RELAY_REGISTER")) {
	        	 
	        	 //Respond with peer list
	        	 String responseStr = "";
	        	 
	        	 responseStr += state.self.toString() + ",";
	        	 synchronized(state.peerList){
	        		 for(Node peer : state.peerList) responseStr += peer.toString() + ",";
	        	 }
		        	 
	        	 responseStr = responseStr.substring(0, responseStr.length()-1);
	        	 DataOutputStream out = new DataOutputStream(socketToHandle.getOutputStream());
		         out.writeUTF(responseStr);

		         //Relay the REGISTER message to all known peers
		         Message toSend = new Message(state.self,"REGISTER",message.sender.toString(),state.self);
		         state.sendQueue.offer(toSend);
		         
		         //Tell the user
		         System.out.println("*** "+message.sender.toString()+" joined the swarm ***");
	         }
	         
        	 /*
        	  * Register another peer with myself
        	  */
	         else if(message.type.equals("REGISTER")) {
	        	 

	        	 Node registeringNode = new Node(message.content);
	        	 synchronized(state.peerList) {
	        		 state.peerList.add(registeringNode);
	        	 }
	        	 ChatUtils.reloadGuiPeerList(state);
	        	 
	        	 DataOutputStream out = new DataOutputStream(socketToHandle.getOutputStream());
		         out.writeUTF("REGISTER_ACK");
		         
		         //Console Output
		         System.out.println("*** "+registeringNode.toString()+" joined the swarm ***");
		         
	         }
	         
	         /*
	          * Respond to a PING message - used when connecting with the swarm
	          */
	         else if(message.type.equals("PING")) {
	        	 
	        	 //Respond with PONG
	        	 DataOutputStream out = new DataOutputStream(socketToHandle.getOutputStream());
		         out.writeUTF("PONG");
		         
	         }
	         
	         /*
	          * Receive a public message
	          */
	         else if(message.type.equals("PUBLIC_MESSAGE")) {
	        	 
	        	 //Print and acknowledge
	        	 System.out.println("<"+message.sender.port+" - PUBLIC> "+message.content);
	        	 DataOutputStream out = new DataOutputStream(socketToHandle.getOutputStream());
		         out.writeUTF("PUBLIC_MESSAGE_ACK");
	        	          
	         }
	         
        	 /*
        	  * Receive a private message
        	  */
	         else if(message.type.equals("PRIVATE_MESSAGE")) {
	        	 
	        	 //Print and acknowledge
	        	 System.out.println("<"+message.sender.port+" - PRIVATE> "+message.content);
	        	 DataOutputStream out = new DataOutputStream(socketToHandle.getOutputStream());
		         out.writeUTF("PRIVATE_MESSAGE_ACK");
		         
	         }
	         
	         //Close and return
	         socketToHandle.close();
	         return;
        
		 } catch(IOException e) {
            e.printStackTrace();
            return;
        }
	 }
}


/*
 * A blocking thread that listens for incoming requests and spawns ConnHandlers
 */
class Listener extends Thread {
	
    private Datastore state;
    private ServerSocket serverSocket;
    
    public Listener(Datastore state, int port) throws IOException {
        this.state = state;
        serverSocket = new ServerSocket(port);
        serverSocket.setReuseAddress(true);
        state.self = new Node("127.0.0.1",serverSocket.getLocalPort());
    }

    public void run() {
        	
    		//Tell user we've started
        	System.out.println("*** Running on PORT: " + serverSocket.getLocalPort() + " ***");
        	
        	while (true) {
        		try {
        			Socket clientSocket = null;
        			clientSocket = serverSocket.accept();
        			
        			//delegate to new thread
        			new Thread(new ConnHandler(state,clientSocket)).start();
        			
        			//Go back to listening
                   
                } catch(IOException e) {
                   e.printStackTrace();
                }
        	}
	}
}

/*
 * Pulls messages off the sendQueue and sends them out, also deals with the response
 */
class Sender extends Thread {
    private Datastore state;
    
    public Sender(Datastore state) {
        this.state = state;
    }
    
    //Send a message and return response or Null if there was an error
    public String sendRawMessage(Message command) {
    	String response = "";
    	Socket client = null;
		try {
			client = new Socket(command.recipient.ip, command.recipient.port);

			DataOutputStream out = new DataOutputStream(client.getOutputStream());
			out.writeUTF(command.serialized());
         
			DataInputStream in = new DataInputStream(client.getInputStream());
			response = in.readUTF();
         
		} catch (UnknownHostException e) {
			return "NULL";
		} catch (IOException e) {
			System.out.println("*** "+command.recipient.toString()+" has left the swarm ***");
			return "NULL";
		} finally {
			try {
				client.close();
			} catch (IOException e) { return "NULL";}
			catch (NullPointerException e) { return "NULL";}
		}
		
        return response;
    }
    
    //Called to start the thread
    public void run() {
    	while (true) {
   			 
   			 //Block until we have a message to send
   			 Message command = null;
   			 try {
				command = state.sendQueue.take();
   			 } catch (InterruptedException e1) {e1.printStackTrace();}
			
   			 
   			 try {
	   			 //send REGISTER message
	   			 if(command.type.equals("REGISTER")) {
	   				 
	   				synchronized(state.peerList) {
	   	        		for(Node peer : state.peerList) {
	   	        			command.recipient = peer;
	   	        			sendRawMessage(command);
	   	        		}
	   	        	 }
	   				
			         //Add to ourself
			         synchronized(state.peerList){
		        		 state.peerList.add(new Node(command.content));
		        	 }
			         ChatUtils.reloadGuiPeerList(state);
			         
	   			 }
	   			 //Send RELAY_REGISTER message - ONLY USED TO JOIN SWARM
	   	         else if(command.type.equals("RELAY_REGISTER")) {
	   	        	
	   	        	 //add returned list to out peer list
	   	        	List<String> recievedPeerList = ChatUtils.readRawMessage(sendRawMessage(command));
	   	        	synchronized(state.peerList) {
	   	        		for(String peerAsString : recievedPeerList) {
	   	        			state.peerList.add(new Node(peerAsString));
	   	        		}
	   	        	}
	   	        	ChatUtils.reloadGuiPeerList(state);
	   	        	
	   	        	System.out.println("*** Successfully Connected To Swarm ***");
	   	        	System.out.println("___________________________________________________");
	   	        	  
	   	         }
	   			 //Send PUBLIC_MESSAGE chat
	   	         else if(command.type.equals("PUBLIC_MESSAGE")) {
	   	        	
	   	        	 System.out.println("<ME - PUBLIC> "+command.content);
	   	        	 synchronized(state.peerList) {
	   	        		for(Node peer : state.peerList) {
	   	        			command.recipient = peer;
	   	        			if(sendRawMessage(command) == "NULL") {
	   	        				state.peerList.remove(peer);
	   	        				ChatUtils.reloadGuiPeerList(state);
	   	        			}
	   	        		}
	   	        	 }
	   	        	 
	   	         }
	   			 //Send PRIVATE_MESSAGE chat
	   	         else if(command.type.equals("PRIVATE_MESSAGE")) {
		   	        
	   	        	 System.out.println("<ME - PRIVATE> "+command.content);
	   	        	 if(sendRawMessage(command) == "NULL") {
	   	        		state.peerList.remove(command.recipient);
	   	        		ChatUtils.reloadGuiPeerList(state);
	   	        	 }
	   	         }
	   			 
	   			 } catch (NumberFormatException e) {
							e.printStackTrace();
	   			 }
    	}
	}
}


/*
 * Stores global data that both threads need to access
 * All writes and reads are synchronized and thread safe
 */
class Datastore {
	
	//List of peers currently Connected to
	static List<Node> peerList = Collections.synchronizedList(new ArrayList<Node>()); 
	
	//Queue to send out messages
	static BlockingQueue<Message> sendQueue = new LinkedBlockingQueue<Message>();
	
	//Gets initialized in Configurator
	static Node self;
	
	//Gets initialized in GUI thread
	static JComboBox guiPeerList;
	
}


/*
 * Contains common methods that are used almost everywhere
 */
class ChatUtils {
	
	//Return comma separated string as a list of Strings
	public static List<String> readRawMessage(String rawMessage) {
		List<String> items = new ArrayList<String>();
		Collections.addAll(items, rawMessage.split(","));
		return items;
	}
	
	//Reloads the GUI peer list combo box with current peers
	public static void reloadGuiPeerList(Datastore state) {
		state.guiPeerList.removeAllItems();
		state.guiPeerList.addItem("ALL");
		synchronized(state.peerList) {
			for(Node n : state.peerList) state.guiPeerList.addItem(n.toString());
		}
	}
	
	//Checks if we can open a socket to the Peer
	public static boolean isReachable(Datastore state, String connectionString) {
    	String response = "";
    	Socket client = null;
    	Node testNode = new Node(connectionString);
    	
    	//Basically sends a PING message to other Peer and sees if it responded with a PONG message
		try {
			client = new Socket(testNode.ip,testNode.port);

			DataOutputStream out = new DataOutputStream(client.getOutputStream());
			out.writeUTF(new Message(state.self.toString()+",PING,"+state.self.toString()+","+testNode.toString()).serialized());
         
			DataInputStream in = new DataInputStream(client.getInputStream());
			response = in.readUTF();
			
			if(response.equals("PONG")) {
				try { client.close(); } catch (IOException ee) {} catch (NullPointerException ee) {}
				return true;
			}
			else {
				try { client.close(); } catch (IOException ee) {} catch (NullPointerException ee) {}
				return false;
			} 
         
		} catch (UnknownHostException e) {
			try { client.close(); } catch (IOException ee) {} catch (NullPointerException ee) {}
			return false;
		} catch (IOException e) {
			try { client.close(); } catch (IOException ee) {} catch (NullPointerException ee) {}
			return false;
		} 
		
    }
	
	
	
}

class Config {
	public Config(Datastore state) throws IOException, InterruptedException {
		
		//Port Choosing stuff
		Object[] options = {"Manually choose a port",
                "Automatically choose a port"};
		int choice = JOptionPane.showOptionDialog(null,
			"How would you like to choose a port?",
			"Config",
			JOptionPane.YES_NO_CANCEL_OPTION,
			JOptionPane.PLAIN_MESSAGE,
			null,
			options,
			options[1]);
		
		String port = "0";
		if(choice == 0) {
			port = JOptionPane.showInputDialog("Enter Port to Listen on:");
		}
		
        //Create Listener and Sender
    	Listener l = new Listener(state,Integer.parseInt(port));
    	Sender s = new Sender(state);
    	l.start();
    	s.start();
    	
        //Code to connect auto-magically to default peer or Ask for another Peer
		//Create a swarm or Join a swarm
		options[1] = "Join a Swarm";
        options[0] = "Create a Swarm";
		choice = JOptionPane.showOptionDialog(null,
				"Please Choose an Option",
				"Config",
				JOptionPane.YES_NO_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE,
				null,
				options,
				options[1]);
		
		//Connect to swarm if they want
    	if(choice == 0) {
    		System.out.println("*** New Swarm Started ***");
    	}
    	else if(choice == 1) {
    		String bootstrapPeer = "127.0.0.1/5000";
    		if(!ChatUtils.isReachable(state,bootstrapPeer)) {
    			
    			while(!ChatUtils.isReachable(state,bootstrapPeer)) {
	    	    	bootstrapPeer =  JOptionPane.showInputDialog("Could Not Find default peer. Please enter connection string of another peer.");
    			}
    			System.out.println("*** Found custom peer! :) ***");
    			System.out.println("*** Please Wait. Connecting to swarm... ***");
    			state.sendQueue.offer(new Message(state.self.toString()+",RELAY_REGISTER,"+state.self.toString()+","+bootstrapPeer));
    		
    		} else {
    			System.out.println("*** Found default peer! :) ***");
    			System.out.println("*** Please Wait. Connecting to swarm... ***");
    			state.sendQueue.offer(new Message(state.self.toString()+",RELAY_REGISTER,"+state.self.toString()+","+bootstrapPeer));

    		}
    	}
    	//We are now connected and ready to roll.
        
        

	}
}

