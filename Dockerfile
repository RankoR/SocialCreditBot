FROM azul/zulu-openjdk-alpine:15.0.3

# TODO: Replace version
COPY build/libs/SocialCreditBot-*.jar /app.jar

CMD ["/usr/bin/java", "-jar", "/app.jar", "/data/secret.properties", "/data/ratings.db"]