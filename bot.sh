#!/bin/bash

# --------------------------- #
serviceName="td-pm"
artifact="td-pm"
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

  [ -f "$artifact.jar" ] || bash $0 rebuild

  shift

  java -server -jar $artifact.jar $@

elif [ "$1" == "start" ]; then

  systemctl start $serviceName

  bash $0 log

elif [ "$1" == "restart" ]; then

  systemctl restart $serviceName

  bash $0 log

elif [ "$1" == "force-update" ]; then

  git fetch &>/dev/null
  git reset --hard FETCH_HEAD
  git submodule update --init --force --recursive

  bash $0 rebuild

elif [ "$1" == "rebuild" ]; then

  [ -f "neko/pom.xml" ] || git submodule update --init --force --recursive

  shift

  bash mvnw -T 1C clean package $@ &&
    rm -f $module/target/*shaded.jar $module/target/*proguard_base.jar

  if [ $? -eq 0 ]; then

    cp -f $module/target/$artifact-*.jar $artifact.jar

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

  bash $0 rebuild

  exit $?

elif [ "$1" == "log" ]; then

  journalctl -u $serviceName -f

elif [ "$1" == "logs" ]; then

  shift 1

  journalctl -u $serviceName --no-tail $@

else

  systemctl "$1" $serviceName

fi
