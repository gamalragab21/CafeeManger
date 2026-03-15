package net.marllex.waselak.admin.di

import net.marllex.waselak.admin.network.AdminApiClient
import net.marllex.waselak.admin.viewmodel.AnalyticsViewModel
import net.marllex.waselak.admin.viewmodel.HomeViewModel
import net.marllex.waselak.admin.viewmodel.LoginViewModel
import net.marllex.waselak.admin.viewmodel.LogsViewModel
import net.marllex.waselak.admin.viewmodel.PlansViewModel
import net.marllex.waselak.admin.viewmodel.SettingsViewModel
import net.marllex.waselak.admin.viewmodel.VendorDetailViewModel
import net.marllex.waselak.admin.viewmodel.AdminNotificationsViewModel
import net.marllex.waselak.admin.viewmodel.VendorsViewModel
import net.marllex.waselak.config.BuildConfig
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val adminModule = module {
    single { AdminApiClient(BuildConfig.BASE_URL, get()) }
    viewModel { LoginViewModel(get()) }
    viewModel { HomeViewModel(get()) }
    viewModel { VendorsViewModel(get()) }
    viewModel { PlansViewModel(get()) }
    viewModel { AnalyticsViewModel(get()) }
    viewModel { LogsViewModel(get()) }
    viewModel { SettingsViewModel(get(), get()) }
    viewModel { VendorDetailViewModel(get()) }
    viewModel { AdminNotificationsViewModel(get()) }
}
