package net.sf.jaer2.eventio;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javafx.event.EventHandler;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import net.sf.jaer2.util.GUISupport;
import net.sf.jaer2.util.Reflections;
import net.sf.jaer2.util.XMLconf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProcessorNetwork implements Serializable {
	private static final long serialVersionUID = 5207051699167107128L;

	/** Local logger for log messages. */
	private static final Logger logger = LoggerFactory.getLogger(ProcessorNetwork.class);

	/** Unique ID counter for chain identification. */
	private static int chainIdCounter = 1;

	/** List of all chains in this network. */
	private final List<ProcessorChain> processorChains = new ArrayList<>();

	/** Main GUI layout - Vertical Box. */
	transient private final VBox rootLayout = new VBox(10);

	public ProcessorNetwork() {
		CommonConstructor();
	}

	private void CommonConstructor() {
		buildGUI();

		ProcessorNetwork.logger.debug("Created ProcessorNetwork {}.", this);
	}

	private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();

		// Restore transient fields.
		Reflections.setFinalField(this, "rootLayout", new VBox(10));

		// Update chains to set parent network to this current instance.
		for (final ProcessorChain chain : processorChains) {
			chain.setParentNetwork(this);
		}

		// Do construction.
		CommonConstructor();
	}

	/**
	 * Get unique ID for processor chain in this network.
	 * Always increases by one, no duplicates.
	 * Starts at 1.
	 *
	 * @return Next unique ID for processor chain identification.
	 */
	public static int getNextAvailableChainID() {
		return ProcessorNetwork.chainIdCounter++;
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
	 */
	public void addChain(final ProcessorChain chain) {
		if (chain == null) {
			// Ignore null.
			return;
		}

		GUISupport.runOnJavaFXThread(new Runnable() {
			@Override
			public void run() {
				chain.setParentNetwork(ProcessorNetwork.this);

				processorChains.add(chain);
				rootLayout.getChildren().add(chain.getGUI());

				ProcessorNetwork.logger.debug("Added chain {}.", chain);
			}
		});
	}

	/**
	 * Deletes the specified processor chain and removes it from the GUI.
	 *
	 * @param chain
	 *            chain to remove.
	 */
	public void removeChain(final ProcessorChain chain) {
		GUISupport.runOnJavaFXThread(new Runnable() {
			@Override
			public void run() {
				rootLayout.getChildren().remove(chain.getGUI());
				processorChains.remove(chain);

				ProcessorNetwork.logger.debug("Removed chain {}.", chain);
			}
		});
	}

	/**
	 * Create the base GUI elements and add them to the rootLayout.
	 */
	private void buildGUI() {
		// First, add the buttons to manage new ProcessorChains.
		GUISupport.addButtonWithMouseClickedHandler(rootLayout, "New Chain", true, "/images/icons/Add.png",
			new EventHandler<MouseEvent>() {
				@Override
				public void handle(@SuppressWarnings("unused") final MouseEvent event) {
					addChain(new ProcessorChain());
				}
			});

		GUISupport.addButtonWithMouseClickedHandler(rootLayout, "Load Chain", true,
			"/images/icons/Import Document.png", new EventHandler<MouseEvent>() {
				@Override
				public void handle(@SuppressWarnings("unused") final MouseEvent event) {
					addChain(XMLconf.fromXML(ProcessorChain.class));
				}
			});

		// Add content already present at build-time.
		for (final ProcessorChain chain : processorChains) {
			rootLayout.getChildren().add(chain.getGUI());
		}
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}
}
