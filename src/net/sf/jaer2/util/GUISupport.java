package net.sf.jaer2.util;

import java.util.Collection;

import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import org.apache.commons.lang3.tuple.ImmutablePair;

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

	public static Label addLabel(final Pane parentPane, final String text, final String tooltip, final Color color,
		final Font font) {
		final Label label = new Label(text);

		label.setTooltip(new Tooltip((tooltip == null) ? (text) : (tooltip)));

		if (color != null) {
			label.setTextFill(color);
		}

		if (font != null) {
			label.setFont(font);
		}

		if (parentPane != null) {
			parentPane.getChildren().add(label);
		}

		return label;
	}

	public static <T> ImmutablePair<HBox, ComboBox<T>> addLabelWithComboBoxHorizontal(final Pane parentPane,
		final String text, final String tooltip, final Collection<T> values, final int defaultValue) {
		final HBox hbox = new HBox();

		// Create and add both Label and ComboBox.
		final Label label = GUISupport.addLabel(hbox, text, tooltip, null, null);
		final ComboBox<T> comboBox = GUISupport.addComboBox(hbox, values, defaultValue);

		// Ensure the ComboBox has the same Tooltip as the Label.
		comboBox.setTooltip(label.getTooltip());

		if (parentPane != null) {
			parentPane.getChildren().add(hbox);
		}

		return new ImmutablePair<>(hbox, comboBox);
	}

	public static <T> ImmutablePair<VBox, ComboBox<T>> addLabelWithComboBoxVertical(final Pane parentPane,
		final String text, final String tooltip, final Collection<T> values, final int defaultValue) {
		final VBox vbox = new VBox();

		// Create and add both Label and ComboBox.
		final Label label = GUISupport.addLabel(vbox, text, tooltip, null, null);
		final ComboBox<T> comboBox = GUISupport.addComboBox(vbox, values, defaultValue);

		// Ensure the ComboBox has the same Tooltip as the Label.
		comboBox.setTooltip(label.getTooltip());

		if (parentPane != null) {
			parentPane.getChildren().add(vbox);
		}

		return new ImmutablePair<>(vbox, comboBox);
	}

	public static Text addText(final Pane parentPane, final String text, final Color color, final Font font) {
		final Text txt = new Text(text);

		if (color != null) {
			txt.setFill(color);
		}

		if (font != null) {
			txt.setFont(font);
		}

		if (parentPane != null) {
			parentPane.getChildren().add(txt);
		}

		return txt;
	}
}
