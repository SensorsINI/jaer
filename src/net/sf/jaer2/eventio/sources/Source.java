package net.sf.jaer2.eventio.sources;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import net.sf.jaer2.util.GUISupport;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.controlsfx.control.action.Action;
import org.controlsfx.dialog.Dialog;

public abstract class Source {
	/** Main GUI layout - Horizontal Box. */
	private final HBox rootLayout = new HBox(0);
	/** Main GUI layout for Sub-Classes - Vertical Box. */
	protected final VBox rootLayoutChildren = new VBox(0);

	/** Configuration GUI layout - Vertical Box. */
	private final VBox rootConfigLayout = new VBox(0);
	/** Configuration GUI layout for Sub-Classes - Vertical Box. */
	protected final VBox rootConfigLayoutChildren = new VBox(0);

	/** Configuration GUI: tasks to execute on dialog closure. */
	protected final List<ImmutablePair<Dialog.Actions, Runnable>> rootConfigTasks = new ArrayList<>(2);

	public Source() {
		buildConfigGUI();
		buildGUI();
	}

	public void executeConfigTasks(final Action result) {
		if (result == Dialog.Actions.OK) {
			for (final ImmutablePair<Dialog.Actions, Runnable> task : rootConfigTasks) {
				if ((task.left == null) || (task.left == Dialog.Actions.OK)) {
					task.right.run();
				}
			}
		}

		if (result == Dialog.Actions.CANCEL) {
			for (final ImmutablePair<Dialog.Actions, Runnable> task : rootConfigTasks) {
				if ((task.left == null) || (task.left == Dialog.Actions.CANCEL)) {
					task.right.run();
				}
			}
		}
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
