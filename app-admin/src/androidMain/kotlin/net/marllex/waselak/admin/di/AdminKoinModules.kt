package net.marllex.waselak.admin.di

import net.marllex.waselak.admin.session.AdminSessionManager
import net.marllex.waselak.admin.session.AndroidAdminSessionManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private val androidAdminModule = module {
    single<AdminSessionManager> { AndroidAdminSessionManager(androidContext()) }
}

fun adminKoinModules() = listOf(androidAdminModule, adminModule)
