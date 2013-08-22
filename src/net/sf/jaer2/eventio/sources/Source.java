package net.sf.jaer2.eventio.sources;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import net.sf.jaer2.util.GUISupport;
import net.sf.jaer2.util.Reflections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Source implements Serializable {
	private static final long serialVersionUID = 9065706462825717879L;

	/** Local logger for log messages. */
	protected static final Logger logger = LoggerFactory.getLogger(Source.class);

	/** Main GUI layout - Vertical Box. */
	transient private final VBox rootLayout = new VBox(0);
	/** Main GUI layout for Sub-Classes - Vertical Box. */
	transient protected final VBox rootLayoutChildren = new VBox(0);

	/** Main GUI GUI: tasks to execute when related data changes. */
	transient protected final List<Runnable> rootTasksUIRefresh = new ArrayList<>(8);

	/** Configuration GUI layout - Vertical Box. */
	transient private final VBox rootConfigLayout = new VBox(0);
	/** Configuration GUI layout for Sub-Classes - Vertical Box. */
	transient protected final VBox rootConfigLayoutChildren = new VBox(0);

	/** Configuration GUI: tasks to execute before showing the dialog. */
	transient protected final List<Runnable> rootConfigTasksDialogRefresh = new ArrayList<>(8);
	/** Configuration GUI: tasks to execute on clicking OK. */
	transient protected final List<Runnable> rootConfigTasksDialogOK = new ArrayList<>(8);

	public Source() {
		CommonConstructor();
	}

	private void CommonConstructor() {
		// Build GUIs for this processor, always in this order!
		buildConfigGUI();
		buildGUI();

		Source.logger.debug("Created Source {}.", this);
	}

	private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();

		// Restore transient fields.
		Reflections.setFinalField(this, "rootLayout", new VBox(0));
		Reflections.setFinalField(this, "rootLayoutChildren", new VBox(0));
		Reflections.setFinalField(this, "rootTasksUIRefresh", new ArrayList<Runnable>(8));
		Reflections.setFinalField(this, "rootConfigLayout", new VBox(0));
		Reflections.setFinalField(this, "rootConfigLayoutChildren", new VBox(0));
		Reflections.setFinalField(this, "rootConfigTasksDialogRefresh", new ArrayList<Runnable>(8));
		Reflections.setFinalField(this, "rootConfigTasksDialogOK", new ArrayList<Runnable>(8));

		// Do construction.
		CommonConstructor();
	}

	/**
	 * Get the graphical layout corresponding to this class, so that it can be
	 * displayed somewhere by adding it to a Scene.
	 *
	 * @return GUI reference to display.
	 */
	public final Pane getGUI() {
		return rootLayout;
	}

	private void buildGUI() {
		GUISupport.addLabel(rootLayout, toString(), null, null, null);

		// Add layout box for sub-classes at the very end.
		rootLayout.getChildren().add(rootLayoutChildren);
	}

	/**
	 * Get the graphical layout for the configuration screen corresponding to
	 * this class, so that it can be
	 * displayed somewhere by adding it to a Scene.
	 *
	 * @return GUI reference to display.
	 */
	public Pane getConfigGUI() {
		return rootConfigLayout;
	}

	private void buildConfigGUI() {
		GUISupport.addLabel(rootConfigLayout, toString(), null, null, null);

		// Add layout box for sub-classes at the very end.
		rootConfigLayout.getChildren().add(rootConfigLayoutChildren);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}
}
