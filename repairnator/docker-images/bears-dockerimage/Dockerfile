FROM maven:3.3.9-jdk-8

ARG HOME=/home/maven/

# Configure Maven User

ENV MAVEN_CONFIG ".m2"

RUN useradd --create-home --shell /bin/bash --user-group maven

WORKDIR $HOME

RUN chown -R maven:maven $HOME
RUN mkdir -p .m2

# Repairnator

LABEL Description="Repairnator Pipeline docker image" Vendor="Spirals" Version="0.0.0"

VOLUME ["/var/log/"]

COPY z3_for_linux $HOME
COPY logback.xml $HOME
COPY version.ini $HOME
COPY configure_git.sh $HOME
COPY build_repairnator4Bears.sh $HOME
COPY pipeline_launcher4Bears.sh $HOME

RUN chmod +x $HOME/*.sh
RUN echo "Europe/Paris" > /etc/timezone 
RUN apt-get update 
RUN apt-get install cloc -y 

RUN $HOME/configure_git.sh 
RUN $HOME/build_repairnator4Bears.sh

USER maven

ENV M2_HOME=$MAVEN_HOME
ENV GITHUB_OAUTH=
ENV REPAIR_MODE=
ENV BUILD_ID=
ENV NEXT_BUILD_ID=
ENV LOG_FILENAME=
ENV LOG_LEVEL=
ENV RUN_ID=
ENV MONGODB_HOST=
ENV MONGODB_NAME=
ENV OUTPUT=
ENV PUSH_URL=
ENV SMTP_SERVER=
ENV NOTIFY_TO=

ENTRYPOINT $HOME/pipeline_launcher4Bears.sh
