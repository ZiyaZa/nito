package admin.model;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.net.DatagramPacket;
import java.net.Socket;

import admin.model.exam_entries.Container;
import admin.model.exam_entries.Entry;
import admin.model.exam_entries.Exam;
import admin.model.exam_entries.Instruction;
import admin.model.exam_entries.Question;
import admin.model.exam_entries.QuestionPart;
import admin.model.exam_entries.Template;
import common.network.Screenshot;
import common.network.Server;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.util.Pair;

/**
 * The main model class for admin interface
 * @author Ziya Mukhtarov
 * @version 14/05/2019
 */
public class Model implements Serializable
{
	private static final long serialVersionUID = -1363093326221065966L;
	private static final String secret = "24DdwVJljT28m6MSOfvMnj7iZbL8bNMmo7xnLKsZSyurflOLg2JFtq0hsY09";

	public static final String MESSAGE_SEPERATOR = ":::";

	private static Model instance;

	private transient int status;
	private Examinees examinees;
	private transient Server server;
	private Container entries;
	private Exam lastExam;
	private transient Exam currentExam;
	private transient Thread examEndCheckerThread;
	private transient Thread timeSynchronizerThread;
	private transient ObservableList<Pair<String, Integer>> logs;
	private transient int lastSetWidth = -1;
	private transient boolean isConnectionBlocked = false;

	/**
	 * Creates new Model for Nito admin interface
	 */
	private Model()
	{
		initialize();
		entries = new Container();
		lastExam = null;
	}

	/**
	 * @return the instance of the Nito Admin interface model
	 */
	public static Model getInstance()
	{
		if ( instance == null)
		{
			new Model();
		}
		return instance;
	}

	/**
	 * Initializes the Model. Can be called from constructor or after
	 * deserialization
	 */
	private void initialize()
	{
		examinees = new Examinees();
		currentExam = null;
		examEndCheckerThread = null;
		instance = this;
		logs = FXCollections.observableArrayList();
	}

	/**************************** PREPARATION ****************************/
	/**
	 * Sets the workspace folder for loading previous data and saving new data
	 * @param folder The workspace folder pathname
	 */
	public void setWorkspaceFolder( File folder)
	{
		Workspace.getInstance().set( folder);
	}

	/**
	 * Creates new exam
	 * @param title  Title of the exam
	 * @param length Length of the exam
	 * @return The reference to the created exam
	 */
	public Exam createExam( String title, int length)
	{
		Exam e = new Exam( title, length);
		entries.add( e);
		return e;
	}

	/**
	 * Creates new question
	 * @param parent    The parent entry
	 * @param title     Title of the question
	 * @param maxPoints The maximum amount of points available for this question
	 * @return The reference to the created question
	 */
	public Question createQuestion( Entry parent, String title)
	{
		Question q = new Question( title);
		parent.add( q);
		return q;
	}

	/**
	 * Creates new question part
	 * @param parent    The parent entry
	 * @param title     Title of the question part
	 * @param maxPoints The maximum amount of points available for this question
	 *                  part
	 * @return The reference to the created question part
	 */
	public QuestionPart createQuestionPart( Entry parent, String title, int maxPoints)
	{
		QuestionPart qp = new QuestionPart( title, maxPoints);
		parent.add( qp);
		return qp;
	}

	/**
	 * Creates new question part
	 * @param parent The parent entry
	 * @return The reference to the created template
	 */
	public Template createTemplate( Entry parent)
	{
		Template t = new Template( "Template of " + parent.getTitle());
		parent.add( t);
		return t;
	}

	/**
	 * Creates new exam instruction
	 * @param parent The parent entry
	 * @return The reference to the created instruction
	 */
	public Instruction createInstruction( Entry parent)
	{
		Instruction i = new Instruction();
		parent.add( i);
		return i;
	}

	/**
	 * Sets the content of the specified entry
	 * @param e       The entry to set the content of
	 * @param content The new content
	 */
	public void setContentOfEntry( Entry e, String content)
	{
		e.setContent( content);
	}

	/**
	 * Deletes the entry
	 * @param e The entry to delete
	 */
	public void deleteEntry( Entry e)
	{
		e.getParent().remove( e);
	}

	/**
	 * @return the entries
	 */
	public Container getEntries()
	{
		return entries;
	}

