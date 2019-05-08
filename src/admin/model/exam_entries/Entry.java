package admin.model.exam_entries;

import admin.model.Examinee;
import admin.model.IDHandler;
import admin.model.Model;

/**
 * This class is for the Entry of an Exam.
 * @author Adeem Adil Khatri<br>
 *         Ziya Mukhtarov
 * @version 07/05/2019
 */
public class Entry extends Container
{
	// Constants
	private static final long serialVersionUID = 6649922102661498183L;
	
	// Properties
	protected String title;
	protected String id;
	protected String content;

	// Constructors
	/**
	 * Constructs a basic new Entry with provided values
	 */
	public Entry( String title)
	{
		this.title = title;
		content = "";
		id = IDHandler.getInstance().generate( this);
	}

	// methods
	/**
	 * This method sends the data of this Entry to Examinee from the specified
	 * Model. This method should be overridden if this entry should be sendable.
	 * Otherwise, this method does not do anything.
	 * @param e The Examinee to which the data is to be send
	 * @param m The model from which the data is to be send
	 */
	public void send( Examinee e, Model m)
	{
	}

	/**
	 * @return The title
	 */
	public String getTitle()
	{
		return title;
	}

	/**
	 * @param title - The title to set
	 */
	public void setTitle( String title)
	{
		this.title = title;
	}

	/**
	 * @return The content
	 */
	public String getContent()
	{
		return content;
	}

	/**
	 * @param content - The content to set
	 */
	public void setContent( String content)
	{
		this.content = content;
	}

	/**
	 * @return The id
	 */
	public String getId()
	{
		return id;
	}
}