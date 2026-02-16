package net.marllex.waselak.core.database.di

import app.cash.sqldelight.ColumnAdapter
import net.marllex.waselak.core.database.*
import net.marllex.waselak.core.database.dao.*
import org.koin.dsl.module

private val intAdapter = object : ColumnAdapter<Int, Long> {
    override fun decode(databaseValue: Long): Int = databaseValue.toInt()
    override fun encode(value: Int): Long = value.toLong()
}

val databaseModule = module {
    single {
        val driverFactory = get<DatabaseDriverFactory>()
        WaselakDatabase(
            driver = driverFactory.createDriver(),
            attendanceAdapter = Attendance.Adapter(
                worked_minutesAdapter = intAdapter,
            ),
            categoriesAdapter = Categories.Adapter(
                display_orderAdapter = intAdapter,
            ),
            order_itemsAdapter = Order_items.Adapter(
                quantityAdapter = intAdapter,
            ),
            salary_paymentsAdapter = Salary_payments.Adapter(
                worked_daysAdapter = intAdapter,
                worked_hoursAdapter = intAdapter,
            ),
            stockAdapter = Stock.Adapter(
                quantityAdapter = intAdapter,
                min_quantityAdapter = intAdapter,
            ),
            stock_transactionsAdapter = Stock_transactions.Adapter(
                quantityAdapter = intAdapter,
                previous_quantityAdapter = intAdapter,
            ),
            tablesAdapter = Tables.Adapter(
                capacityAdapter = intAdapter,
            ),
            workersAdapter = Workers.Adapter(
                qr_code_versionAdapter = intAdapter,
            ),
            customersAdapter = Customers.Adapter(
                order_countAdapter = intAdapter,
            ),
        )
    }

    single { VendorDao(get()) }
    single { UserDao(get()) }
    single { CategoryDao(get()) }
    single { ItemDao(get()) }
    single { TableDao(get()) }
    single { OrderDao(get()) }
    single { StockDao(get()) }
    single { WorkerDao(get()) }
    single { CustomerDao(get()) }
}
