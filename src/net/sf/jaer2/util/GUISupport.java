package net.sf.jaer2.util;

import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;

public class GUISupport {
	public static Button addButton(final Pane parentPane, final String text, final String imagePath) {
		final Button button = new Button(text, new ImageView(imagePath));

		parentPane.getChildren().add(button);

		return button;
	}

	public static Button addButtonWithMouseClickedHandler(final Pane parentPane, final String text,
		final String imagePath, final EventHandler<? super MouseEvent> handler) {
		final Button button = GUISupport.addButton(parentPane, text, imagePath);

		if (handler != null) {
			button.setOnMouseClicked(handler);
		}

		return button;
	}
}
