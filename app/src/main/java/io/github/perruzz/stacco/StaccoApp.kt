package io.github.perruzz.stacco

import android.app.Application
import android.content.Context
import io.github.perruzz.stacco.data.StaccoRepository
import io.github.perruzz.stacco.notify.AppSync
import io.github.perruzz.stacco.notify.BankAlertWorker
import io.github.perruzz.stacco.notify.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class StaccoApp : Application() {

    /** Lives as long as the process: used for background sync work. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var repository: StaccoRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = StaccoRepository.create(this, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        Notifications.createChannels(this)
        AppSync.start(this, repository, appScope)
        BankAlertWorker.schedule(this)
    }
}

val Context.stacco: StaccoApp get() = applicationContext as StaccoApp
