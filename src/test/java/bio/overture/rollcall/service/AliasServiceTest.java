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
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AliasServiceTest {

    @ClassRule
    public static ElasticsearchContainer esContainer = new ElasticsearchContainer(
            "docker.elastic.co/elasticsearch/elasticsearch:6.2.0"
    );

    private static final String INDEX1 = "file_centric_sd_ygva0e1c_re_foobar1";
    private static final String INDEX2 = "file_centric_sd_preasa7s_re_foobar1";
    private static final String INDEX3 = "file_centric_sd_preasa7s_re_foobar2";
    private static final String INDEX4 = "participant_centric_sd_ygva0e1c_re_foobar1";
    private static final String INDEX5 = "participant_centric_sd_preasa7s_re_foobar1";
    private static final String INDEX6 = "participant_centric_sd_preasa7s_re_foobar2";

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

        val config = new RollcallConfig(
                Lists.list(
                        new RollcallConfig.ConfiguredAlias("file_centric", "file", "centric"),
                        new RollcallConfig.ConfiguredAlias("participant_centric", "participant", "centric")
                )
        );
        service = new AliasService(config, repository);

        CreateIndexRequest createIndexRequest = new CreateIndexRequest(INDEX1);
        client.indices().create(createIndexRequest);
        client.indices().create(new CreateIndexRequest(INDEX2));
        client.indices().create(new CreateIndexRequest(INDEX3));
        client.indices().create(new CreateIndexRequest(INDEX4));
        client.indices().create(new CreateIndexRequest(INDEX5));
        client.indices().create(new CreateIndexRequest(INDEX6));
        client.indices().create(new CreateIndexRequest("badindex"));
    }

    @After
    @SneakyThrows
    public void tearDown() {
        client.indices().delete(new DeleteIndexRequest(INDEX1));
        client.indices().delete(new DeleteIndexRequest(INDEX2));
        client.indices().delete(new DeleteIndexRequest(INDEX3));
        client.indices().delete(new DeleteIndexRequest("badindex"));
        client.indices().delete(new DeleteIndexRequest(INDEX4));
        client.indices().delete(new DeleteIndexRequest(INDEX5));
        client.indices().delete(new DeleteIndexRequest(INDEX6));
    }

    @Test
    public void getConfiguredTest() {
        val configured = service.getConfigured();
        //participant_centric && file_centric
        assertThat(configured).hasSize(2);
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
        val requestFC1 = new AliasRequest("file_centric", "RE_foobar1", Lists.list("SD_preasa7s", "sd_ygva0e1c"));
        service.release(requestFC1);
        val state1 = repository.getAliasState();
        //file_centric_sd_ygva0e1c_re_foobar1
        //file_centric_sd_preasa7s_re_foobar1
        val result1 = Stream.of(INDEX1, INDEX2).collect(Collectors.toSet());
        assertThat(state1.keySet()).isEqualTo(result1);

        val requestFC2 = new AliasRequest("file_centric", "RE_foobar2", Lists.list("SD_preasa7s"));
        service.release(requestFC2);
        val state2 = repository.getAliasState();
        //file_centric_sd_ygva0e1c_re_foobar1
        //file_centric_sd_preasa7s_re_foobar2
        val result2 = Stream.of(INDEX1, INDEX3).collect(Collectors.toSet());
        assertThat(state2.keySet()).isEqualTo(result2);

        val requestPC1 = new AliasRequest("participant_centric", "RE_foobar1", Lists.list("SD_preasa7s", "sd_ygva0e1c"));
        service.release(requestPC1);
        val state3 = repository.getAliasState();
        //file_centric_sd_ygva0e1c_re_foobar1
        //file_centric_sd_preasa7s_re_foobar2
        //participant_centric_sd_ygva0e1c_re_foobar1
        //participant_centric_sd_preasa7s_re_foobar1
        val result3 = Stream.of(INDEX1, INDEX3, INDEX4, INDEX5).collect(Collectors.toSet());
        assertThat(state3.keySet()).isEqualTo(result3);

        val requestPC2 = new AliasRequest("participant_centric", "RE_foobar2", Lists.list("SD_preasa7s"));
        service.release(requestPC2);
        val state4 = repository.getAliasState();
        //file_centric_sd_ygva0e1c_re_foobar1
        //file_centric_sd_preasa7s_re_foobar2
        //participant_centric_sd_ygva0e1c_re_foobar1
        //participant_centric_sd_preasa7s_re_foobar2
        val result4 = Stream.of(INDEX1, INDEX3, INDEX4, INDEX6).collect(Collectors.toSet());
        assertThat(state4.keySet()).isEqualTo(result4);
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