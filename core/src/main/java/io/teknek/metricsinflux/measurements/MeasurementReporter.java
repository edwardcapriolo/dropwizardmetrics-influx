package io.teknek.metricsinflux.measurements;

import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import io.dropwizard.metrics5.Clock;
import io.dropwizard.metrics5.Counter;
import io.dropwizard.metrics5.Gauge;
import io.dropwizard.metrics5.Histogram;
import io.dropwizard.metrics5.Meter;
import io.dropwizard.metrics5.MetricFilter;
import io.dropwizard.metrics5.MetricName;
import io.dropwizard.metrics5.MetricRegistry;
import io.dropwizard.metrics5.ScheduledReporter;
import io.dropwizard.metrics5.Snapshot;
import io.dropwizard.metrics5.Timer;

import io.teknek.metricsinflux.api.measurements.MetricMeasurementTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MeasurementReporter extends ScheduledReporter{
	private static final Logger LOGGER = LoggerFactory.getLogger(MeasurementReporter.class);
	private static final Pattern SAFE_METRIC_NAME = Pattern.compile("[A-Za-z0-9_./:-]+");

	private final Sender sender;
	private final Clock clock;
	private Map<String, String> baseTags;
	private MetricMeasurementTransformer transformer;

	public MeasurementReporter(Sender sender, MetricRegistry registry, MetricFilter filter, TimeUnit rateUnit, TimeUnit durationUnit, Clock clock, Map<String, String> baseTags, MetricMeasurementTransformer transformer, ScheduledExecutorService executor) {
		super(registry, "measurement-reporter", filter, rateUnit, durationUnit, executor);
		this.baseTags = baseTags;
		this.sender = sender;
		this.clock = clock;
		this.transformer = transformer;
	}

	public MeasurementReporter(Sender sender, MetricRegistry registry, MetricFilter filter, TimeUnit rateUnit, TimeUnit durationUnit, Clock clock, Map<String, String> baseTags, MetricMeasurementTransformer transformer) {
		super(registry, "measurement-reporter", filter, rateUnit, durationUnit);
		this.baseTags = baseTags;
		this.sender = sender;
		this.clock = clock;
		this.transformer = transformer;
	}

	@Override
	public void report(SortedMap<MetricName, Gauge<?>> gauges
			, SortedMap<MetricName, Counter> counters
			, SortedMap<MetricName, Histogram> histograms
			, SortedMap<MetricName, Meter> meters
			, SortedMap<MetricName, Timer> timers) {

		final long timestamp = clock.getTime();

		for (Map.Entry<MetricName, Gauge<?>> entry : gauges.entrySet()) {
			if (!isValidMetricName(entry.getKey())) {
				continue;
			}
			sender.send(fromGauge(entry.getKey().getKey(), entry.getValue(), timestamp));
		}

		for (Map.Entry<MetricName, Counter> entry : counters.entrySet()) {
			if (!isValidMetricName(entry.getKey())) {
				continue;
			}
			sender.send(fromCounter(entry.getKey().getKey(), entry.getValue(), timestamp));
		}

		for (Map.Entry<MetricName, Histogram> entry : histograms.entrySet()) {
			if (!isValidMetricName(entry.getKey())) {
				continue;
			}
			sender.send(fromHistogram(entry.getKey().getKey(), entry.getValue(), timestamp));
		}

		for (Map.Entry<MetricName, Meter> entry : meters.entrySet()) {
			if (!isValidMetricName(entry.getKey())) {
				continue;
			}
			sender.send(fromMeter(entry.getKey().getKey(), entry.getValue(), timestamp));
		}

		for (Map.Entry<MetricName, Timer> entry : timers.entrySet()) {
			if (!isValidMetricName(entry.getKey())) {
				continue;
			}
			sender.send(fromTimer(entry.getKey().getKey(), entry.getValue(), timestamp));
		}

		sender.flush();
	}

	public static boolean isValidMetricName(MetricName metricName) {
		if (metricName == null || metricName.getKey() == null || metricName.getKey().isBlank()) {
			LOGGER.warn("Discarding metric with blank name: {}", metricName);
			return false;
		}
		if (!metricName.getTags().isEmpty()) {
			LOGGER.warn("Discarding metric '{}' because tagged MetricName instances are not supported by this reporter", metricName);
			return false;
		}
		if (!SAFE_METRIC_NAME.matcher(metricName.getKey()).matches()) {
			LOGGER.warn("Discarding metric '{}' because the name contains unsupported line protocol characters", metricName.getKey());
			return false;
		}
		return true;
	}

	private Measure fromTimer(String metricName, Timer t, long timestamp) {
		Snapshot snapshot = t.getSnapshot();

		Map<String, String> tags = new HashMap<String, String>(baseTags);
		tags.putAll(transformer.tags(metricName));

		Measure measure = new Measure(transformer.measurementName(metricName))
				.timestamp(timestamp)
				.addTag(tags)
				.addValue("count", snapshot.size())
				.addValue("min", convertDuration(snapshot.getMin()))
				.addValue("max", convertDuration(snapshot.getMax()))
				.addValue("mean", convertDuration(snapshot.getMean()))
				.addValue("std-dev", convertDuration(snapshot.getStdDev()))
				.addValue("50-percentile", convertDuration(snapshot.getMedian()))
				.addValue("75-percentile", convertDuration(snapshot.get75thPercentile()))
				.addValue("95-percentile", convertDuration(snapshot.get95thPercentile()))
				.addValue("99-percentile", convertDuration(snapshot.get99thPercentile()))
				.addValue("999-percentile", convertDuration(snapshot.get999thPercentile()))
				.addValue("one-minute", convertRate(t.getOneMinuteRate()))
				.addValue("five-minute", convertRate(t.getFiveMinuteRate()))
				.addValue("fifteen-minute", convertRate(t.getFifteenMinuteRate()))
				.addValue("mean-minute", convertRate(t.getMeanRate()))
				.addValue("run-count", t.getCount());

		return measure;
	}

	private Measure fromMeter(String metricName, Meter mt, long timestamp) {
		Map<String, String> tags = new HashMap<String, String>(baseTags);
		tags.putAll(transformer.tags(metricName));

		Measure measure = new Measure(transformer.measurementName(metricName))
				.timestamp(timestamp)
				.addTag(tags)
				.addValue("count", mt.getCount())
				.addValue("one-minute", convertRate(mt.getOneMinuteRate()))
				.addValue("five-minute", convertRate(mt.getFiveMinuteRate()))
				.addValue("fifteen-minute", convertRate(mt.getFifteenMinuteRate()))
				.addValue("mean-minute", convertRate(mt.getMeanRate()));
		return measure;
	}

	private Measure fromHistogram(String metricName, Histogram h, long timestamp) {
		Snapshot snapshot = h.getSnapshot();

		Map<String, String> tags = new HashMap<String, String>(baseTags);
		tags.putAll(transformer.tags(metricName));

		Measure measure = new Measure(transformer.measurementName(metricName))
				.timestamp(timestamp)
				.addTag(tags)
				.addValue("count", snapshot.size())
				.addValue("min", snapshot.getMin())
				.addValue("max", snapshot.getMax())
				.addValue("mean", snapshot.getMean())
				.addValue("std-dev", snapshot.getStdDev())
				.addValue("50-percentile", snapshot.getMedian())
				.addValue("75-percentile", snapshot.get75thPercentile())
				.addValue("95-percentile", snapshot.get95thPercentile())
				.addValue("99-percentile", snapshot.get99thPercentile())
				.addValue("999-percentile", snapshot.get999thPercentile())
				.addValue("run-count", h.getCount());
		return measure;
	}

	private Measure fromCounter(String metricName, Counter c, long timestamp) {
		Map<String, String> tags = new HashMap<String, String>(baseTags);
		tags.putAll(transformer.tags(metricName));

		Measure measure = new Measure(transformer.measurementName(metricName))
				.timestamp(timestamp)
				.addTag(tags)
				.addValue("count", c.getCount());

		return measure;
	}

	@SuppressWarnings("rawtypes")
	private Measure fromGauge(String metricName, Gauge g, long timestamp) {
		Map<String, String> tags = new HashMap<String, String>(baseTags);
		tags.putAll(transformer.tags(metricName));

		Measure measure = new Measure(transformer.measurementName(metricName))
				.timestamp(timestamp)
				.addTag(tags);
		Object o = g.getValue();

		if (o == null) {
			// skip null values
			return null;
		}
		if (o instanceof Long || o instanceof Integer) {
			long value = ((Number)o).longValue();
			measure.addValue("value", value);
		} else if (o instanceof Double) {
			Double d = (Double) o;
			if (d.isInfinite() || d.isNaN()) {
				// skip Infinite & NaN
				return null;
			}
			measure.addValue("value", d.doubleValue());
		} else if (o instanceof Float) {
			Float f = (Float) o;
			if (f.isInfinite() || f.isNaN()) {
				// skip Infinite & NaN
				return null;
			}
			measure.addValue("value", f.floatValue());
		} else {
			String value = ""+o;
			measure.addValue("value", value);
		}

		return measure;
	}
}
