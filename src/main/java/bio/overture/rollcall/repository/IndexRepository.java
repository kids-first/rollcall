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
        InputStream inputStream = this.client.getLowLevelClient()
                .performRequest("GET", "/_cat/aliases")
                .getEntity()
                .getContent();

        return new BufferedReader(new InputStreamReader(inputStream))
                .lines()
                .map(s -> s.split(" "))
                .collect(
                        Collectors.groupingBy(a -> a[1],
                                Collectors.mapping(a-> AliasMetaData.builder(a[0]).build(), Collectors.toSet())
                        )
                );
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
