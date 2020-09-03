FROM docker.pkg.github.com/tdbotproject/nekolib/td-base:latest

WORKDIR /root

ADD bot/target/td-pm-bot.jar .

ENTRYPOINT java -jar td-pm-bot.jar