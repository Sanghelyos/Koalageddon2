package acidicoala.koalageddon.steam.di

import acidicoala.koalageddon.steam.domain.use_case.ReloadSteamConfig
import acidicoala.koalageddon.steam.ui.SteamScreenModel
import org.kodein.di.DI
import org.kodein.di.bindProvider

val steamModule = DI.Module(name = "Steam") {
    bindProvider { ReloadSteamConfig(di) }
    bindProvider { SteamScreenModel(di) }
}