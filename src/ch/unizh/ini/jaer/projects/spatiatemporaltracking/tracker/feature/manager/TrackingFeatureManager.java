/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.media.opengl.GLAutoDrawable;

import net.sf.jaer.event.TypedEvent;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.factory.FeatureExtractorFactory;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.FeatureExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.notifier.FeatureNotifier;

import com.jogamp.opengl.util.awt.TextRenderer;

/**
 *
 * @author matthias
 */
public class TrackingFeatureManager implements FeatureManager {

	/**
	 * Stores the reference time which corresponds to the time of the
	 * creation of the algorithm.
	 */
	private int reference;

	/** Stores the source of the manager. */
	private FeatureNotifier notifier;

	/** The last event added to this manager. */
	private TypedEvent event;

	/** The last packet added to this manager. */
	private List<TypedEvent> packet;

	/** Stores the instance of the factory used to create new extractors. */
	private FeatureExtractorFactory factory;

	/**
	 * Stores the features.
	 */
	private Map<Features, FeatureExtractor> features;

	/**
	 * Creates a new instance of the class TrackingFeatureManager.
	 * 
	 * @param notifier The notifier used by this manager to notify the
	 * associated extractors about changes.
	 * @param factory The factory used to crate new extractors.
	 * @param reference The timestamp used as reference.
	 */
	public TrackingFeatureManager(FeatureNotifier notifier,
		FeatureExtractorFactory factory,
		int reference) {
		this.notifier = notifier;
		this.factory = factory;
		this.reference = reference;

		features = new EnumMap<Features, FeatureExtractor>(Features.class);
		packet = new ArrayList<TypedEvent>();
	}

	@Override
	public int getReferenceTime() {
		return reference;
	}

	@Override
	public void add(FeatureExtractor feature) {
		delete(feature.getIdentifier());

		features.put(feature.getIdentifier(), feature);
	}

	@Override
	public FeatureExtractor add(Features feature) {
		return factory.addFeature(this, feature);
	}

	@Override
	public FeatureExtractor get(Features feature) {
		if (!has(feature)) {
			return this.add(feature);
		}
		return features.get(feature);
	}

	@Override
	public Set<Features> getFeatures() {
		return features.keySet();
	}

	@Override
	public boolean has(Features feature) {
		if ((feature == Features.None) ||
			(feature == Features.Event) ||
			(feature == Features.Packet)) {
			return true;
		}

		return features.containsKey(feature);
	}

	@Override
	public FeatureExtractor remove(FeatureExtractor extractor) {
		return this.remove(extractor.getIdentifier());
	}

	@Override
	public FeatureExtractor remove(Features feature) {
		if (features.containsKey(feature)) {
			FeatureExtractor r = features.remove(feature);
			notifier.remove(r);

			return r;
		}
		return null;
	}

	@Override
	public void delete(Features feature) {
		if (features.containsKey(feature)) {
			this.remove(feature).delete();
		}
	}

	@Override
	public void clean() {
		if (features.isEmpty()) {
			return;
		}

		Set<Features> keys = features.keySet();
		for (Features key : keys) {
			delete(key);
		}

		clean();
	}

	@Override
	public FeatureNotifier getNotifier() {
		return notifier;
	}

	@Override
	public void add(TypedEvent event) {
		this.event = event;
		packet.add(event);

		notifier.notify(Features.Event, event.timestamp);
	}

	@Override
	public void packet(int timestamp) {
		notifier.notify(Features.Packet, timestamp);

		packet.clear();
	}

	@Override
	public TypedEvent getEvent() {
		return event;
	}

	@Override
	public List<TypedEvent> getPacket() {
		return packet;
	}

	@Override
	public void draw(GLAutoDrawable drawable, TextRenderer renderer, float x, float y) {
		int offset = 0;
		Collection<FeatureExtractor> lfe = features.values();
		for (FeatureExtractor extractor : lfe) {
			synchronized(extractor) {
				extractor.draw(drawable, renderer, x, y - offset);
				offset += extractor.getHeight();
			}
		}
	}

	@Override
	public int getHeight() {
		int offset = 0;
		Collection<FeatureExtractor> lfe = features.values();
		for (FeatureExtractor extractor : lfe) {
			offset += extractor.getHeight();
		}
		return offset;
	}
}
