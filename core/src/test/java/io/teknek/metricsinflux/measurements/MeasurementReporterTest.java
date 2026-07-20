package io.teknek.metricsinflux.measurements;

import io.dropwizard.metrics5.*;
import io.dropwizard.metrics5.Timer.Context;
import io.teknek.metricsinflux.SortedMaps;
import io.teknek.metricsinflux.api.measurements.MetricMeasurementTransformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static io.teknek.metricsinflux.SortedMaps.singleton;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;

public class MeasurementReporterTest {
	private ListInlinerSender sender;
	private MetricRegistry registry;
	private MeasurementReporter reporter;

	@BeforeEach
	public void init() {
		sender = new ListInlinerSender(100);
		registry = new MetricRegistry();
		reporter = new MeasurementReporter(sender, registry, null, TimeUnit.SECONDS, TimeUnit.MILLISECONDS, Clock.defaultClock(), Collections.<String, String>emptyMap(), MetricMeasurementTransformer.NOOP);
	}

	@Test
	public void reportingOneCounterGeneratesOneLine() {
		assertThat(sender.getFrames().size(), is(0));

		// Let's test with one counter
		String counterName = "c";
		Counter c = registry.counter(counterName);
		c.inc();
		reporter.report(SortedMaps.<MetricName, Gauge<?>>empty(), singleton(MetricName.build(counterName), c), SortedMaps.<MetricName, Histogram>empty(), SortedMaps.<MetricName, Meter>empty(), SortedMaps.<MetricName, Timer>empty());
		assertThat(sender.getFrames().size(), is(1));
		assertThat(sender.getFrames().get(0), startsWith(counterName));
		assertThat(sender.getFrames().get(0), containsString("count=1i"));
	}

	@Test
	public void reportingInvalidMetricNameDiscardsMetric() {
		Counter counter = new Counter();
		counter.inc();

		reporter.report(SortedMaps.<MetricName, Gauge<?>>empty(), singleton(MetricName.build("bad metric"), counter), SortedMaps.<MetricName, Histogram>empty(), SortedMaps.<MetricName, Meter>empty(), SortedMaps.<MetricName, Timer>empty());

		assertThat(sender.getFrames().size(), is(0));
	}

	@Test
	public void reportingTaggedMetricNameDiscardsMetric() {
		Counter counter = new Counter();
		counter.inc();

		reporter.report(SortedMaps.<MetricName, Gauge<?>>empty(), singleton(MetricName.build("counter").tagged("tag", "value"), counter), SortedMaps.<MetricName, Histogram>empty(), SortedMaps.<MetricName, Meter>empty(), SortedMaps.<MetricName, Timer>empty());

		assertThat(sender.getFrames().size(), is(0));
	}

	@Test
	public void reportingOneGaugeGeneratesOneLine() {
		assertThat(sender.getFrames().size(), is(0));

		// Let's test with one counter
		String gaugeName = "g";
		Gauge<Integer> g = new Gauge<Integer>() {
			@Override
			public Integer getValue() {
				return 0;
			}
		};

		reporter.report(SortedMaps.<MetricName, Gauge<?>>singleton(MetricName.build(gaugeName), g), SortedMaps.<MetricName, Counter>empty(), SortedMaps.<MetricName, Histogram>empty(), SortedMaps.<MetricName, Meter>empty(), SortedMaps.<MetricName, Timer>empty());
		assertThat(sender.getFrames().size(), is(1));
		assertThat(sender.getFrames().get(0), startsWith(gaugeName));
		assertThat(sender.getFrames().get(0), containsString("value=0i"));
	}

