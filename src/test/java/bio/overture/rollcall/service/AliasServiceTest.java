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

package bio.overture.rollcall.service;

import bio.overture.rollcall.config.RollcallConfig;
import bio.overture.rollcall.exception.ReleaseIntegrityException;
import bio.overture.rollcall.model.AliasRequest;
import bio.overture.rollcall.repository.IndexRepository;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.http.HttpHost;
import org.assertj.core.util.Lists;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;

import org.elasticsearch.cluster.metadata.AliasMetaData;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AliasServiceTest {

    @ClassRule
    public static ElasticsearchContainer esContainer = new ElasticsearchContainer(
            "docker.elastic.co/elasticsearch/elasticsearch:6.4.3"
    );

    private static final String INDEX1 = "file_centric_sd_ygva0e1c_re_foobar1";
    private static final String INDEX2 = "file_centric_sd_preasa7s_re_foobar1";
    private static final String INDEX3 = "file_centric_sd_preasa7s_re_foobar2";

    private RestHighLevelClient client;
    private IndexRepository repository;
    private AliasService service;

    @Before
    @SneakyThrows
    public void setUp() {
        client = new RestHighLevelClient(
                RestClient.builder(
                        HttpHost.create(esContainer.getHttpHostAddress())));

        repository = new IndexRepository(client);

        val config = new RollcallConfig(Lists.list(new RollcallConfig.ConfiguredAlias("file_centric", "file", "centric")));
        service = new AliasService(config, repository);

        CreateIndexRequest createIndexRequest = new CreateIndexRequest(INDEX1);
        client.indices().create(createIndexRequest);
        client.indices().create(new CreateIndexRequest(INDEX2));
        client.indices().create(new CreateIndexRequest(INDEX3));
        client.indices().create(new CreateIndexRequest("badindex"));
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
    public void getConfiguredTest() {
        val configured = service.getConfigured();
        assertThat(configured).hasSize(1);
    }


    @NotNull
    private static ArrayList<AliasMetaData> listAliases(Map<String, Set<AliasMetaData>> state1, String index1) {
        return new ArrayList<>(state1.get(index1));
    }

    private static AliasMetaData firstAlias(Map<String, Set<AliasMetaData>> state1, String index1) {
        return listAliases(state1, index1).get(0);
    }

    @Test
    public void releaseTest() {
        val request1 = new AliasRequest("file_centric", "RE_foobar1", Lists.list("SD_preasa7s", "sd_ygva0e1c"));
        service.release(request1);
        val state1 = repository.getAliasState();
        assertThat(firstAlias(state1, INDEX1).alias()).isEqualTo("file_centric");
        assertThat(firstAlias(state1, INDEX2).alias()).isEqualTo("file_centric");

        val request2 = new AliasRequest("file_centric", "RE_foobar2", Lists.list("SD_preasa7s"));
        service.release(request2);
        val state2 = repository.getAliasState();
        assertThat(firstAlias(state2, INDEX1).alias()).isEqualTo("file_centric");
        assertThat(state2).doesNotContainKey(INDEX2);
        assertThat(firstAlias(state2, INDEX3).alias()).isEqualTo("file_centric");
    }


    @Test
    public void testReleaseNonDestructiveFailurePreFlight() {
        val request1 = new AliasRequest("file_centric", "RE_foobar1", Lists.list("SD_preasa7s", "sd_ygva0e1c"));
        service.release(request1);
        val state1 = repository.getAliasState();
        assertThat(firstAlias(state1, INDEX1).alias()).isEqualTo("file_centric");
        assertThat(firstAlias(state1, INDEX2).alias()).isEqualTo("file_centric");

        val badRequest = new AliasRequest("file_centric", "THIS_RELEASE_DONT_EXIST", Lists.list("SD_preasa7s", "sd_ygva0e1c"));
        assertThatThrownBy(() -> service.release(badRequest)).isInstanceOf(ReleaseIntegrityException.class);

        // Should not have changed.
        val state2 = repository.getAliasState();
        assertThat(firstAlias(state2, INDEX1).alias()).isEqualTo("file_centric");
        assertThat(firstAlias(state2, INDEX2).alias()).isEqualTo("file_centric");
    }

}