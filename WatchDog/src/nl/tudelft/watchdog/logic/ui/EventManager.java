package nl.tudelft.watchdog.logic.ui;

import java.util.Date;

import nl.tudelft.watchdog.logic.InitializationManager;
import nl.tudelft.watchdog.logic.document.Document;
import nl.tudelft.watchdog.logic.document.DocumentCreator;
import nl.tudelft.watchdog.logic.interval.IntervalManager;
import nl.tudelft.watchdog.logic.interval.intervaltypes.EditorIntervalBase;
import nl.tudelft.watchdog.logic.interval.intervaltypes.IntervalBase;
import nl.tudelft.watchdog.logic.interval.intervaltypes.IntervalType;
import nl.tudelft.watchdog.logic.interval.intervaltypes.JUnitInterval;
import nl.tudelft.watchdog.logic.interval.intervaltypes.PerspectiveInterval;
import nl.tudelft.watchdog.logic.interval.intervaltypes.PerspectiveInterval.Perspective;
import nl.tudelft.watchdog.logic.interval.intervaltypes.ReadingInterval;
import nl.tudelft.watchdog.logic.interval.intervaltypes.TypingInterval;
import nl.tudelft.watchdog.logic.ui.events.EditorEvent;
import nl.tudelft.watchdog.logic.ui.events.WatchDogEvent;
import nl.tudelft.watchdog.logic.ui.events.WatchDogEvent.EventType;

import org.eclipse.ui.texteditor.ITextEditor;

/**
 * Manager for {@link EditorEvent}s. Links such events to actions in the
 * IntervalManager, i.e. manages the creation and deletion of intervals based on
 * the incoming events. This class therefore contains the logic of when and how
 * new intervals are created, and how WatchDog reacts to incoming events
 * generated by its listeners.
 */
public class EventManager {

	/** The {@link InitializationManager} this observer is working with. */
	private IntervalManager intervalManager;

	private UserInactivityNotifier userInactivityNotifier;

	private InactivityNotifier typingInactivityNotifier;

	private InactivityNotifier readingInactivityNotifier;

	/** Constructor. */
	public EventManager(final IntervalManager intervalManager,
			int userActivityTimeout) {
		this.intervalManager = intervalManager;

		// the user inactivity timer additionally calls a new user event
		userInactivityNotifier = new UserInactivityNotifier(this,
				userActivityTimeout, EventType.USER_INACTIVITY, intervalManager);
		typingInactivityNotifier = new InactivityNotifier(this,
				userActivityTimeout, EventType.TYPING_INACTIVITY);
		readingInactivityNotifier = new InactivityNotifier(this,
				userActivityTimeout, EventType.READING_INACTIVITY);
	}

	/**
	 * Simple proxy for {@link #update(WatchDogEvent, Date)}, calling it with
	 * the forcedDate set to now.
	 */
	public void update(WatchDogEvent event) {
		update(event, new Date());
	}

