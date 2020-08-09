#!/bin/bash

# --------------------------- #
serviceName="td-pm"
artifact="td-pm-bot"
module="bot"
# --------------------------- #

info() { echo "I: $*"; }

error() {

  echo "E: $*"
  exit 1

}

if [ ! "$1" ]; then

  echo "bash $0 [ init | update | run | log | start | stop | ... ]"

  exit

fi

if [ "$1" == "init" ]; then

  echo ">> 写入服务"

  cat >/etc/systemd/system/$serviceName.service <<EOF
[Unit]
Description=Telegram Bot ($serviceName)
After=network.target
Wants=network.target

[Service]
Type=simple
WorkingDirectory=$(readlink -e ./)
ExecStart=/bin/bash $0 run
Restart=on-failure
RestartPreventExitStatus=100

[Install]
WantedBy=multi-user.target
EOF

  systemctl daemon-reload

  echo ">> 写入启动项"

  systemctl enable $serviceName &>/dev/null

  echo "<< 完毕."

  exit

elif [ "$1" == "run" ]; then

  target="$artifact-$(git rev-parse --short HEAD).jar"

#  if [ ! -x "$(find . -maxdepth 1 -name ${artifact}-*))" ]; then
#    for oldTarget in ./${artifact}-*; do
  if [ ! -x "$(find . -maxdepth 1 -name '*.jar'))" ]; then
    for oldTarget in ./*.jar; do
      if [ ! $oldTarget -ef $target ]; then
        rm $oldTarget
      fi
    done
  fi

  [ -f "$target" ] || bash $0 rebuild || exit 1

  shift

  java -jar $target $@

elif [ "$1" == "start" ]; then

  systemctl start $serviceName

  bash $0 log

elif [ "$1" == "restart" ]; then

  systemctl restart $serviceName &

  bash $0 log

elif [ "$1" == "force-update" ]; then

  git fetch &>/dev/null
  git reset --hard FETCH_HEAD
  git submodule update --init --force --recursive

  bash $0 rebuild

elif [ "$1" == "rebuild" ]; then

  [ -f "neko/pom.xml" ] || git submodule update --init --force --recursive

  shift

  target="$artifact-$(git rev-parse --short HEAD).jar"

  bash mvnw -T 1C package $@

  if [ $? -eq 0 ]; then

    cp -f $module/target/$artifact.jar $target

  fi

elif [ "$1" == "update" ]; then

  git fetch &>/dev/null

  if [ "$(git rev-parse HEAD)" = "$(git rev-parse FETCH_HEAD)" ]; then

    echo "<< 没有更新"

    exit 1

  fi

  echo ">> 检出更新 $(git rev-parse FETCH_HEAD)"

  git reset --hard FETCH_HEAD
  git submodule update --init --force --recursive

  shift

  bash $0 rebuild $@

  exit $?

elif [ "$1" == "upgrade" ]; then

  git fetch &>/dev/null

  if [ "$(git rev-parse HEAD)" = "$(git rev-parse FETCH_HEAD)" ]; then

    echo "<< 没有更新"

    exit 1

  fi

  echo ">> 检出更新 $(git rev-parse FETCH_HEAD)"

  git reset --hard FETCH_HEAD
  git submodule update --init --force --recursive

  shift

  bash $0 rebuild $@ && bash $0 restart

elif [ "$1" == "log" ]; then

  journalctl -u $serviceName -o short --no-hostname -f -n 40


elif [ "$1" == "logs" ]; then

  journalctl -u $serviceName -o short --no-hostname --no-tail -e

else

  systemctl "$1" $serviceName

fi
