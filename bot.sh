#!/bin/bash

# --------------------------- #
cd $(dirname "$(readlink -f "$0")")
# --------------------------- #
ARTIFACT="td-pm-bot"
MODULE="bot"
SERVICE_NAME="td-pm"
JAVA_ARGS=""
ARGS=""

[ -f "bot.conf" ] && source "bot.conf"
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

  cat >/etc/systemd/system/$SERVICE_NAME.service <<EOF
[Unit]
Description=Telegram Bot ($SERVICE_NAME)
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

  systemctl enable $SERVICE_NAME &>/dev/null

  echo "<< 完毕."

  exit

elif [ "$1" == "run" ]; then

  shift

  EXEC="build/install/main/bin/main"

  [ -x "$EXEC" ] || bash $0 rebuild || exit 1

  if [ ! -x "$*" ]; then

    exec $EXEC $@

  else

    exec $EXEC $ARGS

  fi

elif [ "$1" == "start" ]; then

  systemctl start $SERVICE_NAME

  bash $0 log

elif [ "$1" == "restart" ]; then

  systemctl restart $SERVICE_NAME &

  bash $0 log

elif [ "$1" == "force-update" ]; then

  git fetch &>/dev/null
  git reset --hard FETCH_HEAD

elif [ "$1" == "rebuild" ]; then

  git submodule update --init --force --recursive
  ./gradlew :installDist

elif [ "$1" == "update" ]; then

  git fetch &>/dev/null

  if [ "$(git rev-parse HEAD)" = "$(git rev-parse FETCH_HEAD)" ]; then

    echo "<< 没有更新"

    exit 1

  fi

  echo ">> 检出更新 $(git rev-parse FETCH_HEAD)"

  git reset --hard FETCH_HEAD

  shift

  ./gradlew :clean

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

  shift

  bash $0 rebuild $@ && bash $0 restart

elif [ "$1" == "log" ]; then

  exec journalctl -u $SERVICE_NAME -o short --no-hostname -f -n 40

elif [ "$1" == "logs" ]; then

  exec journalctl -u $SERVICE_NAME -o short --no-hostname --no-tail -e

else

  systemctl "$1" $SERVICE_NAME

fi
