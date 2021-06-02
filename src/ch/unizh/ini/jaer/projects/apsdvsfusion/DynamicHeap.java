/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import java.util.ArrayList;

/**
 * Creates a Heap as also implemented by java.util.PriorityQueue, but allows direct access to the elements of the 
 * heap. Thereby, it becomes possible to change the weight of an element of the heap.
 * @author Dennis
 *
 */
public class DynamicHeap<T extends Comparable<T>> {
	ArrayList<Entry> list = new ArrayList<Entry>();
	
	public class Entry {
		T content;
		int position;
		public Entry(T content) {
			this(content,-1);
		}
		public Entry(T content, int position) {
			this.position = position;
			this.content = content;
		}
		public int getPosition() {
			return position;
		}
		public T getContent() {
			return content;
		}
		public void contentChanged() {
			reposition(this);
		}
		public void remove() {
			synchronized (DynamicHeap.this) {
				if (position >= 0)
					DynamicHeap.this.remove(position);
			}
		}
		public void setContent(T content) {
			synchronized (DynamicHeap.this) {
				this.content = content;
				reposition(this);
			}
		}
		public String toString() {
			return content.toString()+"@"+position;
		}
	}
	
	/**
	 * 
	 */
	public DynamicHeap() {
	}
	public synchronized Entry add(Entry entry) {
		if (entry != null) {
			entry.position = list.size();
			list.add(entry);
			moveUp(entry);
		}
		return entry;
	}
	public synchronized Entry add(T content) {
		Entry entry = new Entry(content);
		add(entry);
		return entry;
	}
	private int moveUp(Entry entry, int position) {
		if (position > 0 && position < list.size()) {
			Entry parent = list.get((position-1)/2);
			T content = entry.content;
			while (position > 0 && content.compareTo(parent.content) < 0) {
				list.set(position, parent);
				parent.position = position;
				position = (position-1)/2;
				entry.position = position;
				list.set(position, entry);
				parent = list.get((position-1)/2);
			}
			return position;
		}
		else return -1;
	}
	protected void moveUp(Entry entry) {
		moveUp(entry,entry.position);
	}
	private int moveDownOnce(Entry entry, int position) {
		if (position*2+1 >= list.size())
			return -1;
		else {
			Entry childA = list.get(position*2+1);
			Entry childB = (list.size() > position*2+2)?list.get(position*2+2):null;
			if (childB == null || childA.content.compareTo(childB.content) <= 0) {
				if (childA.content.compareTo(entry.content) < 0) {
					list.set(position, childA);
					childA.position = position;
					position = position*2+1;
					list.set(position, entry);
					entry.position = position;
					return position;
				}
				else return -1;
			}
			else if (childB.content.compareTo(entry.content) < 0) {
				list.set(position, childB);
				childB.position = position;
				position = position*2+2;
				list.set(position, entry);
				entry.position = position;
				return position;
			}
			else return -1;
		}
	}
	private int moveDown(Entry entry, int position) {
		if (position >= 0 && position*2+1 < list.size()) {
			position = moveDownOnce(entry,position);
			if (position < 0)
				return -1;
			while (position >= 0) {
				position = moveDownOnce(entry,position);
			}
			return entry.position;
		}
		else return -1;
	}
	protected void moveDown(Entry entry) {
		moveDown(entry,entry.position);
	}
	public synchronized void reposition(Entry entry) {
		if (entry.position < 0)
			add(entry);
		else {
			if (moveDown(entry,entry.position) < 0)
				moveUp(entry,entry.position);
		}
	}
	
	public synchronized Entry remove(int position) {
		if (position >= 0 && position < list.size()) {
			Entry ret = list.get(position);
			ret.position = -1;

			Entry replacement = list.remove(list.size()-1);
			if (list.size() > position) {
				replacement.position = position;
				list.set(position,replacement);
				moveDown(replacement,position);
			}
			return ret;
		}
		else return null;
	}
	
	public boolean isEmpty() {
		return list.isEmpty();
	}
	public void clear() {
		for (Entry e : list)
			e.position = -1;
		list.clear();
	}
	public int size() {
		return list.size();
	}
	public synchronized Entry peek() {
		if (list.size() > 0)
			return list.get(0);
		else return null;
	}
	public synchronized Entry poll() {
		return remove(0);
	}
	public synchronized Entry push(Entry entry) {
		return add(entry);
	}
	
	public synchronized Entry push(T content) {
		return add(content);
	}
	
	/**
	 * Creates a new entry for the heap, which is not yet added to the heap.
	 * @param content The content for this Entry.
	 * @return The newly created entry.
	 */
	public Entry createEntry(T content) {
		return new Entry(content);
	}
	public String toString() {
		return list.toString();
	}
//	@SuppressWarnings("unchecked")
//	public static void main(String[] args) {
//		DynamicHeap<Integer> heap = new DynamicHeap<Integer>();
////		DynamicHeap<Integer>.Entry[] entries = (DynamicHeap<Integer>.Entry[])new Object[100];
////		DynamicHeap<Integer>.Entry[] entries = heap.createEntryArray(100);
////		DynamicHeap<Integer>.Entry[] entries = (DynamicHeap<Integer>.Entry[])Array.newInstance(heap.Entry.getClass, 100);
//		Object[] entries = new Object[100];
//		Random r = new Random();
//		for (int i = 0; i < 100; i++) {
//			entries[i] = heap.add(r.nextInt(10000));
//		}
//		for (int i = 0; i < 100; i++) {
//			((DynamicHeap<Integer>.Entry)entries[i]).setContent(r.nextInt(10000));
//		}
//		System.out.println(heap.peek());
//		for (int i = 0; i < 100; i++) {
//			System.out.println(heap.poll());
//		}
//		for (int i = 0; i < 100; i++) {
//			((DynamicHeap<Integer>.Entry)entries[i]).setContent(r.nextInt(10000));
////			entries[i] = heap.add(r.nextInt(10000));
//		}
//		for (int i = 0; i < 100; i++) {
//			((DynamicHeap<Integer>.Entry)entries[i]).remove();
//		}
//		System.out.println(heap);
//	}
}
