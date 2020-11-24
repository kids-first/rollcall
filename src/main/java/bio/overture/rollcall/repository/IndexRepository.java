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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.val;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.cluster.metadata.AliasMetaData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest.AliasActions.add;
import static org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest.AliasActions.remove;

@Repository
public class IndexRepository {

    private final RestHighLevelClient client;

    @Autowired
    public IndexRepository(RestHighLevelClient client) {
        this.client = client;
    }

    @SneakyThrows
    public String[] getIndices() {
        InputStream inputStream = this.client.getLowLevelClient()
                .performRequest("GET", "/_cat/indices?h=i")
                .getEntity()
                .getContent();

        return new BufferedReader(new InputStreamReader(inputStream))
                .lines().toArray(String[]::new);

    }

    @SneakyThrows
    public Map<String, Set<AliasMetaData>> getAliasState() {
        InputStream inputStreamFromCatApi = this.client.getLowLevelClient()
                .performRequest("GET", "/_cat/aliases?format=json")
                .getEntity()
                .getContent();

        TypeReference<List<Map<String, String>>> typeRef = new TypeReference<>() {
        };
        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, String>> rawAliasesMetaData = mapper.readValue(inputStreamFromCatApi, typeRef);

        Map<String, Set<AliasMetaData>> indexNameToAliasesMetaData = new HashMap<>();
        for (Map<String, String> rawAliasMetaData : rawAliasesMetaData) {
            String keyIndexName = rawAliasMetaData.get("index");
            AliasMetaData aliasMetaData = AliasMetaData
                    .builder(rawAliasMetaData.get("alias"))
                    .build();
            Set<AliasMetaData> newSetOfAliasesMetaData = Stream.of(aliasMetaData)
                    .collect(Collectors.toCollection(HashSet::new));
            indexNameToAliasesMetaData.put(keyIndexName,newSetOfAliasesMetaData);
        }
        return indexNameToAliasesMetaData;
    }

    @SneakyThrows
    public boolean removeAlias(String alias, List<String> indices) {
        val req = new IndicesAliasesRequest();
        indices.forEach(i -> req.addAliasAction(remove().alias(alias).index(i)));

        if (req.getAliasActions().isEmpty()) {
            return true;
        }
        return client.indices().updateAliases(req).isAcknowledged();
    }

    @SneakyThrows
    public boolean addAlias(String alias, List<String> indices) {
        val req = new IndicesAliasesRequest();
        indices.forEach(i -> req.addAliasAction(add().alias(alias).index(i)));
        return client.indices().updateAliases(req).isAcknowledged();
    }

}
