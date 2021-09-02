package com.jaredloomis.silk

import com.jaredloomis.silk.db.dbModelModule
import com.jaredloomis.silk.db.tableModule
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

val di = DI {
  import(dbModelModule)
  import(tableModule)

  bind<ExecutorService>() with singleton { Executors.newCachedThreadPool() }
}