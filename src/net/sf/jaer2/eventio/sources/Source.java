package net.sf.jaer2.eventio.sources;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import net.sf.jaer2.util.GUISupport;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.controlsfx.dialog.Dialog;

public abstract class Source {
	/** Main GUI layout - Vertical Box. */
	protected final VBox rootLayout = new VBox(10);

	/** Configuration GUI layout - Vertical Box. */
	protected final VBox rootConfigLayout = new VBox(10);
	/** Configuration GUI: tasks to execute on success. */
	protected final List<ImmutablePair<Dialog.Actions, Runnable>> rootConfigTasks = new ArrayList<>(2);

	public Source() {
		buildConfigGUI();
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
	}
}
