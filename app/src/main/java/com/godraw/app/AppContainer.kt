package com.godraw.app

import android.content.Context
import com.godraw.app.billing.BillingConfig
import com.godraw.app.billing.BillingRepository
import com.godraw.app.billing.EntitlementManager
import com.godraw.app.billing.ServerPurchaseVerifier
import com.godraw.app.billing.TierSimulatorFactory
import com.godraw.app.billing.VerifiedEntitlementStore
import com.godraw.app.data.db.AppDatabase
import com.godraw.app.data.project.ProjectRepository
import com.godraw.app.data.settings.SettingsRepository
import com.godraw.app.ui.common.ThumbnailCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container shared by the whole app. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val database: AppDatabase = AppDatabase.create(context)
    val settings = SettingsRepository(context)
    val projects = ProjectRepository(context, database)
    val thumbnails = ThumbnailCache()

    val billing = BillingRepository(
        context = context,
        verifier = ServerPurchaseVerifier(BillingConfig.verificationUrl),
        store = VerifiedEntitlementStore(context),
        scope = appScope,
    )
    val entitlements = EntitlementManager(billing, TierSimulatorFactory.create(context), appScope)
}
