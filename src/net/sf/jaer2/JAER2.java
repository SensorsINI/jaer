package net.sf.jaer2;

import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import net.sf.jaer2.eventio.ProcessorNetwork;

public class JAER2 extends Application {
	public static void main(final String[] args) {
		// Launch the JavaFX application: do initialization and call start()
		// when ready.
		Application.launch(args);
	}

	@SuppressWarnings("unused")
	@Override
	public void start(final Stage primaryStage) throws Exception {
		ProcessorNetwork net = new ProcessorNetwork();

		final Rectangle2D screen = Screen.getPrimary().getVisualBounds();
		final Scene rootScene = new Scene(net.getGUI(), screen.getWidth(), screen.getHeight(), Color.GRAY);

		primaryStage.setTitle("jAER2 ProcessorNetwork Configuration");
		primaryStage.setScene(rootScene);

		primaryStage.show();
	}
}
