package net.sf.jaer2.util;

import java.util.Collection;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
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
import org.controlsfx.control.ButtonBar;
import org.controlsfx.control.ButtonBar.ButtonType;
import org.controlsfx.control.action.AbstractAction;
import org.controlsfx.control.action.Action;
import org.controlsfx.dialog.Dialog;
import org.controlsfx.dialog.Dialogs;

public final class GUISupport {
	public static Button addButton(final Pane parentPane, final String text, final String imagePath) {
		final Button button = new Button(text);

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
}
