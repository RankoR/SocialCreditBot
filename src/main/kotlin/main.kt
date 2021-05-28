import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.User
import java.io.File
import java.io.FileInputStream
import java.util.Properties
import kotlin.math.absoluteValue

/**
 * @param args: arrayOf("<properties_file_path>", "<db_path>")
 */
fun main(args: Array<String>) {
    val ratingsRepository: RatingRepository = RatingRepositoryImpl(args[1])

    val bot = bot {
        token = getBotToken(args[0])

        dispatch {
            command(COMMAND_MY_RATING) {
                message.from?.let { user ->
                    val info = ratingsRepository.getRating(user.id)
                    val userSocialCredit = info?.rating ?: 0L

                    bot.sendMessage(
                        chatId = ChatId.fromId(message.chat.id),
                        text = "Товарищ @${user.username}, партия сообщить, что твой социальный рейтинг составлять $userSocialCredit"
                    )
                }
            }

            command(COMMAND_RATING) {
                val ratings = ratingsRepository
                    .getRatings()
                    .associate { info ->
                        info.username to info.rating
                    }

                val stringBuilder = StringBuilder().apply {
                    append("⚡ Товарищ, слушай внимательно великий лидер Xi!\n")
                    append("\uD83D\uDCC8 Партия публиковать списки социальный рейтинг:\n\n")
                }

                ratings.forEach { (username, credit) ->
                    if (credit > 0) {
                        stringBuilder.append("\uD83D\uDC4D Партия гордится товарищ @$username с рейтинг $credit\n")
                    } else {
                        stringBuilder.append("\uD83D\uDC4E Ну и ну! Товарищ @$username разочаровывай партия своим рейтинг $credit\n")
                    }
                }

                bot.sendMessage(
                    chatId = ChatId.fromId(message.chat.id),
                    text = stringBuilder.toString()
                )
            }

            message {
                if (message.sticker == null || message.replyToMessage?.from == null) {
                    return@message
                }

                if (message.from?.id == message.replyToMessage?.from?.id) {
                    bot.sendMessage(
                        chatId = ChatId.fromId(message.chat.id),
                        text = "\uD83D\uDEAB Партия запрещать изменять свой рейтинг. Великий лидер Xi есть следить за тобой!"
                    )

                    return@message
                }

                if (message.replyToMessage?.from?.username == "CCPSocialCreditBot") { // Dirty hack, IDK how to get self ID
                    bot.sendMessage(
                        chatId = ChatId.fromId(message.chat.id),
                        text = "\uD83D\uDEAB Простой товарищ не может изменять рейтинг великий партия! Великий лидер Xi есть следить за тобой!"
                    )

                    return@message
                }

                val socialCreditChange = message.getSocialCreditChange() ?: return@message
                val socialCreditChangeText = if (socialCreditChange > 0) {
                    "Плюс ${socialCreditChange.absoluteValue} социальный рейтинг. Партия горится тобой \uD83D\uDC4D"
                } else {
                    "Минус ${socialCreditChange.absoluteValue} социальный рейтинг. Ты разочаровываешь партию \uD83D\uDE1E"
                }

                message.replyToMessage?.from?.let { user ->
                    val previousCredit = ratingsRepository.getRating(user.id)?.rating ?: 0L
                    val info = ratingsRepository.changeRating(user.id, user.username ?: "-", socialCreditChange)

                    bot.sendMessage(
                        chatId = ChatId.fromId(message.chat.id),
                        text = "@${user.username} $socialCreditChangeText\nТекущий социальный рейтинг: ${info.rating}"
                    )

                    bot.sendToUyghurCampIfNeeded(
                        previousCredit = previousCredit,
                        currentCredit = info.rating,
                        chatId = message.chat.id,
                        user = user
                    )
                }
            }
        }
    }
    bot.startPolling()
}

private fun getBotToken(propertiesFilePath: String): String {
    return Properties()
        .apply {
            FileInputStream(File(propertiesFilePath)).use(::load)
        }
        .getProperty(PROPERTY_BOT_TOKEN)
        ?: throw IllegalStateException("Property named \"$PROPERTY_BOT_TOKEN\" not found in $propertiesFilePath")
}

// https://www.aspi.org.au/report/uyghurs-sale
private fun Bot.sendToUyghurCampIfNeeded(previousCredit: Long, currentCredit: Long, chatId: Long, user: User) {
    if (previousCredit >= 0L && currentCredit < 0L) {
        val job = uyghurCampJobs.random()
        val text = "\uD83C\uDF34 Партия отправлять товарищ @${user.username} в санаторий для уйгур $job. Партия заботься о простой товарищ! \uD83D\uDC6E️"

        sendMessage(
            chatId = ChatId.fromId(chatId),
            text = text
        )
    } else if (previousCredit < 0L && currentCredit >= 0L) {
        val text = "\uD83C\uDFE1 Партия возвращать товарищ @${user.username} из санаторий для уйгур. Впредь будь аккуратный! \uD83D\uDC6E️"

        sendMessage(
            chatId = ChatId.fromId(chatId),
            text = text
        )
    }
}

private fun Message.getSocialCreditChange(): Long? {
    return when {
        plusSocialCreditStickers.contains(sticker?.fileUniqueId) -> DEFAULT_PLUS_CREDIT
        minusSocialCreditStickers.contains(sticker?.fileUniqueId) -> DEFAULT_MINUS_CREDIT
        else -> null
    }
}
