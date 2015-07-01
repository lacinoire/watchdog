package nl.tudelft.watchdog.logic.interval.intervaltypes;

import java.util.Date;

import nl.tudelft.watchdog.core.logic.interval.intervaltypes.IntervalType;

import org.eclipse.ui.texteditor.ITextEditor;

/** A reading interval, i.e. an interval in which the user read some code. */
public class ReadingInterval extends EditorIntervalBase {
	/** Serial id. */
	private static final long serialVersionUID = 1L;

	/** Constructor. */
	public ReadingInterval(ITextEditor part, Date start) {
		super(part, IntervalType.READING, start);
	}

}
