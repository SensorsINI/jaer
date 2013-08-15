package net.sf.jaer2.eventio.processors;

import javafx.scene.layout.Pane;

import javax.media.opengl.GLAutoDrawable;

import net.sf.jaer2.eventio.eventpackets.EventPacketContainer;

public interface AnnotatedProcessor {
	public Object prepareAnnotateEvents(EventPacketContainer container);

	public void annotateEvents(EventPacketContainer container, Object annotateData, GLAutoDrawable glDrawable,
		Pane fxPane);
}
