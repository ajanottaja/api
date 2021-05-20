FROM circleci/clojure:openjdk-17-tools-deps-buster-node as build
WORKDIR /app

# Install packaging dependencies
# chown to circleci user to ensure correct permissions while building
COPY --chown=3434:3434 ./uberdeps /app/uberdeps
RUN cd uberdeps \
    && clj -M -e '(println "Install uberdeps deps")' \
    && cd ..

# Install dependencies
# chown to circleci user to ensure correct permissions while building
COPY --chown=3434:3434 ./deps.edn /app/
RUN clj -Mclj -e '(println "Install deps")'

# Copy over rest of the stuff and build a jar
COPY --chown=3434:3434 ./ /app
RUN ./uberdeps/package.sh


FROM openjdk:17-jdk-alpine

ENV AJANOTTAJA_DB_PORT=5432 \
    AJANOTTAJA_DB_NAME=ajanottaja \
    AJANOTTAJA_DB_USER=ajanottaja \
    AJANOTTAJA_SERVER_IP=0.0.0.0 \
    AJANOTTAJA_SERVER_PORT=3000 \
    AJANOTTAJA_ENV=prod

RUN addgroup -S ajanottaja \
    && adduser -S ajanottaja -G ajanottaja

USER ajanottaja

COPY --from=build --chown=ajanottaja:ajanottaja /app/target/ajanottaja.jar /ajanottaja.jar

CMD [ "java", "-Dmalli.registry/type=custom", "-jar",  "/ajanottaja.jar" ]