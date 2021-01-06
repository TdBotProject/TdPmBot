package io.nekohasekai.pm.manage

import com.esotericsoftware.kryo.KryoException
import io.nekohasekai.ktlib.compress.xz
import io.nekohasekai.ktlib.core.byteBuffer
import io.nekohasekai.ktlib.core.input
import io.nekohasekai.ktlib.core.toByteArray
import io.nekohasekai.ktlib.td.core.TdHandler
import io.nekohasekai.ktlib.td.core.raw.getFile
import io.nekohasekai.ktlib.td.i18n.localeFor
import io.nekohasekai.ktlib.td.utils.*
import io.nekohasekai.pm.*
import io.nekohasekai.pm.instance.BACKUP_VERSION
import td.TdApi

class ImportBot : TdHandler() {

    companion object {
        const val dataId = DATA_IMPORT_BOT
    }

    override fun onLoad() {
        initData(dataId)
    }

    override suspend fun onNewMessage(userId: Int, chatId: Long, message: TdApi.Message) {

        val document = (message.content as? TdApi.MessageDocument)?.takeIf {
            it.document.fileName.toLowerCase().endsWith(".td_pm")
        }?.document ?: return

        val L = localeFor(userId)

        if (!launcher.userAccessible(userId)) {
            if (!global.public) {
                sudo makeMd L.PRIVATE_INSTANCE.input(TdPmBot.repoUrl) syncTo chatId
                return
            } else if (document.document.size > 3 * 1024) {
                sudo makeMd L.IMPORT_TOO_LARGE.input(TdPmBot.repoUrl) syncTo chatId
                return
            }
        }

        sudo make Typing sendTo chatId
        val botFile = download(document.document)
        val input = botFile.inputStream().xz().byteBuffer()

        try {

            val version = input.readInt()

            if (version > BACKUP_VERSION) {
                sudo makeMd L.CLIENT_OUTDATE.input(TdPmBot.repoUrl) syncTo chatId
                return
            }

            val botId = input.readInt()
            val username = input.readString()

            input.close()

            sudo makeMd L.IMPORT_INFO.input(botId, username) withMarkup inlineButton {
                dataLine(L.IMPORT_EXEC, dataId, document.document.id.toByteArray())
            } replyTo message

        } catch (e: Exception) {
            sudo makeMd L.INVALID_FILE.input(e.message ?: e.javaClass.simpleName) syncTo chatId
        }

    }

}