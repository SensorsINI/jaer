package net.sf.jaer2.util;

import java.util.Collection;

import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;

public class GUISupport {
	public static Button addButton(final Pane parentPane, final String text, final String imagePath) {
		final Button button = new Button(text, new ImageView(imagePath));

		if (parentPane != null) {
			parentPane.getChildren().add(button);
		}

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

	public static <T> ComboBox<T> addComboBox(final Pane parentPane, final Collection<T> values, final int defaultValue)
		throws IndexOutOfBoundsException {
		if ((defaultValue < -1) || (defaultValue >= values.size())) {
			throw new IndexOutOfBoundsException();
		}

		final ComboBox<T> comboBox = new ComboBox<>();

		comboBox.getItems().addAll(values);
		if (defaultValue != -1) {
			comboBox.getSelectionModel().select(defaultValue);
		}

		if (parentPane != null) {
			parentPane.getChildren().add(comboBox);
		}

		return comboBox;
	}
}
