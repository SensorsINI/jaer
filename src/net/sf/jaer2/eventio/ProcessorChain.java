package net.sf.jaer2.eventio;

import java.util.LinkedList;
import java.util.List;

import javafx.event.EventHandler;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import net.sf.jaer2.eventio.processors.Processor;
import net.sf.jaer2.util.GUISupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProcessorChain {
	private final static Logger logger = LoggerFactory.getLogger(ProcessorChain.class);

	private final int chainId;
	private final String chainName;

	protected final ProcessorNetwork parentNetwork;

	private final List<Processor> processors = new LinkedList<>();

	private int processorIdCounter = 1;

	private final HBox rootLayout = new HBox(20);

	public ProcessorChain(final ProcessorNetwork network) {
		parentNetwork = network;

		chainId = parentNetwork.getNextAvailableChainID();
		chainName = getClass().getSimpleName();

		buildGUI();

		ProcessorChain.logger.debug("Created ProcessorChain {}.", this);
	}

	/**
	 * Get unique ID for Processors in this chain.
	 * Always increases by one, no duplicates.
	 * Starts at 1.
	 *
	 * @return Next unique ID for Processor identification.
	 */
	public int getNextAvailableProcessorID() {
		return processorIdCounter++;
	}

	public Pane getGUI() {
		return rootLayout;
	}

	private void buildGUI() {
		final VBox controlBox = new VBox(10);
		rootLayout.getChildren().add(controlBox);

		// First, add the buttons to manage ProcessorChains and new Processors.
		final Label chainDescription = new Label(toString());
		controlBox.getChildren().add(chainDescription);

		GUISupport.addButtonWithMouseClickedHandler(controlBox, "Delete Chain", "/icons/Remove.png",
			new EventHandler<MouseEvent>() {
				@Override
				public void handle(@SuppressWarnings("unused") final MouseEvent event) {
					parentNetwork.removeChain(ProcessorChain.this);
				}
			});

		GUISupport.addButtonWithMouseClickedHandler(controlBox, "New Processor", "/icons/Add.png", null);
	}

	public int getChainId() {
		return chainId;
	}

	public String getChainName() {
		return chainName;
	}

	public ProcessorNetwork getParentNetwork() {
		return parentNetwork;
	}

	@Override
	public String toString() {
		return String.format("%s - ID %d", chainName, chainId);
	}
}
