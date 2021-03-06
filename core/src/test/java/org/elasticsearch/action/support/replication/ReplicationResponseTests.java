/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.action.support.replication;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.RoutingMissingException;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.shard.IndexShardRecoveringException;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.test.EqualsHashCodeTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import static org.elasticsearch.test.EqualsHashCodeTestUtils.checkEqualsAndHashCode;
import static org.hamcrest.CoreMatchers.equalTo;

public class ReplicationResponseTests extends ESTestCase {

    public void testShardInfoToString() {
        final int total = 5;
        final int successful = randomIntBetween(1, total);
        final ReplicationResponse.ShardInfo shardInfo = new ReplicationResponse.ShardInfo(total, successful);
        assertThat(
            shardInfo.toString(),
            equalTo(String.format(Locale.ROOT, "ShardInfo{total=5, successful=%d, failures=[]}", successful)));
    }

    public void testShardInfoEqualsAndHashcode() {
        EqualsHashCodeTestUtils.CopyFunction<ReplicationResponse.ShardInfo> copy = shardInfo ->
                new ReplicationResponse.ShardInfo(shardInfo.getTotal(), shardInfo.getSuccessful(), shardInfo.getFailures());

        EqualsHashCodeTestUtils.MutateFunction<ReplicationResponse.ShardInfo> mutate = shardInfo -> {
            List<Supplier<ReplicationResponse.ShardInfo>> mutations = new ArrayList<>();
            mutations.add(() ->
                    new ReplicationResponse.ShardInfo(shardInfo.getTotal() + 1, shardInfo.getSuccessful(), shardInfo.getFailures()));
            mutations.add(() ->
                    new ReplicationResponse.ShardInfo(shardInfo.getTotal(), shardInfo.getSuccessful() + 1, shardInfo.getFailures()));
            mutations.add(() -> {
                int nbFailures = randomIntBetween(1, 5);
                return new ReplicationResponse.ShardInfo(shardInfo.getTotal(), shardInfo.getSuccessful(),  randomFailures(nbFailures));
            });
            return randomFrom(mutations).get();
        };

        checkEqualsAndHashCode(randomShardInfo(), copy, mutate);
    }

    public void testFailureEqualsAndHashcode() {
        EqualsHashCodeTestUtils.CopyFunction<ReplicationResponse.ShardInfo.Failure> copy = failure -> {
            Index index = failure.fullShardId().getIndex();
            ShardId shardId = new ShardId(index.getName(), index.getUUID(), failure.shardId());
            Exception cause = (Exception) failure.getCause();
            return new ReplicationResponse.ShardInfo.Failure(shardId, failure.nodeId(), cause, failure.status(), failure.primary());
        };

        EqualsHashCodeTestUtils.MutateFunction<ReplicationResponse.ShardInfo.Failure> mutate = failure -> {
            List<Supplier<ReplicationResponse.ShardInfo.Failure>> mutations = new ArrayList<>();

            final Index index = failure.fullShardId().getIndex();
            final ShardId randomIndex = new ShardId(randomUnicodeOfCodepointLength(5), index.getUUID(), failure.shardId());
            mutations.add(() -> new ReplicationResponse.ShardInfo.Failure(randomIndex, failure.nodeId(), (Exception) failure.getCause(),
                    failure.status(), failure.primary()));

            final ShardId randomUUID = new ShardId(index.getName(), randomUnicodeOfCodepointLength(5), failure.shardId());
            mutations.add(() -> new ReplicationResponse.ShardInfo.Failure(randomUUID, failure.nodeId(), (Exception) failure.getCause(),
                    failure.status(), failure.primary()));

            final ShardId randomShardId = new ShardId(index.getName(),index.getUUID(), failure.shardId() + randomIntBetween(1, 3));
            mutations.add(() -> new ReplicationResponse.ShardInfo.Failure(randomShardId, failure.nodeId(), (Exception) failure.getCause(),
                    failure.status(), failure.primary()));

            final String randomNode = randomUnicodeOfLength(3);
            mutations.add(() -> new ReplicationResponse.ShardInfo.Failure(failure.fullShardId(), randomNode, (Exception) failure.getCause(),
                    failure.status(), failure.primary()));

            final Exception randomException = randomFrom(new IllegalStateException("a"), new IllegalArgumentException("b"));
            mutations.add(() -> new ReplicationResponse.ShardInfo.Failure(failure.fullShardId(), failure.nodeId(), randomException,
                    failure.status(), failure.primary()));

            final RestStatus randomStatus = randomFrom(RestStatus.values());
            mutations.add(() -> new ReplicationResponse.ShardInfo.Failure(failure.fullShardId(), failure.nodeId(),
                    (Exception) failure.getCause(), randomStatus, failure.primary()));

            final boolean randomPrimary = !failure.primary();
            mutations.add(() -> new ReplicationResponse.ShardInfo.Failure(failure.fullShardId(), failure.nodeId(),
                    (Exception) failure.getCause(), failure.status(), randomPrimary));

            return randomFrom(mutations).get();
        };

        checkEqualsAndHashCode(randomFailure(), copy, mutate);
    }

    private static ReplicationResponse.ShardInfo randomShardInfo() {
        int total = randomIntBetween(1, 10);
        int successful = randomIntBetween(0, total);
        return new ReplicationResponse.ShardInfo(total, successful, randomFailures(Math.max(0, (total - successful))));
    }

    private static ReplicationResponse.ShardInfo.Failure[] randomFailures(int nbFailures) {
        List<ReplicationResponse.ShardInfo.Failure> randomFailures = new ArrayList<>(nbFailures);
        for (int i = 0; i < nbFailures; i++) {
            randomFailures.add(randomFailure());
        }
        return randomFailures.toArray(new ReplicationResponse.ShardInfo.Failure[nbFailures]);
    }

    private static ReplicationResponse.ShardInfo.Failure randomFailure() {
        return new ReplicationResponse.ShardInfo.Failure(
                new ShardId(randomAsciiOfLength(5), randomAsciiOfLength(5), randomIntBetween(0, 5)),
                randomAsciiOfLength(3),
                randomFrom(
                        new IndexShardRecoveringException(new ShardId("_test", "_0", 5)),
                        new ElasticsearchException(new IllegalArgumentException("argument is wrong")),
                        new RoutingMissingException("_test", "_type", "_id")
                ),
                randomFrom(RestStatus.values()),
                randomBoolean()
        );
    }
}
