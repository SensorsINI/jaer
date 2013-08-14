package net.sf.jaer2.eventio;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import net.sf.jaer2.util.GUISupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProcessorNetwork {
	/** Local logger for log messages. */
	private final static Logger logger = LoggerFactory.getLogger(ProcessorNetwork.class);

	/** List of all chains in this network. */
	private final ObservableList<ProcessorChain> processorChains = FXCollections.observableArrayList();

	/** Unique ID counter for chain identification. */
	private int chainIdCounter = 1;

	/** Main GUI layout. */
	private final VBox rootLayout = new VBox(10);

	public ProcessorNetwork() {
		buildGUI();

		ProcessorNetwork.logger.debug("Created ProcessorNetwork {}.", this);
	}

	/**
	 * Get unique ID for processor chain in this network.
	 * Always increases by one, no duplicates.
	 * Starts at 1.
	 *
	 * @return Next unique ID for processor chain identification.
	 */
	public int getNextAvailableChainID() {
		return chainIdCounter++;
	}

	/**
	 * Get the graphical layout corresponding to this class, so that it can be
	 * displayed somewhere by adding it to a Scene.
	 *
	 * @return GUI reference to display.
	 */
	public Pane getGUI() {
		return rootLayout;
	}

	/**
	 * Create a new processor chain and add it to the GUI.
	 *
	 * @return the new processor chain.
	 */
	public ProcessorChain addChain() {
		final ProcessorChain chain = new ProcessorChain(this);

		processorChains.add(chain);
		rootLayout.getChildren().add(chain.getGUI());

		ProcessorNetwork.logger.debug("Added chain {}.", chain);

		return chain;
	}

	/**
	 * Deletes the specified processor chain and removes it from the GUI.
	 *
	 * @param chain
	 *            chain to remove.
	 */
	public void removeChain(final ProcessorChain chain) {
		rootLayout.getChildren().remove(chain.getGUI());
		processorChains.remove(chain);

		ProcessorNetwork.logger.debug("Removed chain {}.", chain);
	}

	/**
	 * Create the base GUI elements and add them to the rootLayout.
	 */
	private void buildGUI() {
		// First, add the buttons to manage new ProcessorChains.
		GUISupport.addButtonWithMouseClickedHandler(rootLayout, "New Chain", "/icons/Add.png",
			new EventHandler<MouseEvent>() {
				@Override
				public void handle(@SuppressWarnings("unused") final MouseEvent event) {
					addChain();
				}
			});
	}
}
