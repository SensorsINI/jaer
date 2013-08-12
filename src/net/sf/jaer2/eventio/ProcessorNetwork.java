package net.sf.jaer2.eventio;

import java.util.LinkedList;
import java.util.List;

import javafx.event.EventHandler;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import net.sf.jaer2.util.GUISupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProcessorNetwork {
	private final static Logger logger = LoggerFactory.getLogger(ProcessorNetwork.class);

	private final List<ProcessorChain> processorChains = new LinkedList<>();

	private int chainIdCounter = 1;

	private final VBox rootLayout = new VBox(20);

	public ProcessorNetwork() {
		buildGUI();

		ProcessorNetwork.logger.debug("Created ProcessorNetwork {}.", this);
	}

	/**
	 * Get unique ID for ProcessorChains in this network.
	 * Always increases by one, no duplicates.
	 * Starts at 1.
	 *
	 * @return Next unique ID for ProcessorChain identification.
	 */
	public int getNextAvailableChainID() {
		return chainIdCounter++;
	}

	public Pane getGUI() {
		return rootLayout;
	}

	public ProcessorChain addChain() {
		final ProcessorChain chain = new ProcessorChain(this);

		processorChains.add(chain);
		rootLayout.getChildren().add(chain.getGUI());

		ProcessorNetwork.logger.debug("Added chain {}.", chain);

		return chain;
	}

	public void removeChain(final ProcessorChain chain) {
		rootLayout.getChildren().remove(chain.getGUI());
		processorChains.remove(chain);

		ProcessorNetwork.logger.debug("Removed chain {}.", chain);
	}

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
