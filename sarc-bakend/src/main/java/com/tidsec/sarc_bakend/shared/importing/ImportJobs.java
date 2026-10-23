package com.tidsec.sarc_bakend.shared.importing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;
import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.shared.persistence.AfterCommit;

import jakarta.annotation.PreDestroy;

/**
 * Ejecuta importaciones fuera del hilo HTTP (TRD 09.1) en un pool pequeno y acotado. Cada trabajo
 * lleva el usuario que lo pidio, para que la auditoria y la autoria de los registros sean las de
 * esa persona. Se encola tras confirmar la transaccion que creo el lote, asi el trabajo siempre lo
 * encuentra guardado.
 */
@Component
public class ImportJobs {

	private static final Logger log = LoggerFactory.getLogger(ImportJobs.class);
	private static final int THREADS = 2;
	private static final int QUEUE_CAPACITY = 100;

	private final ThreadPoolTaskExecutor pool;
	private final TaskExecutor executor;

	ImportJobs() {
		this.pool = new ThreadPoolTaskExecutor();
		pool.setThreadNamePrefix("import-");
		pool.setCorePoolSize(THREADS);
		pool.setMaxPoolSize(THREADS);
		pool.setQueueCapacity(QUEUE_CAPACITY);
		pool.setWaitForTasksToCompleteOnShutdown(true);
		pool.setAwaitTerminationSeconds(30);
		pool.initialize();
		this.executor = new DelegatingSecurityContextAsyncTaskExecutor(pool);
	}

	/**
	 * Con la cola llena el trabajo se rechaza: el lote queda en proceso y la recuperacion del siguiente
	 * arranque lo marca fallido para reintentarlo.
	 */
	public void submitAfterCommit(Runnable job) {
		AfterCommit.run(() -> {
			try {
				executor.execute(job);
			}
			catch (TaskRejectedException ex) {
				log.error("Cola de importaciones llena; el trabajo no se encolo", ex);
			}
		});
	}

	@PreDestroy
	void shutdown() {
		pool.shutdown();
	}
}
