package net.marllex.waselak.backend.api.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import net.marllex.waselak.backend.api.middleware.currentUser
import net.marllex.waselak.backend.api.middleware.requireRole
import net.marllex.waselak.backend.plugins.routeTrace
import net.marllex.waselak.backend.data.database.CategoriesTable
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
            val trace = call.routeTrace()
            trace.step("List categories started")
            val principal = currentUser()
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            val categories = transaction {
                CategoriesTable.selectAll()
                    .where { CategoriesTable.vendorId eq UUID.fromString(principal.vendorId) }
                    .orderBy(CategoriesTable.displayOrder)
                    .map { it.toCategoryDto() }
            }
            trace.step("Categories fetched", mapOf("count" to categories.size.toString()))
            trace.step("List categories completed")
            call.respond(HttpStatusCode.OK, categories)
        }

        post {
            val trace = call.routeTrace()
            trace.step("Create category started")
            val principal = requireRole("MANAGER")
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            val request = call.receive<CreateCategoryDto>()
            trace.step("Request received", mapOf("name" to request.name, "displayOrder" to request.display_order.toString()))
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
            trace.step("Category created", mapOf("categoryId" to category.id, "categoryName" to category.name))
            trace.step("Create category completed")
            call.respond(HttpStatusCode.Created, category)
        }

        put("/{id}") {
            val trace = call.routeTrace()
            trace.step("Update category started")
            val principal = requireRole("MANAGER")
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            trace.step("Category ID param", mapOf("categoryId" to id))
            val request = call.receive<UpdateCategoryDto>()
            trace.step("Update fields received", mapOf(
                "name" to (request.name ?: "null"),
                "displayOrder" to (request.display_order?.toString() ?: "null")
            ))

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
            trace.step("Category updated", mapOf("categoryId" to updated.id, "categoryName" to updated.name))
            trace.step("Update category completed")
            call.respond(HttpStatusCode.OK, updated)
        }

        delete("/{id}") {
            val trace = call.routeTrace()
            trace.step("Delete category started")
            val principal = requireRole("MANAGER")
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            trace.step("Category ID param", mapOf("categoryId" to id))

            transaction {
                val deleted = CategoriesTable.deleteWhere {
                    (CategoriesTable.id eq UUID.fromString(id)) and
                    (vendorId eq UUID.fromString(principal.vendorId))
                }
                if (deleted == 0) throw NoSuchElementException("Category not found")
                trace.step("Category deleted from database", mapOf("rowsAffected" to deleted.toString()))
            }
            trace.step("Delete category completed")
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
