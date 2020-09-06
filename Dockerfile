FROM docker.pkg.github.com/nekohasekai/ktlib/td-base:latest

WORKDIR /root

ADD bot/target/td-pm-bot.jar .

ENTRYPOINT java -jar td-pm-bot.jar