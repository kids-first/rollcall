#!/bin/bash

java -jar $ROLLCALL_INSTALL_PATH/install/ROLLCALL.jar \
        --spring.profiles.active=$ROLLCALL_ACTIVE_PROFILES \
        --server.port=$ROLLCALL_SERVER_PORT \
        --elasticsearch.host=$ROLLCALL_ES_HOST \
        --elasticsearch.port=$ROLLCALL_ES_PORT \
        --elasticsearch.scheme=$ROLLCALL_ES_SCHEME \
        --elasticsearch.cluster-name=$ROLLCALL_ES_CLUSTER \
        --auth0.issuer=$AUTH0_ISSUER \
        --auth0.audience=$AUTH0_AUDIENCE