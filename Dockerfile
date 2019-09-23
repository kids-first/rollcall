FROM openjdk:12-alpine

ARG MAVEN_VERSION=3.5.4
ARG SHA=ce50b1c91364cb77efe3776f756a6d92b76d9038b0a0782f7d53acf1e997a14d
ARG BASE_URL=https://apache.osuosl.org/maven/maven-3/${MAVEN_VERSION}/binaries

RUN apk add --no-cache curl tar bash \
  && mkdir -p /usr/share/maven /usr/share/maven/ref \
  && curl -fsSL -o /tmp/apache-maven.tar.gz ${BASE_URL}/apache-maven-${MAVEN_VERSION}-bin.tar.gz \
  && echo "${SHA}  /tmp/apache-maven.tar.gz" | sha256sum -c - \
  && tar -xzf /tmp/apache-maven.tar.gz -C /usr/share/maven --strip-components=1 \
  && rm -f /tmp/apache-maven.tar.gz \
  && ln -s /usr/share/maven/bin/mvn /usr/bin/mvn

WORKDIR /usr/src/app

# copy just the pom.xml and install dependencies for caching
COPY pom.xml .
RUN mvn verify clean --fail-never


COPY . .

RUN mkdir -p /srv/rollcall/install \
    && mkdir -p /srv/rollcall/exec \
    && mvn package -Dmaven.test.skip=true \
    && mv /usr/src/app/target/rollcall-*-SNAPSHOT.jar /srv/rollcall/install/ROLLCALL.jar \
    && mv /usr/src/app/src/main/resources/scripts/run.sh /srv/rollcall/exec/run.sh

# setup required environment variables
ENV ROLLCALL_INSTALL_PATH /srv/rollcall

# start ego server
WORKDIR $ROLLCALL_INSTALL_PATH
CMD $ROLLCALL_INSTALL_PATH/exec/run.sh

