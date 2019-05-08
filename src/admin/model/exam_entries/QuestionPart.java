package admin.model.exam_entries;

import admin.model.Examinee;
import admin.model.Model;

/**
 * @author Ziya Mukhtarov
 * @version 08/05/2019
 */
public class QuestionPart extends Entry
{
	private static final long serialVersionUID = -7659809221309413824L;
	
	private int maxPoints;

	/**
	 * Creates a question part
	 * @param title The title of this part
	 * @param content The content
	 * @param maxPoints
	 */
	public QuestionPart( String title, int maxPoints)
	{
		super( title);
		this.maxPoints = maxPoints;
	}
	
	@Override
	/**
	 */
	public void send( Examinee e, Model m)
	{
		m.sendMessage( "part", id + Model.MESSAGE_SEPERATOR + title + Model.MESSAGE_SEPERATOR + content, e);
		sendAll(e, m);
	}

	/**
	 * @return The maxPoints
	 */
	public int getMaxPoints()
	{
		return maxPoints;
	}

	/**
	 * @param maxPoints The maxPoints to set
	 */
	public void setMaxPoints( int maxPoints)
	{
		this.maxPoints = maxPoints;
	}
}