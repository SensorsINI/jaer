package net.sf.jaer2.util;

import java.util.Collection;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
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

import org.controlsfx.control.ButtonBar;
import org.controlsfx.control.ButtonBar.ButtonType;
import org.controlsfx.control.action.AbstractAction;
import org.controlsfx.control.action.Action;
import org.controlsfx.dialog.Dialog;
import org.controlsfx.dialog.Dialogs;

public final class GUISupport {
	public static Button addButton(final Pane parentPane, final String text, final String imagePath) {
		final Button button = new Button(text);

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

	public static HBox addLabelWithControlHorizontal(final Pane parentPane, final String text, final String tooltip,
		final Control control) {
		final HBox hbox = new HBox();

		// Create and add both Label and Control.
		final Label label = GUISupport.addLabel(hbox, text, tooltip, null, null);
		hbox.getChildren().add(control);

		// Ensure the Control has the same Tooltip as the Label.
		control.setTooltip(label.getTooltip());

		if (parentPane != null) {
			parentPane.getChildren().add(hbox);
		}

		return hbox;
	}

	public static VBox addLabelWithControlVertical(final Pane parentPane, final String text, final String tooltip,
		final Control control) {
		final VBox vbox = new VBox();

		// Create and add both Label and Control.
		final Label label = GUISupport.addLabel(vbox, text, tooltip, null, null);
		vbox.getChildren().add(control);

		// Ensure the Control has the same Tooltip as the Label.
		control.setTooltip(label.getTooltip());

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

	public static Action getActionWithRunnables(final String text, final ButtonType type,
		final Collection<Runnable> tasks) {
		final Action action = new AbstractAction(text) {
			{
				ButtonBar.setType(this, type);
			}

			@Override
			public void execute(final ActionEvent ae) {
				if (!isDisabled()) {
					if (ae.getSource() instanceof Dialog) {
						final Dialog dlg = (Dialog) ae.getSource();

						for (final Runnable task : tasks) {
							task.run();
						}

						dlg.hide();
					}
				}
			}
		};

		return action;
	}

	public static void showDialog(final String title, final Node content, final Collection<Runnable> tasks) {
		final Dialog dialog = new Dialog(null, title, true, false);

		dialog.setContent(content);

		dialog.getActions().addAll(
			(tasks == null) ? (Dialog.Actions.OK)
				: (GUISupport.getActionWithRunnables("OK", ButtonType.OK_DONE, tasks)), Dialog.Actions.CANCEL);

		dialog.show();
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

	public static HBox addArrow(final Pane parentPane, final double lineLength, final double lineWidth, final double headLength,
		final double headAperture) {
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
}
