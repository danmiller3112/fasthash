package fasthash.benchmark;

import java.io.*;
import java.util.Locale;

import fasthash.model.Cache;
import fasthash.model.Order;
import fasthash.stats.CacheStats;
import fasthash.stats.Stats;

/**
 * @author Roman Elizarov
 */
public class BenchmarkAccessSpeed {
	private static final int PASSES = Integer.getInteger("passes", 50);
	private static final int STABLE_PASS = 3;
	private static final int PASSES_PER_SEED = 3;

	public static void main(String[] args) throws IOException {
		if (args.length != 1) {
			System.out.println("Usage: " + BenchmarkAccessSpeed.class.getName() + " <impl-class-name>");
			return;
		}
		PrintWriter log = new PrintWriter(new FileWriter("BenchmarkAccessSpeed.log", true), true);
		try {
			new BenchmarkAccessSpeed(args[0], log).go();
		} finally {
			log.close();
		}
	}

	private final String implClassName;
	private final PrintWriter log;

	public BenchmarkAccessSpeed(String implClassName, PrintWriter log) {
		this.implClassName = implClassName;
		this.log = log;
	}

	Cache createImpl() {
		try {
			return (Cache)Class.forName(implClassName).newInstance();
		} catch (Exception e) {
			throw new IllegalArgumentException(e);
		}
	}

	private AccessSequence seq;
	private Cache impl;
	private int lastCheckSum;

	private void go() {
		long nextSeed = AccessSequence.SEED0;
		int nextInitPass = 1;
		Stats timeStats = new Stats();
		CacheStats cacheStats = new CacheStats();
		for (int pass = 1; pass <= PASSES; pass++) {
			boolean newSeed = false;
			if (pass >=  nextInitPass) {
				newSeed = true;
				init(nextSeed++);
				nextInitPass = pass + PASSES_PER_SEED;
			}
			System.out.printf(Locale.US, "PASS #%2d: ", pass);
			double time = timePass();
			System.out.printf(Locale.US, "%.3f ns per item", time);
			if (pass >= STABLE_PASS) {
				timeStats.add(time);
				System.out.print(", avg " + timeStats);
			} else
				 nextInitPass++;
			System.out.printf(Locale.US, " (checksum %d)%n", lastCheckSum);
			if (newSeed)
				impl.collectStats(seq.access, cacheStats);
		}
		String summary = String.format(Locale.US, "%-20s %2d : %7.3f +- %7.3f with %s (checksum %d) %s",
			impl.describe(), timeStats.n(), timeStats.mean(), timeStats.dev(), seq,
			lastCheckSum, cacheStats);
		System.out.println(summary);
		log.println(summary);
	}

	private void init(long seed) {
		System.out.println("Creating access sequence with seed " + seed + " ...");
		seq = new AccessSequence(seed);
		System.out.println("Created access sequence with seed " + seq);
		System.out.println("Initializing " + implClassName + " ...");
		impl = createImpl();
		/*
		 * Note: Regular implementations do not use 'seq.access' to initialize themselves.
		 * They just cache all 'seq.orders'.
		 * 'seq.access' is only used by a base-line 'OrderList' implementation.
		 */
		impl.init(seq.orders, seq.access);
		System.out.println("Initialized with " + impl.size() + " objects " + impl.describe());
	}

	private double timePass() {
		long time = System.nanoTime();
		lastCheckSum = accessOnce();
		return ((double)(System.nanoTime() - time)) / seq.access.length;
	}

	private int accessOnce() {
		int checkSum = 0;
		for (long id : seq.access)
			checkSum += impl.getById(id).getCheck();
		return checkSum;
	}
}
