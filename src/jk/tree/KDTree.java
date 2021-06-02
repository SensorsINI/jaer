package jk.tree;
/*
 ** KDTree.java by Julian Kent
 **
 ** Licenced under the  Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported License
 **
 ** Licence summary:
 ** Under this licence you are free to:
 **      Share : copy and redistribute the material in any medium or format
 **      Adapt : remix, transform, and build upon the material
 **      The licensor cannot revoke these freedoms as long as you follow the license terms.
 **
 ** Under the following terms:
 **      Attribution:
 **            You must give appropriate credit, provide a link to the license, and indicate
 **            if changes were made. You may do so in any reasonable manner, but not in any
 **            way that suggests the licensor endorses you or your use.
 **      NonCommercial:
 **            You may not use the material for commercial purposes.
 **      ShareAlike:
 **            If you remix, transform, or build upon the material, you must distribute your
 **            contributions under the same license as the original.
 **      No additional restrictions:
 **            You may not apply legal terms or technological measures that legally restrict
 **            others from doing anything the license permits.
 **
 ** See full licencing details here: http://creativecommons.org/licenses/by-nc-sa/3.0/
 **
 ** For additional licencing rights (including commercial) please contact jkflying@gmail.com
 **
 */

import java.util.ArrayList;
import java.util.Arrays;

public abstract class KDTree<T> {

    // use a big bucketSize so that we have less node bounds (for more cache
    // hits) and better splits
    // if you have lots of dimensions this should be big, and if you have few small
    private static final int _bucketSize = 10;

    private final int _dimensions;
    private int _nodes;
    private final Node root;
    private final ArrayList<Node> nodeList = new ArrayList<Node>();

    // prevent GC from having to collect _bucketSize*dimensions*sizeof(float) bytes each
    // time a leaf splits
    private float[] mem_recycle;

    // the starting values for bounding boxes, for easy access
    private final float[] bounds_template;

    // one big self-expanding array to keep all the node bounding boxes so that
    // they stay in cache
    // node bounds available at:
    // low: 2 * _dimensions * node.index + 2 * dim
    // high: 2 * _dimensions * node.index + 2 * dim + 1
    private final ContiguousDoubleArrayList nodeMinMaxBounds;

    private KDTree(int dimensions) {
        _dimensions = dimensions;

        nodeMinMaxBounds = new ContiguousDoubleArrayList(1024 / 8 + 2 * _dimensions);
        mem_recycle = new float[_bucketSize * dimensions];

        bounds_template = new float[2 * _dimensions];
        Arrays.fill(bounds_template, Float.NEGATIVE_INFINITY);
        for (int i = 0, max = 2 * _dimensions; i < max; i += 2)
            bounds_template[i] = Float.POSITIVE_INFINITY;

        // and.... start!
        root = new Node();
    }

    public int nodes() {
        return _nodes;
    }

    public int size() {
        return root.entries;
    }

    public int addPoint(float[] location, T payload) {

        Node addNode = root;
        // Do a Depth First Search to find the Node where 'location' should be
        // stored
        while (addNode.pointLocations == null) {
            addNode.expandBounds(location);
            if (location[addNode.splitDim] < addNode.splitVal)
                addNode = nodeList.get(addNode.lessIndex);
            else
                addNode = nodeList.get(addNode.moreIndex);
        }
        addNode.expandBounds(location);

        int nodeSize = addNode.add(location, payload);

        if (nodeSize % _bucketSize == 0)
            // try splitting again once every time the node passes a _bucketSize
            // multiple
            // in case it is full of points of the same location and won't split
            addNode.split();

        return root.entries;
    }

    public ArrayList<SearchResult<T>> nearestNeighbours(float[] searchLocation, int K) {

        K = Math.min(K, size());

        ArrayList<SearchResult<T>> returnResults = new ArrayList<SearchResult<T>>(K);

        if (K > 0) {
            IntStack stack = new IntStack();
            PrioQueue<T> results = new PrioQueue<T>(K, true);

            stack.push(root.index);

            int added = 0;

            while (stack.size() > 0) {
                int nodeIndex = stack.pop();
                if (added < K || results.peekPrio() > pointRectDist(nodeIndex, searchLocation)) {
                    Node node = nodeList.get(nodeIndex);
                    if (node.pointLocations == null)
                        node.search(searchLocation, stack);
                    else
                        added += node.search(searchLocation, results);
                }
            }

            float[] priorities = results.priorities;
            Object[] elements = results.elements;
            for (int i = 0; i < K; i++) { // forward (closest first)
                SearchResult<T> s = new SearchResult<T>(priorities[i], (T) elements[i]);
                returnResults.add(s);
            }
        }
        return returnResults;
    }

