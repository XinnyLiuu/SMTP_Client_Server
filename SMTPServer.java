import java.net.*;
import java.io.*;
import java.util.*;

/**
 * SMTP Server
 * Server for ISTE 121 final project, connects to a client, then accepts commands from client to process them accordingly
 * @author Brandon Mok + Xin Liu + Brantley Wyche
 * @version 11/7/18
 */
public class SMTPServer implements ClientServerConstants {
	// Sockets
	private ServerSocket sSocket = null;

	// I/O
	private Scanner scn = null;
	private PrintWriter pwt = null;

	// Object streams
	private ObjectInputStream ois = null;
	private ObjectOutputStream oos = null;
	public static final String MSG_FILE = "mailbox.obj";

	// Queue for queueing messages
	Queue<String> msgQueue = new LinkedList<>();

	// HashMap for storing messages with user
	HashMap<String, ArrayList<String>> msgStore = null;

	/**
	 * Main
	 */
	public static void main(String[] args) {
		// Start socket
		new SMTPServer().doStart();
	}

	/**
	 * ServerStart
	 *
	 * Thread that will be instantiated on doStart(), this class will start a server at indicated socket and port
	 */
	class ServerStart extends Thread {
		// Attributes
		private Socket cSocket = null;

		// Run
		public void run() {
			// Start server at SERVER_PORT
			try {
				sSocket = new ServerSocket(SERVER_PORT);
				System.out.println("Server started at " + SERVER_PORT);
			}
			catch(IOException ioe) { ioe.printStackTrace(); }

			// Always listen for connections to server
			while(true) {
				// Accept connections to server
				try {
					cSocket = sSocket.accept();
					System.out.println("Connection established!");
				}
				catch(IOException ioe) {
					ioe.printStackTrace();
					return;
				}

				// Start thread for ClientConnection
				ClientConnection ct = new ClientConnection(cSocket);
				ct.start();
			}
		}
	}

	/**
	 * ClientConnection
	 *
	 * Thread that will be instantiated while the client connects with the server socket. This class will handle handle the interaction between client and server
	 */
	class ClientConnection extends Thread {
		// Attributes
		private Socket cSocket = null;

		// Constructor
		public ClientConnection(Socket _cSocket) {
			this.cSocket = _cSocket;
		}

		// Run
		public void run() {
			// Prepare I/O
			try {
				scn = new Scanner(new InputStreamReader( cSocket.getInputStream() ));
				pwt = new PrintWriter(new OutputStreamWriter( cSocket.getOutputStream() ));
			}
			catch(IOException ioe) { ioe.printStackTrace(); }

			// Listen for commands
			while(scn.hasNextLine()) {
				// Get command
				String cmd = scn.nextLine();

				// HELO
				if(cmd.equals("HELO")) {
					// Send status code
					pwt.println("250 - HELO - OK");
					System.out.println("250 - HELO - OK");
					pwt.flush();
				}
				// MAIL FROM
				else if(cmd.startsWith("MAIL FROM:")) {
					// Get the username
					String username = cmd.substring(10);

					// Send status code
					pwt.println("250 - MAIL FROM - OK");
					System.out.println("250 - MAIL FROM - OK");
					pwt.flush();

					// RCPT TO
					String cmd2 = scn.nextLine();
					if(cmd2.startsWith("RCPT TO:")) {
						// Get the username
						String username2 = cmd2.substring(8);

						// Send status code
						pwt.println("250 - RCPT TO - OK");
						System.out.println("250 - RCPT TO - OK");
						pwt.flush();

						// DATA
						String cmd3 = scn.nextLine();
						if(cmd3.equals("DATA")) {
							// Send status code
							pwt.println("250 - DATA - OK");
							System.out.println("250 - DATA - OK");
							pwt.flush();

							// The client will send the message next
							String message = scn.nextLine();

							// Add From: into the message
							String fullMessage = "Mail From: " + username + "\n" + message + "\n";

							// Encrypt message
							String encryptedMsg = doEncrypt(fullMessage);

							System.out.println("Encrypted Msg" + encryptedMsg);

							// Prepare schema for encrypted msg
							String finalMsg = EMAIL_START + encryptedMsg + EMAIL_END;

							// Add to queue
							msgQueue.add(finalMsg);

							// Start MailThread
							MailThread mt = new MailThread(username2, msgQueue.peek());
							mt.start();

							// Send status code
							pwt.println("250 - Message Queued - OK");
							System.out.println("250 - Message Queued - OK");
							pwt.flush();
						}
					}
				}
				else if(cmd.startsWith("RETRIEVE FROM:")){
					// Get username
					String user = cmd.substring(14);

					// retrieve messages for this user
					doRetrieve(user);
				}
				else if(cmd.equals("QUIT")) {
					// Send response;
					pwt.println("221 - QUIT - OK"); 
					pwt.flush();

					// Stop streams, write HashMap
					doStop();

					// Kill program
					System.exit(0);
				}
			}// end of while
		}
	}

