package net.sf.jaer2;

import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import net.sf.jaer2.eventio.ProcessorNetwork;

public final class JAER2 extends Application {
	public static void main(final String[] args) {
		// Launch the JavaFX application: do initialization and call start()
		// when ready.
		Application.launch(args);
	}

	@Override
	public void start(final Stage primaryStage) throws Exception {
		// TODO: implement save/restore network.
		final ProcessorNetwork net = new ProcessorNetwork();

		final ScrollPane scroll = new ScrollPane(net.getGUI());

		final Rectangle2D screen = Screen.getPrimary().getVisualBounds();
		final Scene rootScene = new Scene(scroll, screen.getWidth(), screen.getHeight(), Color.GRAY);

		// Add default CSS style-sheet.
		rootScene.getStylesheets().add("/styles/root.css");

		primaryStage.setTitle("jAER2 ProcessorNetwork Configuration");
		primaryStage.setScene(rootScene);

		primaryStage.show();
	}
}