	/**
	 * Introduces the supplied editorEvent, and sets the forcedDate to the
	 * interval that is being created or closed.
	 */
	public void update(WatchDogEvent event, Date forcedDate) {
		IntervalBase interval;
		switch (event.getType()) {
		case START_ECLIPSE:
			intervalManager.addInterval(new IntervalBase(
					IntervalType.ECLIPSE_OPEN, forcedDate));
			userInactivityNotifier.trigger(forcedDate);
			break;

		case END_ECLIPSE:
			userInactivityNotifier.cancelTimer(forcedDate);
			intervalManager.closeAllIntervals();
			break;

		case ACTIVE_WINDOW:
			interval = intervalManager
					.getIntervalOfType(IntervalType.ECLIPSE_ACTIVE);
			if (intervalIsClosed(interval)) {
				intervalManager.addInterval(new IntervalBase(
						IntervalType.ECLIPSE_ACTIVE, forcedDate));
			}
			userInactivityNotifier.trigger(forcedDate);
			break;

		case INACTIVE_WINDOW:
			interval = intervalManager
					.getIntervalOfType(IntervalType.ECLIPSE_ACTIVE);
			intervalManager.closeInterval(interval, forcedDate);
			break;

		case START_JAVA_PERSPECTIVE:
			createNewPerspectiveInterval(Perspective.JAVA, forcedDate);
			userInactivityNotifier.trigger(forcedDate);
			break;

		case START_DEBUG_PERSPECTIVE:
			createNewPerspectiveInterval(Perspective.DEBUG, forcedDate);
			userInactivityNotifier.trigger(forcedDate);
			break;

		case START_UNKNOWN_PERSPECTIVE:
			createNewPerspectiveInterval(Perspective.OTHER, forcedDate);
			userInactivityNotifier.trigger(forcedDate);
			break;

		case JUNIT:
			JUnitInterval junitInterval = (JUnitInterval) event.getSource();
			intervalManager.addInterval(junitInterval);
			break;

		case USER_ACTIVITY:
			interval = intervalManager
					.getIntervalOfType(IntervalType.USER_ACTIVE);
			if (intervalIsClosed(interval)) {
				intervalManager.addInterval(new IntervalBase(
						IntervalType.USER_ACTIVE, forcedDate));
			}
			userInactivityNotifier.trigger(forcedDate);
			break;

		case START_EDIT:
			EditorIntervalBase editorInterval = intervalManager
					.getEditorInterval();
			ITextEditor editor = (ITextEditor) event.getSource();

			readingInactivityNotifier.cancelTimer(forcedDate);
			if (intervalIsOfType(editorInterval, IntervalType.TYPING)
					&& editorInterval.getEditor() == editor) {
				return;
			}

			intervalManager.closeInterval(editorInterval, forcedDate);

			TypingInterval typingInterval = new TypingInterval(editor,
					forcedDate);
			Document document = null;
			if (editorInterval != null && editorInterval.getEditor() == editor) {
				document = editorInterval.getDocument();
			} else {
				document = DocumentCreator.createDocument(editor);
			}
			typingInterval.setDocument(document);
			intervalManager.addInterval(typingInterval);

			typingInactivityNotifier.trigger();
			userInactivityNotifier.trigger(forcedDate);
			break;

		case EDIT:
			editorInterval = intervalManager.getEditorInterval();
			editor = (ITextEditor) event.getSource();

			if (intervalIsClosed(editorInterval)
					|| !intervalIsOfType(editorInterval, IntervalType.TYPING)
					|| editorInterval.getEditor() != editor) {
				update(new WatchDogEvent(event.getSource(),
						EventType.START_EDIT));
				break;
			}

			typingInactivityNotifier.trigger();
			userInactivityNotifier.trigger(forcedDate);
			break;

		case PAINT:
		case CARET_MOVED:
		case ACTIVE_FOCUS:
			editorInterval = intervalManager.getEditorInterval();
			editor = (ITextEditor) event.getSource();
			if (intervalIsClosed(editorInterval)
					|| editorInterval.getEditor() != editor) {
				ReadingInterval readingInterval = new ReadingInterval(editor,
						forcedDate);
				readingInterval.setDocument(DocumentCreator
						.createDocument(editor));
				intervalManager.addInterval(readingInterval);
			}

			readingInactivityNotifier.trigger();
			userInactivityNotifier.trigger(forcedDate);
			break;

		case INACTIVE_FOCUS:
			editorInterval = intervalManager.getEditorInterval();
			intervalManager.closeInterval(editorInterval, forcedDate);
			readingInactivityNotifier.cancelTimer(forcedDate);
			typingInactivityNotifier.cancelTimer(forcedDate);
			break;

		case USER_INACTIVITY:
			interval = intervalManager
					.getIntervalOfType(IntervalType.USER_ACTIVE);
			intervalManager.closeInterval(interval, forcedDate);
			typingInactivityNotifier.cancelTimer(forcedDate);
			readingInactivityNotifier.cancelTimer(forcedDate);
			break;

		case TYPING_INACTIVITY:
			editorInterval = intervalManager.getEditorInterval();
			if (intervalIsOfType(editorInterval, IntervalType.TYPING)) {
				intervalManager.closeInterval(editorInterval, forcedDate);
			}
			break;

		case READING_INACTIVITY:
			editorInterval = intervalManager.getEditorInterval();
			if (intervalIsOfType(editorInterval, IntervalType.READING)) {
				intervalManager.closeInterval(editorInterval, forcedDate);
			}
			break;

		default:
			break;
		}
	}

	private boolean intervalIsOfType(IntervalBase interval, IntervalType type) {
		return interval != null && interval.getType() == type;
	}

	private boolean intervalIsClosed(IntervalBase interval) {
		return interval == null || interval.isClosed();
	}

	/** Creates a new perspective Interval of the given type. */
	private void createNewPerspectiveInterval(
			PerspectiveInterval.Perspective perspecitveType, Date forcedDate) {
		PerspectiveInterval perspectiveInterval = intervalManager
				.getIntervalOfClass(PerspectiveInterval.class);
		if (perspectiveInterval != null
				&& perspectiveInterval.getPerspectiveType() == perspecitveType) {
			// abort if such an interval is already open.
			return;
		}
		intervalManager.closeInterval(perspectiveInterval, forcedDate);
		intervalManager.addInterval(new PerspectiveInterval(perspecitveType,
				forcedDate));
	}

}