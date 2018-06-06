/*
 * MIT License
 *
 * Copyright (c) 2017-2018 Frederik Ar. Mikkelsen
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package fredboat.feature.metrics.collectors;

import fredboat.sentinel.Sentinel;
import io.prometheus.client.Collector;
import io.prometheus.client.GaugeMetricFamily;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by napster on 19.04.18.
 */
@Component
public class ShardStatusCollector extends Collector {

    public ShardStatusCollector(Sentinel sentinel) {
        super();
    }

    @Override
    public List<MetricFamilySamples> collect() {

        List<MetricFamilySamples> mfs = new ArrayList<>();
        List<String> noLabels = Collections.emptyList();

        GaugeMetricFamily totalShards = new GaugeMetricFamily("fredboat_shards_total",
                "Total shards managed by this instance", noLabels);
        mfs.add(totalShards);

        GaugeMetricFamily shardsConnected = new GaugeMetricFamily("fredboat_shards_connected",
                "Total shards managed by this instance that are connected", noLabels);
        mfs.add(shardsConnected);

        totalShards.addMetric(noLabels, shardProvider.streamShards().count());
        shardsConnected.addMetric(noLabels, shardProvider.streamShards()
                .filter(shard -> shard.getStatus() == JDA.Status.CONNECTED)
                .count());

        return mfs;
    }
}
