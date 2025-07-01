FROM registry.access.redhat.com/ubi8/openjdk-21:1.21

ENV JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"

COPY build/quarkus-app/lib/ /deployments/lib/
COPY build/quarkus-app/*.jar /deployments/
COPY build/quarkus-app/app/ /deployments/app/
COPY build/quarkus-app/quarkus/ /deployments/quarkus/

EXPOSE 8080 8081 49000

USER 185

ENTRYPOINT [ "/opt/jboss/container/java/run/run-java.sh" ]