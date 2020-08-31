# TdPmBot

全功能 Telegram 私聊机器人以及创建器.

## 安装

#### 依赖 (Linux)

```shell script
apt install -y openssl git zlib1g libc++-dev default-jdk
```

注： 仅支持 `amd64, i386, arm64`, 否则需自行编译 [LibTDJni](https://github.com/TdBotProject/LibTDJni) 放置在 libs 文件夹下.  

如遇到找不到 LIBC 库, 请更新系统或编译安装.

### 依赖 (Windows)

需要安装 [Git for Windows](https://gitforwindows.org/) 与 [VC++ 2015](https://github.com/abbodi1406/vcredist/releasesvc) 与 OpenJDK 11

您也可使用 `vcpkg` 编译安装 `openssl` 与 `zlib`

## 配置

复制 `_pm.conf` 到 `pm.conf`

```
BOT_LANG: 工作语言, 暂仅支持 `zh_CN`, `zh_TW`, `en_US`.
BOT_TOKEN: 机器人令牌.
PUBLIC: 是否以公开模式运行.
ADMIN: 管理员ID.
LOG_LEVEL: 日志等级, 默认为 INFO.
```

### 其他
```
SERVICE_NAME: systemd 服务名称, 默认 `td-pm`, 修改如果您需要多个实例.
MVN_ARGS: Maven 编译参数.
JAVA_ARGS: JVM 启动参数.
ARGS: 启动参数.
```

若您不知道账号ID, 可留空, 启动后发送 /id 到机器人获取.

`BINLOG`: 指定 UserBot 的 binlog, 跳过交互式认证. ( 仅用于环境变量 )

## 管理

```shell script

./bot.sh run # 编译安装并进入交互式认证  
./bot.sh init # 注册 systemd 服务  
./bot.sh <start/stop/restart> # 启动停止  
./bot.sh <enable/disable> # 启用禁用 (开机启动)  
./bot.sh rebuild # 重新编译  
./bot.sh update # 更新  
./bot.sh upgrade # 更新并重启服务  
./bot.sh log # 实时日志  
./bot.sh logs # 所有日志

echo "alias pm='bash $PWD/bot.sh'" >> $HOME/.bashrc
source $HOME/.bashrc

# 注册 bot.sh 的命令别名 ( pm )
```

## 迁移

`./bot.sh run --backup [fileName 可选]`

备份所有迁移需要的文件到 tar.xz 包, 解压即可覆盖数据.

注: 建议迁移后手动运行 `/gc` 命令拉取消息记录.

## Docker

```
docker run -d --name td-pm \
  -v <数据目录>:/root/data \
  -e BOT_TOKEN=<机器人令牌> \
  -e ADMIN=<管理员ID> \
  -e PUBLIC=true \
  docker.pkg.github.com/tdbotproject/tdpmbot/td-pm

docker logs td-pm -f -t
```

注: 需要使用 Github 账号登录 

`docker login docker.pkg.github.com -u <您的 Github 用户名> -p <您的 Github AccessToken>`

## 公开实例

[@TdPmBot](https://t.me/TdPmBot)

## 使用

如需帮助，请通过 @TdBotProject 的讨论群组与我们联系.

### 创建新机器人

使用 `/new_bot` 命令进入创建步进程序, 输入完后根据提示发送 [Bot Token](https://core.telegram.org/bots#creating-a-new-bot">) 到机器人即可完成创建, 

您也可以使用该 Token 作为命令参数传入直接创建 ( `/new_bot <BotToken>` ).

创建完成后您需要根据提示启动该机器人并保持不禁用, 否则将无法收到消息.

### 编辑机器人

使用 `/my_bots` 命令得到机器人菜单, 选择要设置的机器人后将得到一个管理菜单.

#### 欢迎消息

即对机器人发送 /start 时回复的消息.

点击 `编辑` 按钮开始设置, 因为消息跨机器人无法转存, 所以您需要转到对应机器人进行设置.  
点击 `重置` 按钮重置回默认欢迎消息.

#### 接入群组

所有消息将被发往目标群组而不是您的私聊.

`仅管理员可操作`: 默认所有群组成员可操作, 开启此项以禁止非管理员操作机器人.

`暂停接入`: 暂停接入到群组, 收到新消息时机器人无法访问接入的群组时也会触发此项.

#### 行为选项

`保留提示`: 不要自动删除操作提示消息.

`双向同步`: 直接复制对方的消息, 而不是每次都转发, 并同步对方的编辑, 删除操作.

`保持回复`: 没有进入 ( `/join` ) 对应会话的情况下保持对消息的回复.

`忽略删除`: 不要同步本方的消息删除, 当同时开启 `保留提示` 时在提示中增加一个删除该消息的按钮.

#### 命令管理

您可以为机器人添加命令, 并为每个命令设置不同的消息内容, 并接收到对方消息所回复之命令.

也可以通过链接 ( start payload, 链接可以在命令设置中找到 ), 点击效果同打开bot并发送命令.
     
格式为 `https://t.me/<botUserName>?start=<command>` (参见 https://core.telegram.org/bots#deep-linking ).

### PM 操作

#### 提示消息

当客人发送消息到机器人, 机器人会为每人每 5 条消息发送一条提示消息给您 ( 包括用户ID 与 引用 ).

`回复这条消息`: 消息将被直接发送给客人.

#### 回复消息

客人的消息将被转发至主人或接入的群组 (如果有设置).

如果启用了 `双向同步`, 此处将直接发送对方的消息的复制, 而不是每次转发消息, 否则如果对方回复的消息存在, 将再发送一条提示消息回复对应的消息.

对客人的消息的可用操作:

`回复这条消息`: 消息将被直接发送给客人或回复对应消息.  

回复对方的消息直接发送消息的复制给客人, 如果没有进入对应的会话, 将不会 `回复` 对应消息, 除非您开启了 `保持回复`.

#### 持续对话

对 `提示消息 / 您发送或收到的消息` 回复 `/join` ( 也可以使用 `对方用户名 / ID / 引用` 作为参数).

进入该对话后所有消息将被 `发送/回复` 到目标对话.

注: 如果接入到了群组, 请确保机器人有访问消息权限 ( `BotFather -> /setprivacy -> Disable` ), 否则无法收到此类与机器人无关的消息.

#### 屏蔽用户

命令为 `/block` 或别名 `/ban`, 用法同上.

屏蔽后会忽略对方发送的所有消息, 使用 `/unblock` 或别名 `/unban` 取消屏蔽, 另: 无法屏蔽自己.

#### 撤回所有消息

命令为 `/recall`, 用法同上.

使用后删除有记录的双方所有消息, 并删除记录, 所以配合屏蔽使用时请先屏蔽.