    public ArrayList<T> ballSearch(float[] searchLocation, float radius) {
        IntStack stack = new IntStack();
        ArrayList<T> results = new ArrayList<T>();

        stack.push(root.index);

        while (stack.size() > 0) {
            int nodeIndex = stack.pop();
            if (radius > pointRectDist(nodeIndex, searchLocation)) {
                Node node = nodeList.get(nodeIndex);
                if (node.pointLocations == null)
                    stack.push(node.moreIndex).push(node.lessIndex);
                else
                    node.searchBall(searchLocation, radius, results);
            }
        }
        return results;
    }

    public ArrayList<T> rectSearch(float[] mins, float[] maxs) {
        IntStack stack = new IntStack();
        ArrayList<T> results = new ArrayList<T>();

        stack.push(root.index);

        while (stack.size() > 0) {
            int nodeIndex = stack.pop();
            if (overlaps(mins, maxs, nodeIndex)) {
                Node node = nodeList.get(nodeIndex);
                if (node.pointLocations == null)
                    stack.push(node.moreIndex).push(node.lessIndex);
                else
                    node.searchRect(mins, maxs, results);
            }
        }
        return results;

    }

    abstract float pointRectDist(int offset, final float[] location);

    abstract float pointDist(float[] arr, float[] location, int index);

    boolean contains(float[] arr, float[] mins, float[] maxs, int index) {

        int offset = (index + 1) * mins.length;

        for (int i = mins.length; i-- > 0;) {
            float d = arr[--offset];
            if (mins[i] > d | d > maxs[i])
                return false;
        }
        return true;
    }

    boolean overlaps(float[] mins, float[] maxs, int offset) {
        offset *= (2 * maxs.length);
        final float[] array = nodeMinMaxBounds.array;
        for (int i = 0; i < maxs.length; i++, offset += 2) {
            float bmin = array[offset], bmax = array[offset + 1];
            if (mins[i] > bmax | maxs[i] < bmin)
                return false;
        }

        return true;
    }

    public static class Euclidean<T> extends KDTree<T> {
        public Euclidean(int dims) {
            super(dims);
        }

        float pointRectDist(int offset, final float[] location) {
            offset *= (2 * super._dimensions);
            float distance = 0;
            final float[] array = super.nodeMinMaxBounds.array;
            for (int i = 0; i < location.length; i++, offset += 2) {

                float diff = 0;
                float bv = array[offset];
                float lv = location[i];
                if (bv > lv)
                    diff = bv - lv;
                else {
                    bv = array[offset + 1];
                    if (lv > bv)
                        diff = lv - bv;
                }
                distance += sqr(diff);
            }
            return distance;
        }

        float pointDist(float[] arr, float[] location, int index) {
            float distance = 0;
            int offset = (index + 1) * super._dimensions;

            for (int i = super._dimensions; i-- > 0;) {
                distance += sqr(arr[--offset] - location[i]);
            }
            return distance;
        }

    }

    public static class Manhattan<T> extends KDTree<T> {
        public Manhattan(int dims) {
            super(dims);
        }

        float pointRectDist(int offset, final float[] location) {
            offset *= (2 * super._dimensions);
            float distance = 0;
            final float[] array = super.nodeMinMaxBounds.array;
            for (int i = 0; i < location.length; i++, offset += 2) {

                float diff = 0;
                float bv = array[offset];
                float lv = location[i];
                if (bv > lv)
                    diff = bv - lv;
                else {
                    bv = array[offset + 1];
                    if (lv > bv)
                        diff = lv - bv;
                }
                distance += (diff);
            }
            return distance;
        }

        float pointDist(float[] arr, float[] location, int index) {
            float distance = 0;
            int offset = (index + 1) * super._dimensions;

            for (int i = super._dimensions; i-- > 0;) {
                distance += Math.abs(arr[--offset] - location[i]);
            }
            return distance;
        }
    }

    public static class WeightedManhattan<T> extends KDTree<T> {
        private float[] weights;