	/**
	 * MailThread
	 *
	 * Thread that will read from the queue and store the message with its corresponding user in a file
	 *
	 * There will a list of users authorized to recieve messages on this server.
	 */
	class MailThread extends Thread {
		// Attributes
		String username = "";
		String message = "";

		// Constructor
		public MailThread(String _username, String _message) {
			this.username = _username;
			this.message = _message;
		}

		// Run
		public void run() {
			// Check if username exists in map first
			if(msgStore.containsKey(username)) {
				System.out.println("User mailbox found. Adding messages...");

				// Get user's current messages
				ArrayList<String> userMessages =  msgStore.get(username);

				// Add message to userMessages
				userMessages.add(message);

				// Update map
				msgStore.put(username, userMessages);
			}
			// If username does not exist
			else {
				System.out.println("User mailbox not created. Creating mailbox...");

				// Create Arraylist
				ArrayList<String> mailArr = new ArrayList<>();

				// Add to mailArr
				mailArr.add(message);

				// Add to map
				msgStore.put(username, mailArr);

				// Save HashMap
				doSave(); 
			}
		}
	}


	/**
	 * Starts sserver thread and socket to listen for connections
	 */
	private void doStart() {
		// Look for map object file first before starting
		try{
			File msgFile = new File(MSG_FILE);
			ois = new ObjectInputStream(new FileInputStream(msgFile));

			Object o = ois.readObject();

			if(o instanceof HashMap) {
				msgStore = (HashMap)o; // cast to hashmap

				for(String s : msgStore.keySet()) {
					System.out.println(s);
				}

				System.out.println("HashMap found!");
			}
		}
		catch(FileNotFoundException fnfe){
			fnfe.printStackTrace();

			System.out.println("Previous HashMap not found...");
			System.out.println("Creating new HashMap...");
			msgStore = new HashMap<>();
		}
		catch(ClassNotFoundException cnfe) { cnfe.printStackTrace();}
		catch(IOException ioe){ ioe.printStackTrace(); }

		// Starts ServerStart thread
		new ServerStart().start();
	}

	/**
	 * Closes server thread and socket to prevent further connections
	 */
	private void doStop() {
		// Save HashMap
		doSave();
		
		// Closes sSocket and streams
		try {
			// Close stream
			sSocket.close();
			scn.close();
			pwt.close();
		}
		catch(IOException ioe) { ioe.printStackTrace(); }
	}

	/**
	 * On command "RETRIEVE FROM USER", the server will look for users that have the USER combination and return all their messages
	 */
	private void doRetrieve(String _user) {
		// Get username
		String user = _user;

		// Check if msgStore contains user
		if(msgStore.containsKey(user)) {
			ArrayList<String> allMail = msgStore.get(user);
			String mail = "";
	
			// Interate through list
			for(String m : allMail) {
				mail += m + "\n";
			}
	
			// Send Mail to user
			pwt.println(mail);
			System.out.println("250 - RETRIEVE FROM - OK"); 
			pwt.flush();
		}
		else {
			// Send error
			pwt.println("221 - RETRIEVE FROM - FAIL");
			System.out.println("221 - RETRIEVE FROM - FAIL"); 
			pwt.flush();
		}

	}// end of doRetrieve

	/**
	 * Encrypts message received from client with Ceasar Cipher of Shift 13
	 */
	private String doEncrypt(String message) {
      String result = "";

      for(int i = 0; i < message.length(); i++){
         int characterPos = LETTERS.indexOf(message.charAt(i));
         int keyValue = (SHIFT + characterPos) % 26;
         char replaceVal = LETTERS.charAt(keyValue);
         result += replaceVal;
	  }

	  return result;
	}// end of doEncrypt

	/** 
	 * Saves HashMap to userMSGS.obj file
	 */
	private void doSave() {
		// Save HashMap
		try {
			File file = new File(MSG_FILE);
			oos = new ObjectOutputStream(new FileOutputStream(file)); // objectoutputstream

			// writes the hashmap into separate msg file
			oos.writeObject(msgStore);
			oos.flush();
			System.out.println("Saving HashMap ...");
		}
		catch(FileNotFoundException fnfe) { fnfe.printStackTrace(); }
		catch(IOException ioe) { ioe.printStackTrace(); }
	}
}
