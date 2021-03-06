package fasthash.impl;

import fasthash.model.AbstractCache;
import fasthash.model.Order;
import fasthash.stats.ProbeCounter;
import gnu.trove.impl.PrimeFinder;

/**
 * @author Roman Elizarov
 */
public class FastCache2 extends AbstractCache {
	private int size;
	private Order[] a = new Order[13];

	public int size() {
		return size;
	}

	private int hash(long id) {
		return ((int)id ^ (int)(id >>> 32)) & 0x7fffffff;
	}

	private int index0(int hash, int length) {
		return hash % length;
	}

	private int step(int hash, int length) {
		return 1 + (hash % (length - 2));
	}

	private int next(int index, int step, int length) {
		index += step;
		if (index >= length)
			index -= length;
		return index;
	}

	public void addObject(Order order) {
		if (size >= (2 * a.length) / 3)
			rehash();
		if (putImpl(a, order) == null)
			size++;
	}

	private Order putImpl(Order[] a, Order order) {
		long id = order.getId();
		int hash = hash(id);
		int index = index0(hash, a.length);
		Order obj = a[index];
		if (obj == null || obj.getId() == id) {
			a[index] = order;
			return obj;
		}
		int step = step(hash, a.length);
		while ((obj = a[index = next(index, step, a.length)]) != null) {
			if (obj.getId() == id)
				break;
		}
		a[index] = order;
		return obj;
	}

	private void rehash() {
		Order[] b = new Order[PrimeFinder.nextPrime(a.length * 2)];
		for (int i = a.length; --i >= 0;) {
			Order order = a[i];
			if (order != null)
				putImpl(b, order);
		}
		a = b;
	}

	public Order getById(long id) {
		int hash = hash(id);
		int index = index0(hash, a.length);
		Order obj = a[index];
		if (obj == null || obj.getId() == id)
			return obj;
		int step = step(hash, a.length);
		while ((obj = a[index = next(index, step, a.length)]) != null) {
			if (obj.getId() == id)
				return obj;
		}
		return null;
	}

	@Override
	protected double getFillFactor() {
		return (double)size / a.length;
	}

	@Override
	protected double countTotalProbes() {
		ProbeCounter cnt = new ProbeCounter(0);
		for (Order order : a)
			if (order != null)
				countProbes(order.getId(), cnt);
		return cnt.getCount();
	}

	@Override
	protected double countAccessProbes(long[] access, ProbeCounter cnt) {
		for (long id : access)
			countProbes(id, cnt);
		return cnt.getCount();
	}

	private void countProbes(long id, ProbeCounter cnt) {
		int hash = hash(id);
		int index = index0(hash, a.length);
		cnt.access(index);
		Order obj = a[index];
		assert obj != null;
		if (obj.getId() == id)
			return;
		int step = step(hash, a.length);
		index = next(index, step, a.length);
		cnt.access(index);
		while ((obj = a[index]) != null) {
			if (obj.getId() == id)
				break;
			index = next(index, step, a.length);
			cnt.access(index);
		}
	}
}
