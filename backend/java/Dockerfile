# syntax=docker/dockerfile:1
# El contexto de build es la RAIZ del repo (necesita database/migrations):
#   docker build -f backend/java/Dockerfile -t retail-store-api-java .

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Maven no reintenta una descarga cortada: aborta el build entero con
# "Premature end of Content-Length delimited message body". Con una conexion
# regular eso convierte cada despliegue en una tirada de dados.
ENV MAVEN_OPTS="-Dmaven.wagon.http.retryHandler.count=5 \
                -Dmaven.wagon.httpconnectionManager.ttlSeconds=60 \
                -Dmaven.wagon.rto=120000"

# Se conserva el anidamiento del repositorio: el pom localiza las migraciones
# con ${project.basedir}/../../database/migrations y las mete en el jar como
# recurso (db/migration). Aplanar esto rompe Flyway de una forma que no se ve
# hasta que la aplicacion arranca y no encuentra ninguna migracion.
COPY backend/java/pom.xml backend/java/pom.xml
COPY database/migrations   database/migrations
COPY backend/java/src      backend/java/src

# El repositorio de Maven va en una cache de BuildKit y no en una capa: se
# reutiliza entre despliegues aunque cambie el pom, y no engorda la imagen.
#
# Aqui habia antes un `dependency:go-offline` en una capa propia para cachear
# las dependencias. Se quito: ese objetivo ignora las <exclusions> del pom y
# ademas resuelve las dependencias OPCIONALES de cada dependencia. Con
# flyway-core eso significa bajarse el driver de Oracle, Google Cloud Storage,
# gRPC y AWS Secrets Manager —cientos de MB que este proyecto no usa— y otras
# tantas ocasiones de que un corte de red tumbe el despliegue.
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -f backend/java/pom.xml -B -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/backend/java/target/retail-store-api.jar app.jar

# Las imagenes de la semilla. V002__datos.sql inserta filas que apuntan a
# `archivos/semilla/...`, asi que sin estos bytes el catalogo arranca con todas
# las fotos rotas. Van dentro de la imagen y no en un volumen a proposito: son
# parte del proyecto y cambian solo cuando cambia el codigo.
COPY backend/java/datos/archivos/semilla datos/archivos/semilla

# Los dos directorios que si son datos: en produccion se montan volumenes
# encima. Se crean aqui con el dueno correcto porque el proceso corre como
# `app` y no podria crear nada bajo un volumen que Docker hiciera de root.
RUN mkdir -p datos/archivos/productos datos/archivos/chat \
    && chown -R app:app /app

USER app
# JVM acotada para convivir con Next.js en un VPS pequeno
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
