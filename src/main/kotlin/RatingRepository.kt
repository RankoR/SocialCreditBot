import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.transaction

data class UserRatingInfo(
    val userId: Long,
    val username: String,
    val rating: Long
)

object UsersRatings : LongIdTable() {
    val userId = long("userId").uniqueIndex()
    val username = varchar("username", length = 256).index()
    val rating = long("rating").index()
}

class UserRating(id: EntityID<Long>) : LongEntity(id) {
    var userId by UsersRatings.userId
    var username by UsersRatings.username
    var rating by UsersRatings.rating

    companion object : LongEntityClass<UserRating>(UsersRatings)
}

interface RatingRepository {

    fun changeRating(userId: Long, username: String, ratingChange: Long): UserRatingInfo
    fun getRating(userId: Long): UserRatingInfo?
    fun getRatings(): List<UserRatingInfo>
}

internal class RatingRepositoryImpl(
    dbPath: String
) : RatingRepository {

    init {
        Database.connect("jdbc:sqlite:$dbPath")

        transaction {
            addLogger(StdOutSqlLogger)
            SchemaUtils.create(UsersRatings)
        }
    }

    override fun changeRating(userId: Long, username: String, ratingChange: Long): UserRatingInfo {
        return transaction {
            val userRating = UserRating
                .find { UsersRatings.userId eq userId }
                .singleOrNull()
                ?.apply {
                    this.username = username
                    this.rating += ratingChange
                }
                ?: UserRating.new {
                    this.userId = userId
                    this.username = username
                    this.rating = ratingChange
                }

            userRating.toInfo().also {
                println("User rating updated: $it")
            }
        }
    }

    override fun getRating(userId: Long): UserRatingInfo? {
        return transaction {
            UserRating
                .find { UsersRatings.userId eq userId }
                .singleOrNull()
                ?.toInfo()
        }
    }

    override fun getRatings(): List<UserRatingInfo> {
        return transaction {
            UserRating
                .all()
                .limit(RATINGS_SELECTION_LIMIT)
                .map { it.toInfo() }
                .sortedByDescending { it.rating }
        }
    }

    private fun UserRating.toInfo(): UserRatingInfo {
        return UserRatingInfo(
            userId = userId,
            username = username,
            rating = rating
        )
    }

    private companion object {
        private const val RATINGS_SELECTION_LIMIT = 50
    }
}