	@Test
	public void reportingOneMeterGeneratesOneLine() {
		assertThat(sender.getFrames().size(), is(0));

		// Let's test with one counter
		String meterName = "m";
		Meter meter = registry.meter(meterName);
		meter.mark();
		reporter.report(SortedMaps.<MetricName, Gauge<?>>empty(), SortedMaps.<MetricName, Counter>empty(), SortedMaps.<MetricName, Histogram>empty(), SortedMaps.singleton(MetricName.build(meterName), meter), SortedMaps.<MetricName, Timer>empty());

		assertThat(sender.getFrames().size(), is(1));
		assertThat(sender.getFrames().get(0), startsWith(meterName));

		assertThat(sender.getFrames().get(0), containsString("count=1i"));
		assertThat(sender.getFrames().get(0), containsString("one-minute="));
		assertThat(sender.getFrames().get(0), containsString("five-minute="));
		assertThat(sender.getFrames().get(0), containsString("fifteen-minute="));
		assertThat(sender.getFrames().get(0), containsString("mean-minute="));
	}

	@Test
	public void reportingOneHistogramGeneratesOneLine() {
		assertThat(sender.getFrames().size(), is(0));

		// Let's test with one counter
		String histogramName = "h";
		Histogram histogram = registry.histogram(histogramName);
		histogram.update(0);
		reporter.report(SortedMaps.<MetricName, Gauge<?>>empty(), SortedMaps.<MetricName, Counter>empty(), SortedMaps.singleton(MetricName.build(histogramName), histogram), SortedMaps.<MetricName, Meter>empty(), SortedMaps.<MetricName, Timer>empty());

		assertThat(sender.getFrames().size(), is(1));
		assertThat(sender.getFrames().get(0), startsWith(histogramName));

		assertThat(sender.getFrames().get(0), containsString("count=1i"));
		assertThat(sender.getFrames().get(0), containsString("min="));
		assertThat(sender.getFrames().get(0), containsString("max="));
		assertThat(sender.getFrames().get(0), containsString("mean="));
		assertThat(sender.getFrames().get(0), containsString("std-dev="));
		assertThat(sender.getFrames().get(0), containsString("50-percentile="));
		assertThat(sender.getFrames().get(0), containsString("75-percentile="));
		assertThat(sender.getFrames().get(0), containsString("95-percentile="));
		assertThat(sender.getFrames().get(0), containsString("99-percentile="));
		assertThat(sender.getFrames().get(0), containsString("999-percentile="));
		assertThat(sender.getFrames().get(0), containsString("run-count="));
	}

	@Test
	public void reportingOneTimerGeneratesOneLine() {
		assertThat(sender.getFrames().size(), is(0));

		// Let's test with one counter
		String timerName = "t";
		Timer meter = registry.timer(timerName);
		Context ctx = meter.time();

		try {
			Thread.sleep(20);
		} catch (InterruptedException ignored) {
		}

		ctx.stop();

		reporter.report(SortedMaps.<MetricName, Gauge<?>>empty(), SortedMaps.<MetricName, Counter>empty(), SortedMaps.<MetricName, Histogram>empty(), SortedMaps.<MetricName, Meter>empty(), SortedMaps.singleton(MetricName.build(timerName), meter));


		assertThat(sender.getFrames().size(), is(1));
		assertThat(sender.getFrames().get(0), startsWith(timerName));

		assertThat(sender.getFrames().get(0), containsString("count=1i"));
		assertThat(sender.getFrames().get(0), containsString("one-minute="));
		assertThat(sender.getFrames().get(0), containsString("five-minute="));
		assertThat(sender.getFrames().get(0), containsString("fifteen-minute="));
		assertThat(sender.getFrames().get(0), containsString("mean-minute="));
		assertThat(sender.getFrames().get(0), containsString("min="));
		assertThat(sender.getFrames().get(0), containsString("max="));
		assertThat(sender.getFrames().get(0), containsString("mean="));
		assertThat(sender.getFrames().get(0), containsString("std-dev="));
		assertThat(sender.getFrames().get(0), containsString("50-percentile="));
		assertThat(sender.getFrames().get(0), containsString("75-percentile="));
		assertThat(sender.getFrames().get(0), containsString("95-percentile="));
		assertThat(sender.getFrames().get(0), containsString("99-percentile="));
		assertThat(sender.getFrames().get(0), containsString("999-percentile="));
		assertThat(sender.getFrames().get(0), containsString("run-count="));
	}
}