        public WeightedManhattan(int dims) {
            super(dims);
            weights = new float[dims];
            for (int i = 0; i < dims; i++)
                weights[i] = 1f;
        }

        public void setWeights(float[] newWeights) {
            weights = newWeights;
        }

        float pointRectDist(int offset, final float[] location) {
            offset *= (2 * super._dimensions);
            float distance = 0;
            final float[] array = super.nodeMinMaxBounds.array;
            for (int i = 0; i < location.length; i++, offset += 2) {

                float diff = 0;
                float bv = array[offset];
                float lv = location[i];
                if (bv > lv)
                    diff = bv - lv;
                else {
                    bv = array[offset + 1];
                    if (lv > bv)
                        diff = lv - bv;
                }
                distance += (diff) * weights[i];
            }
            return distance;
        }

        float pointDist(float[] arr, float[] location, int index) {
            float distance = 0;
            int offset = (index + 1) * super._dimensions;

            for (int i = super._dimensions; i-- > 0;) {
                distance += Math.abs(arr[--offset] - location[i]) * weights[i];
            }
            return distance;
        }
    }

    public static class SearchResult<S> {
        public float distance;
        public S payload;

        SearchResult(float dist, S load) {
            distance = dist;
            payload = load;
        }
    }

    private class Node {

        // for accessing bounding box data
        // - if trees weren't so unbalanced might be better to use an implicit
        // heap?
        int index;

        // keep track of size of subtree
        int entries;

        // leaf
        ContiguousDoubleArrayList pointLocations;
        ArrayList<T> pointPayloads = new ArrayList<T>(_bucketSize);

        // stem
        // Node less, more;
        int lessIndex, moreIndex;
        int splitDim;
        float splitVal;

        Node() {
            this(new float[_bucketSize * _dimensions]);
        }

        Node(float[] pointMemory) {
            pointLocations = new ContiguousDoubleArrayList(pointMemory);
            index = _nodes++;
            nodeList.add(this);
            nodeMinMaxBounds.add(bounds_template);
        }

        void search(float[] searchLocation, IntStack stack) {
            if (searchLocation[splitDim] < splitVal)
                stack.push(moreIndex).push(lessIndex); // less will be popped
            // first
            else
                stack.push(lessIndex).push(moreIndex); // more will be popped
            // first
        }

        // returns number of points added to results
        int search(float[] searchLocation, PrioQueue<T> results) {
            int updated = 0;
            for (int j = entries; j-- > 0;) {
                float distance = pointDist(pointLocations.array, searchLocation, j);
                if (results.peekPrio() > distance) {
                    updated++;
                    results.addNoGrow(pointPayloads.get(j), distance);
                }
            }
            return updated;
        }

        void searchBall(float[] searchLocation, float radius, ArrayList<T> results) {

            for (int j = entries; j-- > 0;) {
                float distance = pointDist(pointLocations.array, searchLocation, j);
                if (radius >= distance) {
                    results.add(pointPayloads.get(j));
                }
            }
        }

        void searchRect(float[] mins, float[] maxs, ArrayList<T> results) {

            for (int j = entries; j-- > 0;)
                if (contains(pointLocations.array, mins, maxs, j))
                    results.add(pointPayloads.get(j));

        }

        void expandBounds(float[] location) {
            entries++;
            int mio = index * 2 * _dimensions;
            for (int i = 0; i < _dimensions; i++) {
                nodeMinMaxBounds.array[mio] = Math.min(nodeMinMaxBounds.array[mio], location[i]);
                mio++;
                nodeMinMaxBounds.array[mio] = Math.max(nodeMinMaxBounds.array[mio], location[i]);
                mio++;
            }
        }

        int add(float[] location, T load) {
            pointLocations.add(location);
            pointPayloads.add(load);
            return entries;
        }

