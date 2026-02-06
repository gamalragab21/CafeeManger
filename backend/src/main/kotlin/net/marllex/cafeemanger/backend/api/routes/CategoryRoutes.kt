package net.marllex.cafeemanger.backend.api.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import net.marllex.cafeemanger.backend.api.middleware.currentUser
import net.marllex.cafeemanger.backend.api.middleware.requireRole
import net.marllex.cafeemanger.backend.data.database.CategoriesTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

@Serializable
data class CategoryDto(
    val id: String,
    val vendor_id: String,
    val name: String,
    val display_order: Int = 0,
    val created_at: Long? = null,
    val updated_at: Long? = null
)

@Serializable
data class CreateCategoryDto(val name: String, val display_order: Int = 0)

@Serializable
data class UpdateCategoryDto(val name: String? = null, val display_order: Int? = null)

fun Route.categoryRoutes() {
    route("/api/v1/categories") {
        get {
            val principal = currentUser()
            val categories = transaction {
                CategoriesTable.selectAll()
                    .where { CategoriesTable.vendorId eq UUID.fromString(principal.vendorId) }
                    .orderBy(CategoriesTable.displayOrder)
                    .map { it.toCategoryDto() }
            }
            call.respond(HttpStatusCode.OK, categories)
        }

        post {
            val principal = requireRole("MANAGER")
            val request = call.receive<CreateCategoryDto>()
            require(request.name.isNotBlank()) { "Category name is required" }

            val category = transaction {
                val id = CategoriesTable.insertAndGetId {
                    it[vendorId] = UUID.fromString(principal.vendorId)
                    it[name] = request.name
                    it[displayOrder] = request.display_order
                    it[createdAt] = Clock.System.now()
                    it[updatedAt] = Clock.System.now()
                }
                CategoriesTable.selectAll().where { CategoriesTable.id eq id }.first().toCategoryDto()
            }
            call.respond(HttpStatusCode.Created, category)
        }

        put("/{id}") {
            val principal = requireRole("MANAGER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val request = call.receive<UpdateCategoryDto>()

            val updated = transaction {
                CategoriesTable.update({
                    (CategoriesTable.id eq UUID.fromString(id)) and
                    (CategoriesTable.vendorId eq UUID.fromString(principal.vendorId))
                }) { stmt ->
                    request.name?.let { stmt[name] = it }
                    request.display_order?.let { stmt[displayOrder] = it }
                    stmt[updatedAt] = Clock.System.now()
                }
                CategoriesTable.selectAll().where { CategoriesTable.id eq UUID.fromString(id) }
                    .firstOrNull()?.toCategoryDto() ?: throw NoSuchElementException("Category not found")
            }
            call.respond(HttpStatusCode.OK, updated)
        }

        delete("/{id}") {
            val principal = requireRole("MANAGER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            transaction {
                val deleted = CategoriesTable.deleteWhere {
                    (CategoriesTable.id eq UUID.fromString(id)) and
                    (vendorId eq UUID.fromString(principal.vendorId))
                }
                if (deleted == 0) throw NoSuchElementException("Category not found")
            }
            call.respond(HttpStatusCode.OK, mapOf("success" to true))
        }
    }
}

private fun ResultRow.toCategoryDto() = CategoryDto(
    id = this[CategoriesTable.id].toString(),
    vendor_id = this[CategoriesTable.vendorId].toString(),
    name = this[CategoriesTable.name],
    display_order = this[CategoriesTable.displayOrder],
    created_at = this[CategoriesTable.createdAt].toEpochMilliseconds(),
    updated_at = this[CategoriesTable.updatedAt].toEpochMilliseconds()
)