	/**************************** MONITORING *****************************/
	/**
	 * Start the specified exam
	 * @param exam the exam to start
	 */
	public void startSendingExam( Exam exam)
	{
		isConnectionBlocked = false;

		currentExam = exam;
		lastExam = exam;

		if ( server != null)
		{
			server.close();
		}
		try
		{
			server = new ServerForExam();
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void startExam()
	{
		if ( currentExam == null)
			return;

		sendMessage( "start", "");
		currentExam.start();
		examEndCheckerThread = new Thread( () -> {
			while ( currentExam.getTimeLeft() > 0)
			{
				try
				{
					Thread.sleep( 1000);
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
					break;
				}
			}
			endCurrentExam();
		});
		examEndCheckerThread.start();

		timeSynchronizerThread = new Thread( () -> {
			while ( true)
			{
				try
				{
					Thread.sleep( 10000);
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
					break;
				}
				sendMessage( "time_remain", currentExam.getTimeLeft() + "");
			}
		});
		timeSynchronizerThread.start();
	}

	public void setScreenshotWidth( int width)
	{
		lastSetWidth = width;
		sendMessage( "screenshot_width", width + "");
	}

	/**
	 * Ends the current exam and forces examinees to submit their solutions
	 */
	public void endCurrentExam()
	{
		if ( currentExam == null)
		{
			return;
		}
		sendMessage( "exam_ended", "");
		examEndCheckerThread.interrupt();
		timeSynchronizerThread.interrupt();

		// TODO NullPointer?
		currentExam.stop();
		currentExam = null;
	}

	/**
	 * @return The lastExam
	 */
	public Exam getLastExam()
	{
		return lastExam;
	}

	/**
	 * Blocks future examinee connections
	 */
	public void blockConnection()
	{
		isConnectionBlocked = true;
		server.stopListeningForConnections();
	}

	/**
	 * Handles the message coming from to the server
	 * @param msg    The message content
	 * @param socket The socket from which this message came from
	 */
	private void handleMessage( String msg, Socket socket)
	{
		String[] parts = msg.split( ":::");
		Examinee from = examinees.getByIP( socket.getInetAddress().getHostAddress());

		if ( !parts[0].equals( secret))
		{
			// Not from Nito. Ignore
			return;
		}

		switch (parts[1])
		{
			case "name":
				// Connection request
				from = createExaminee( parts[2], socket);
				currentExam.send( from, this);
				if ( lastSetWidth != -1)
					setScreenshotWidth( lastSetWidth);
				log( from.getName() + " is connected");
				break;
			case "solution":
				solutionReceived( (parts.length >= 4 ? parts[3] : ""), parts[2], from);
				break;
		}
	}

	/**
	 * Handles the solution submitted by the examinee
	 * @param content The content of the solution
	 * @param id      The id of the question part
	 * @param from    The examinee who submitted this
	 */
	private void solutionReceived( String content, String id, Examinee from)
	{
		Object object = IDHandler.getInstance().getByID( id);
		System.out.println( "DEBUG: " + object);
		if ( !(object instanceof QuestionPart))
		{
			// Not a solution to some question part. Ignore
			return;
		}

		System.out.println( "DEBUG: " + content + " " + from);

		QuestionPart part = (QuestionPart) object;
		from.addSolution( part, content);
	}

	/**
	 * Creates new examinee with the specified name
	 * @param name   The name of the examinee
	 * @param socket The socket to this examinee
	 * @return The reference to created Examinee
	 */
	private Examinee createExaminee( String name, Socket socket)
	{
		Examinee e = examinees.newExaminee( name, socket);
		return e;
	}

	/**
	 * Sends the message to the specified examinee. The message is in this format:
	 * {@code type + } {@link #MESSAGE_SEPERATOR} {@code + msg}
	 * @param type     The type of the message
	 * @param msg      The message to send
	 * @param examinee The examinee to send the message to
	 */
	public void sendMessage( String type, String msg, Examinee examinee)
	{
		server.sendMessage( secret + MESSAGE_SEPERATOR + type + MESSAGE_SEPERATOR + msg, examinee.getSocket().getInetAddress());
	}

	/**
	 * Sends the message to all examinees. The message is in this format:
	 * {@code type + } {@link #MESSAGE_SEPERATOR} {@code + msg}
	 * @param type The type of the message
	 * @param msg  The message to send
	 */
	public void sendMessage( String type, String msg)
	{
		server.sendMessageToAll( secret + MESSAGE_SEPERATOR + type + MESSAGE_SEPERATOR + msg);
	}

	/**
	 * Logs the message and the current elapsed time from the start of the exam
	 * @param message The message to log
	 */
	public void log( String message)
	{
		logs.add( new Pair<String, Integer>( message, lastExam.getTimeElapsed()));
	}

	/**
	 * @return The examinees
	 */
	public Examinees getExaminees()
	{
		return examinees;
	}

	public boolean isConnectionBlocked()
	{
		return isConnectionBlocked;
	}

	/**
	 * @return The logs
	 */
	public ObservableList<Pair<String, Integer>> getLogs()
	{
		return logs;
	}

	/**
	 * @author Ziya Mukhtarov
	 * @version 07/05/2019
	 */
	private class ServerForExam extends Server
	{
		/**
		 * Opens the TCP and UDP servers for Nito
		 * @throws IOException if an I/O error occurs when opening the socket, if the
		 *                     socket could not be opened, or the socket could not bind
		 *                     to the specified local port.
		 */
		public ServerForExam() throws IOException
		{
			super();
		}

		@Override
		public void connectionEstablished( Socket socket)
		{
		}

		@Override
		public void connectionTerminated( Socket socket)
		{
			Examinee e = examinees.getByIP( socket.getInetAddress().getHostAddress());
			if ( e != null)
			{
				log( e.getName() + " is disconnected");
				e.setStatus( Examinee.STATUS_DISCONNECTED);
			}
		}

		@Override
		public void messageReceived( String msg, Socket socket)
		{
			handleMessage( msg, socket);
		}

		@Override
		public void screenshotReceived( Screenshot img, DatagramPacket packet)
		{
			if ( packet == null || packet.getAddress() == null)
				return;
			Examinee e = examinees.getByIP( packet.getAddress().getHostAddress());
			if ( e != null)
			{
				e.setScreen( img);
			}
		}
	}

	/****************************** GENERAL ******************************/
	/**
	 * Deserialization method
	 */
	private void readObject( ObjectInputStream ois) throws Exception
	{
		ois.defaultReadObject();
		initialize();
	}

	/**
	 * @return Current status of this model
	 */
	public int getStatus()
	{
		return status;
	}
}