        void split() {
            int offset = index * 2 * _dimensions;

            float diff = 0;
            for (int i = 0; i < _dimensions; i++) {
                float min = nodeMinMaxBounds.array[offset];
                float max = nodeMinMaxBounds.array[offset + 1];
                if (max - min > diff) {
                    float mean = 0;
                    for (int j = 0; j < entries; j++)
                        mean += pointLocations.array[i + _dimensions * j];

                    mean = mean / entries;
                    float varianceSum = 0;

                    for (int j = 0; j < entries; j++)
                        varianceSum += sqr(mean - pointLocations.array[i + _dimensions * j]);

                    if (varianceSum > diff * entries) {
                        diff = varianceSum / entries;
                        splitVal = mean;

                        splitDim = i;
                    }
                }
                offset += 2;
            }

            // kill all the nasties
            if (splitVal == Float.POSITIVE_INFINITY)
                splitVal = Float.MAX_VALUE;
            else if (splitVal == Float.NEGATIVE_INFINITY)
                splitVal = Float.MIN_VALUE;
            else if (splitVal == nodeMinMaxBounds.array[index * 2 * _dimensions + 2 * splitDim + 1])
                splitVal = nodeMinMaxBounds.array[index * 2 * _dimensions + 2 * splitDim];

            Node less = new Node(mem_recycle); // recycle that memory!
            Node more = new Node();
            lessIndex = less.index;
            moreIndex = more.index;

            // reduce garbage by factor of _bucketSize by recycling this array
            float[] pointLocation = new float[_dimensions];
            for (int i = 0; i < entries; i++) {
                System.arraycopy(pointLocations.array, i * _dimensions, pointLocation, 0, _dimensions);
                T load = pointPayloads.get(i);

                if (pointLocation[splitDim] < splitVal) {
                    less.expandBounds(pointLocation);
                    less.add(pointLocation, load);
                }
                else {
                    more.expandBounds(pointLocation);
                    more.add(pointLocation, load);
                }
            }
            if (less.entries * more.entries == 0) {
                // one of them was 0, so the split was worthless. throw it away.
                _nodes -= 2; // recall that bounds memory
                nodeList.remove(moreIndex);
                nodeList.remove(lessIndex);
            }
            else {

                // we won't be needing that now, so keep it for the next split
                // to reduce garbage
                mem_recycle = pointLocations.array;

                pointLocations = null;

                pointPayloads.clear();
                pointPayloads = null;
            }
        }

    }

    // NB! This Priority Queue keeps things with the LOWEST priority.
    // If you want highest priority items kept, negate your values
    private static class PrioQueue<S> {

        Object[] elements;
        float[] priorities;
        private float minPrio;
        private int size;

        PrioQueue(int size, boolean prefill) {
            elements = new Object[size];
            priorities = new float[size];
            Arrays.fill(priorities, Float.POSITIVE_INFINITY);
            if (prefill) {
                minPrio = Float.POSITIVE_INFINITY;
                this.size = size;
            }
        }

        // uses O(log(n)) comparisons and one big shift of size O(N)
        // and is MUCH simpler than a heap --> faster on small sets, faster JIT

        void addNoGrow(S value, float priority) {
            int index = searchFor(priority);
            int nextIndex = index + 1;
            int length = size - nextIndex;
            System.arraycopy(elements, index, elements, nextIndex, length);
            System.arraycopy(priorities, index, priorities, nextIndex, length);
            elements[index] = value;
            priorities[index] = priority;

            minPrio = priorities[size - 1];
        }

        int searchFor(float priority) {
            int i = size - 1;
            int j = 0;
            while (i >= j) {
                int index = (i + j) >>> 1;
                if (priorities[index] < priority)
                    j = index + 1;
                else
                    i = index - 1;
            }
            return j;
        }

        float peekPrio() {
            return minPrio;
        }
    }

    private static class ContiguousDoubleArrayList {
        float[] array;
        int size;

        ContiguousDoubleArrayList(int size) {
            this(new float[size]);
        }

        ContiguousDoubleArrayList(float[] data) {
            array = data;
        }

        ContiguousDoubleArrayList add(float[] da) {
            if (size + da.length > array.length)
                array = Arrays.copyOf(array, (array.length + da.length) * 2);

            System.arraycopy(da, 0, array, size, da.length);
            size += da.length;
            return this;
        }
    }

    private static class IntStack {
        int[] array;
        int size;

        IntStack() {
            this(64);
        }

        IntStack(int size) {
            this(new int[size]);
        }

        IntStack(int[] data) {
            array = data;
        }

        IntStack push(int i) {
            if (size >= array.length)
                array = Arrays.copyOf(array, (array.length + 1) * 2);

            array[size++] = i;
            return this;
        }

        int pop() {
            return array[--size];
        }

        int size() {
            return size;
        }
    }

    static final float sqr(float d) {
        return d * d;
    }

}