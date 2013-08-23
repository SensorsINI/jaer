package net.sf.jaer2.util;

import java.io.File;
import java.util.Collection;
import java.util.List;

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
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.controlsfx.control.action.Action;
import org.controlsfx.dialog.Dialog;
import org.controlsfx.dialog.Dialogs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GUISupport {
	/** Local logger for log messages. */
	private static final Logger logger = LoggerFactory.getLogger(GUISupport.class);

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

	public static void runTasksCollection(final Collection<Runnable> tasks) {
		if (tasks != null) {
			for (final Runnable task : tasks) {
				GUISupport.runOnJavaFXThread(task);
			}
		}
	}

	public static void showDialog(final String title, final Node content,
		final Collection<Runnable> tasksDialogRefresh, final Collection<Runnable> tasksDialogOK,
		final Collection<Runnable> tasksUIRefresh) {
		GUISupport.runTasksCollection(tasksDialogRefresh);

		final Dialog dialog = new Dialog(null, title, true, false);

		dialog.setContent(content);

		dialog.getActions().addAll(Dialog.Actions.OK, Dialog.Actions.CANCEL);

		final Action result = dialog.show();

		GUISupport.logger.debug("Dialog: clicked on {}.", result);

		if (result == Dialog.Actions.OK) {
			GUISupport.runTasksCollection(tasksDialogOK);

			GUISupport.runTasksCollection(tasksUIRefresh);
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

	public static File showDialogLoadFile(final List<ImmutablePair<String, String>> allowedExtensions) {
		final FileChooser fileChooser = new FileChooser();

		fileChooser.setTitle("Select File to load from ...");

		if (allowedExtensions != null) {
			for (final ImmutablePair<String, String> ext : allowedExtensions) {
				fileChooser.getExtensionFilters().add(new ExtensionFilter(ext.left, ext.right));
			}
		}

		final File toLoad = fileChooser.showOpenDialog(null);

		if (toLoad == null) {
			return null;
		}

		if (!GUISupport.checkReadPermissions(toLoad)) {
			GUISupport.showDialogError("Cannot read from file " + toLoad.getAbsolutePath());
			return null;
		}

		// Sanity check on file name extension.
		if (allowedExtensions != null) {
			for (final ImmutablePair<String, String> ext : allowedExtensions) {
				if (toLoad.getName().endsWith(ext.right.substring(ext.right.indexOf('.')))) {
					return toLoad;
				}
			}

			GUISupport.showDialogError("Invalid file-name extension!");
			return null;
		}

		return toLoad;
	}

	public static File showDialogSaveFile(final List<ImmutablePair<String, String>> allowedExtensions) {
		final FileChooser fileChooser = new FileChooser();

		fileChooser.setTitle("Select File to save to ...");

		if (allowedExtensions != null) {
			for (final ImmutablePair<String, String> ext : allowedExtensions) {
				fileChooser.getExtensionFilters().add(new ExtensionFilter(ext.left, ext.right));
			}
		}

		final File toSave = fileChooser.showSaveDialog(null);

		if (toSave == null) {
			return null;
		}

		if (!GUISupport.checkWritePermissions(toSave)) {
			GUISupport.showDialogError("Cannot write to file " + toSave.getAbsolutePath());
			return null;
		}

		// Sanity check on file name extension.
		if (allowedExtensions != null) {
			for (final ImmutablePair<String, String> ext : allowedExtensions) {
				if (toSave.getName().endsWith(ext.right.substring(ext.right.indexOf('.')))) {
					return toSave;
				}
			}

			GUISupport.showDialogError("Invalid file-name extension!");
			return null;
		}

		return toSave;
	}

	public static boolean checkReadPermissions(final File f) throws NullPointerException {
		if (f == null) {
			throw new NullPointerException();
		}

		// We want to read, so it has to exist and be readable.
		if (f.exists() && f.canRead()) {
			return true;
		}

		return false;
	}

	public static boolean checkWritePermissions(final File f) throws NullPointerException {
		if (f == null) {
			throw new NullPointerException();
		}

		if (f.exists()) {
			if (f.canWrite()) {
				// If it exists already, but is writable.
				return true;
			}

			// Exists already, but is not writable.
			return false;
		}

		// Non-existing paths can usually be written to.
		return true;
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

	public static void runOnJavaFXThread(final Runnable operationToRun) throws NullPointerException {
		if (operationToRun == null) {
			throw new NullPointerException();
		}

		if (Platform.isFxApplicationThread()) {
			operationToRun.run();
		}
		else {
			Platform.runLater(operationToRun);
		}
	}
}
