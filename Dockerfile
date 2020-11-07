FROM openjdk:8-jre-alpine3.9
#EVN vars
ENV MODE development

RUN mkdir -p /app
COPY ./dist /app/dist
COPY ./conf /app/conf
COPY ./cmd /app/cmd
COPY ./runservice /app/runservice
COPY ./entrypoint.sh /app/entrypoint.sh

WORKDIR /app
# set the startup command to execute the jar
VOLUME ["/app/conf"]
VOLUME ["/app/logs"]
EXPOSE 8580
EXPOSE 8589

RUN chmod +x ./entrypoint.sh
ENTRYPOINT ["./entrypoint.sh"]