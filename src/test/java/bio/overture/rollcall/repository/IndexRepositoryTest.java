/*
 * Copyright (c) 2018. The Ontario Institute for Cancer Research. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package bio.overture.rollcall.repository;

import lombok.SneakyThrows;
import lombok.val;
import org.apache.http.HttpHost;
import org.assertj.core.util.Lists;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.cluster.metadata.AliasMetaData;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class IndexRepositoryTest {

    @ClassRule
    public static ElasticsearchContainer esContainer = new ElasticsearchContainer(
            "docker.elastic.co/elasticsearch/elasticsearch:6.3.2"
    );

    private static final String INDEX1 = "file_centric_sd_ygva0e1c_re_foobar";
    private static final String INDEX2 = "file_centric_sd_preasa7s_re_foobar";
    private static final String INDEX3 = "file_centric_sd_46sk55a3_re_foobar";

    private RestHighLevelClient client;
    private IndexRepository repository;

    @Before
    @SneakyThrows
    public void setUp() {

        client = new RestHighLevelClient(
                RestClient.builder(HttpHost.create(esContainer.getHttpHostAddress())));
        repository = new IndexRepository(client);

        client.indices().create(new CreateIndexRequest(INDEX1));
        client.indices().create(new CreateIndexRequest(INDEX2));
        client.indices().create(new CreateIndexRequest(INDEX3));
        client.indices().create(new CreateIndexRequest("badindex"));
        TimeUnit.SECONDS.sleep(1);
    }

    @After
    @SneakyThrows
    public void tearDown() {
        client.indices().delete(new DeleteIndexRequest(INDEX1));
        client.indices().delete(new DeleteIndexRequest(INDEX2));
        client.indices().delete(new DeleteIndexRequest(INDEX3));
        client.indices().delete(new DeleteIndexRequest("badindex"));
    }

    @Test
    @SneakyThrows
    public void getStateTestNoAlias() {
        repository.getAliasState().values().iterator().forEachRemaining(i -> assertThat(i).isEmpty());
    }

    @Test
    @SneakyThrows
    public void releaseAndRemoveTest() {
        val list = Lists.list(INDEX1, INDEX2, INDEX3);

        val added = repository.addAlias("file_centric", list);

        assertThat(added).isTrue();

        val state = repository.getAliasState();
        list.forEach(index -> {
            Set<AliasMetaData> aliasMetaData = state.get(index);
            val indexState = new ArrayList<>(aliasMetaData);
            assertThat(indexState).isNotEmpty();
            assertThat(indexState.get(0).alias()).isEqualTo("file_centric");
        });

        val removed = repository.removeAlias("file_centric", Lists.list(INDEX1, INDEX2, INDEX3));
        assertThat(removed).isTrue();
        repository.getAliasState().values().iterator().forEachRemaining(i -> assertThat(i).isEmpty());
    }

}