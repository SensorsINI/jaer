package net.sf.jaer2.util;

import java.util.Collection;

import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.controlsfx.control.action.Action;
import org.controlsfx.dialog.Dialog;
import org.controlsfx.dialog.Dialogs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GUISupport {
	/** Local logger for log messages. */
	private final static Logger logger = LoggerFactory.getLogger(GUISupport.class);

	public static Button addButton(final Pane parentPane, final String text, final boolean displayText,
		final String imagePath) {
		final Button button = new Button();

		if (displayText) {
			button.setText(text);
		}

		button.setTooltip(new Tooltip(text));

		if (imagePath != null) {
			button.setGraphic(new ImageView(imagePath));
		}

		if (parentPane != null) {
			parentPane.getChildren().add(button);
		}

		return button;
	}

	public static Button addButtonWithMouseClickedHandler(final Pane parentPane, final String text,
		final boolean displayText, final String imagePath, final EventHandler<? super MouseEvent> handler) {
		final Button button = GUISupport.addButton(parentPane, text, displayText, imagePath);

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

	public static CheckBox addCheckBox(final Pane parentPane, final String text, final boolean selected) {
		final CheckBox checkBox = new CheckBox(text);

		checkBox.setTooltip(new Tooltip(text));

		checkBox.setSelected(selected);

		if (parentPane != null) {
			parentPane.getChildren().add(checkBox);
		}

		return checkBox;
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

	public static HBox addLabelWithControlsHorizontal(final Pane parentPane, final String text, final String tooltip,
		final Control... controls) {
		final HBox hbox = new HBox(5);

		// Create and add both Label and Control.
		final Label label = GUISupport.addLabel(hbox, text, tooltip, null, null);

		for (final Control control : controls) {
			hbox.getChildren().add(control);

			// Ensure the Control has the same Tooltip as the Label.
			control.setTooltip(label.getTooltip());
		}

		if (parentPane != null) {
			parentPane.getChildren().add(hbox);
		}

		return hbox;
	}

	public static VBox addLabelWithControlsVertical(final Pane parentPane, final String text, final String tooltip,
		final Control... controls) {
		final VBox vbox = new VBox(5);

		// Create and add both Label and Control.
		final Label label = GUISupport.addLabel(vbox, text, tooltip, null, null);

		for (final Control control : controls) {
			vbox.getChildren().add(control);

			// Ensure the Control has the same Tooltip as the Label.
			control.setTooltip(label.getTooltip());
		}

		if (parentPane != null) {
			parentPane.getChildren().add(vbox);
		}

		return vbox;
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

	public static void showDialog(final String title, final Node content,
		final Collection<ImmutablePair<Dialog.Actions, Runnable>> tasks) {
		final Dialog dialog = new Dialog(null, title, true, false);

		dialog.setContent(content);

		dialog.getActions().addAll(Dialog.Actions.OK, Dialog.Actions.CANCEL);

		final Action result = dialog.show();

		GUISupport.logger.debug("Clicked on {} in dialog.", result);

		if (result == Dialog.Actions.OK) {
			for (final ImmutablePair<Dialog.Actions, Runnable> task : tasks) {
				if ((task.left == null) || (task.left == Dialog.Actions.OK)) {
					task.right.run();
				}
			}
		}

		if (result == Dialog.Actions.CANCEL) {
			for (final ImmutablePair<Dialog.Actions, Runnable> task : tasks) {
				if ((task.left == null) || (task.left == Dialog.Actions.CANCEL)) {
					task.right.run();
				}
			}
		}
	}

	public static void showDialogInformation(final String message) {
		Dialogs.create().lightweight().title("Information").message(message).showInformation();
	}

	public static void showDialogWarning(final String message) {
		Dialogs.create().lightweight().title("Warning").message(message).showInformation();
	}

	public static void showDialogError(final String message) {
		Dialogs.create().lightweight().title("Error").message(message).showInformation();
	}

	public static void showDialogException(final Throwable exception) {
		Dialogs.create().lightweight().title("Exception detected").showException(exception);
	}

	public static HBox addArrow(final Pane parentPane, final double lineLength, final double lineWidth,
		final double headLength, final double headAperture) {
		final HBox arrow = new HBox();
		arrow.setAlignment(Pos.TOP_LEFT);

		if (parentPane != null) {
			parentPane.getChildren().add(arrow);
		}

		final Line line = new Line(0, 0, lineLength, 0);
		line.setStrokeWidth(lineWidth);
		line.setFill(Color.BLACK);
		line.setTranslateY(headAperture - (lineWidth / 2));
		arrow.getChildren().add(line);

		final Polygon head = new Polygon(0, 0, headLength, headAperture, 0, 2 * headAperture);
		head.setFill(Color.BLACK);
		arrow.getChildren().add(head);

		return arrow;
	}

	public static void runOnJavaFXThread(final Runnable operationToRun) {
		if (Platform.isFxApplicationThread()) {
			operationToRun.run();
		}
		else {
			Platform.runLater(operationToRun);
		}
	}
}
