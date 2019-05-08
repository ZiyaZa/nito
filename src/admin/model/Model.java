package admin.model;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.net.DatagramPacket;
import java.net.Socket;
import java.util.ArrayList;

import admin.NitoAdminView;
import admin.model.exam_entries.Container;
import admin.model.exam_entries.Entry;
import admin.model.exam_entries.Exam;
import admin.model.exam_entries.Instruction;
import admin.model.exam_entries.Question;
import admin.model.exam_entries.QuestionPart;
import admin.model.exam_entries.Template;
import common.network.Screenshot;
import common.network.Server;

/**
 * The main model class for admin interface
 * @author Ziya Mukhtarov
 * @version 08/05/2019
 */
public class Model implements Serializable
{
	private static final long serialVersionUID = -1363093326221065966L;
	private static final String secret = "24DdwVJljT28m6MSOfvMnj7iZbL8bNMmo7xnLKsZSyurflOLg2JFtq0hsY09";

	public static final String MESSAGE_SEPERATOR = ":::";
	public static final int STATUS_PREPARATION = 1;
	public static final int STATUS_EXAM_MODE = 2;
	public static final int STATUS_GRADING = 3;

	private static Model instance;

	private transient int status;
	private transient ArrayList<NitoAdminView> views;
	private transient Examinees examinees;
	private transient Server server;
	private Container entries;
	private Exam lastExam;
	private transient Exam currentExam;
	private transient Thread examEndCheckerThread;

	/**
	 * Creates new Model for Nito admin interface
	 */
	private Model()
	{
		initialize();
		entries = new Container();
		lastExam = null;
	}

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
		status = STATUS_PREPARATION;
		views = new ArrayList<>();
		examinees = new Examinees();
		currentExam = null;
		examEndCheckerThread = null;
		instance = this;
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
	public Question createQuestion( Entry parent, String title, int maxPoints)
	{
		Question q = new Question( title, maxPoints);
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
		Instruction i = new Instruction( "Exam instructions");
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
	 * @return the entries
	 */
	public Container getEntries() {
		return entries;
	}

	/**************************** MONITORING *****************************/
	/**
	 * Start the specified exam
	 * @param e the exam to start TODO
	 */
	public void startExam( Exam exam)
	{
		currentExam = exam;
		lastExam = exam;
		try
		{
			server = new ServerForExam();
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		exam.start();
		examEndCheckerThread = new Thread( new Runnable() {

			@Override
			public void run()
			{
				while ( exam.getTimeLeft() > 0)
				{
					try
					{
						Thread.sleep( 1000);
					}
					catch (InterruptedException e)
					{
						e.printStackTrace();
						Thread.currentThread().interrupt();
					}
				}
				endCurrentExam();
			}
		});
		examEndCheckerThread.start();
		status = STATUS_EXAM_MODE;
	}

	public void endCurrentExam()
	{
		if ( currentExam == null)
		{
			return;
		}
		sendMessage( "exam_ended", "");
		examEndCheckerThread.interrupt();
		currentExam = null;
	}

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
				break;
			case "solution":
				solutionReceived( parts[3], parts[2], from);
				break;
			default: // TODO
		}
	}

	private void solutionReceived( String content, String id, Examinee from)
	{
		Object object = IDHandler.getInstance().getByID( id);
		if ( !(object instanceof QuestionPart))
		{
			// Not a solution to some question part. Ignore
			return;
		}

		QuestionPart part = (QuestionPart) object;
		// TODO
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
		updateViews();
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
	 * @return The examinees
	 */
	public Examinees getExaminees()
	{
		return examinees;
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
			// TODO Auto-generated method stub
		}

		@Override
		public void messageReceived( String msg, Socket socket)
		{
			handleMessage( msg, socket);
		}

		@Override
		public void screenshotReceived( Screenshot img, DatagramPacket packet)
		{
			Examinee e = examinees.getByIP( packet.getAddress().getHostAddress());
			if ( e != null)
			{
				e.setScreen( img);
			}
			else
			{
				System.err.println( "Received screenshot, but examinee not initialized! From IP: " + packet.getAddress().getHostAddress());
			}
		}
	}

	/****************************** GRADING ******************************/
	// TODO

	/****************************** GENERAL ******************************/
	/**
	 * Adds a view to this model and immediately calls updateView method. The
	 * updateView method of this view will be automatically called whenever it is
	 * necessary
	 * @param view The view to add to this model
	 */
	public void addView( NitoAdminView view)
	{
		views.add( view);
		view.updateView( this);
	}

	/**
	 * Calls the update view method of all views added to this model
	 */
	private void updateViews()
	{
		// TODO Remember to add this to everywhere!
		Model ref = this;
		for ( NitoAdminView view : views)
		{
			new Thread( new Runnable() {
				@Override
				public void run()
				{
					view.updateView( ref);
				}
			}).start();
		}
	}

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
