# Imagem da API.
#
# Existe para o caso de o build sair por Dockerfile. Se a infra optar por
# Source-to-Image, o BuildConfig substitui este arquivo — mas as decisoes
# comentadas abaixo continuam valendo, porque sao do OpenShift, nao do
# Dockerfile.
#
# Bases alinhadas com o catalogo de imagens usado no cluster do tribunal.

# ---------- build ----------
FROM registry.access.redhat.com/ubi9/openjdk-21:latest AS build

WORKDIR /build

# As dependencias mudam muito menos que o codigo. Copiar so o pom primeiro faz
# o Maven baixar tudo numa camada propria, reaproveitada enquanto o pom nao
# mudar — o que corta minutos de cada build seguinte.
COPY --chown=185:0 pom.xml .
RUN mvn -B -q dependency:go-offline

COPY --chown=185:0 src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- runtime ----------
FROM registry.access.redhat.com/ubi9/openjdk-21-runtime:latest

ENV TZ=America/Sao_Paulo \
    LANG=pt_BR.UTF-8

# O OpenShift ignora o USER do Dockerfile e roda com um UID aleatorio, sempre
# do grupo 0. Por isso a permissao e dada ao GRUPO, nao ao usuario: sem o
# chgrp 0 + g+rwX, qualquer escrita falha com "permission denied" e o pod entra
# em laco de reinicio.
#
# Este sistema nao grava em disco (as artes ficam no banco), mas o diretorio de
# trabalho precisa ser gravavel para o dump de erro da JVM e para arquivos
# temporarios do multipart.
USER root
WORKDIR /app
COPY --from=build /build/target/*.jar /app/app.jar
RUN chgrp -R 0 /app && chmod -R g+rwX /app
USER 185

# MaxRAMPercentage em vez de -Xmx: assim a heap acompanha o limit do cgroup
# definido no Deployment, sem precisar editar a imagem quando o limite mudar.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Dfile.encoding=UTF-8 -Duser.timezone=America/Sao_Paulo"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
