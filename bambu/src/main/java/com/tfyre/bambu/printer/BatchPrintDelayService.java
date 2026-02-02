package com.tfyre.bambu.printer;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service for managing batch print jobs with configurable delays between printer uploads
 */
@ApplicationScoped
public class BatchPrintDelayService {
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final AtomicReference<Instant> lastPrinterSentTime = new AtomicReference<>(null);
    private Duration configuredDelay = Duration.ZERO;
    private final AtomicBoolean isBatchAborted = new AtomicBoolean(false);
    private final List<CompletableFuture<Void>> activeBatchJobs = new CopyOnWriteArrayList<>();
    private final List<PrinterJobWrapper> currentBatchQueue = new CopyOnWriteArrayList<>();
    private final AtomicBoolean isBatchRunning = new AtomicBoolean(false);
    private final AtomicInteger jobIdCounter = new AtomicInteger(0);
    
    @Inject
    BatchPrintDelayConfig delayConfig;
    
    /**
     * Wrapper for printer jobs with unique ID for tracking
     */
    private static class PrinterJobWrapper {
        final int id;
        final PrinterJob job;
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        
        PrinterJobWrapper(int id, PrinterJob job) {
            this.id = id;
            this.job = job;
        }
        
        boolean isCancelled() {
            return cancelled.get();
        }
        
        void cancel() {
            cancelled.set(true);
        }
    }
    
    /**
     * Check if we're still in the delay period after last printer was sent
     */
    public boolean isInDelayPeriod() {
        Instant lastSent = lastPrinterSentTime.get();
        if (lastSent == null) {
            return false;
        }
        
        Instant now = Instant.now();
        Instant delayEndTime = lastSent.plus(configuredDelay);
        
        return now.isBefore(delayEndTime);
    }
    
    /**
     * Get remaining delay time in seconds
     */
    public long getRemainingDelaySeconds() {
        Instant lastSent = lastPrinterSentTime.get();
        if (lastSent == null) {
            return 0;
        }
        
        Instant now = Instant.now();
        Instant delayEndTime = lastSent.plus(configuredDelay);
        
        if (now.isBefore(delayEndTime)) {
            return Duration.between(now, delayEndTime).getSeconds();
        }
        
        return 0;
    }
    
    /**
     * Mark that a printer job was sent
     */
    private void markPrinterSent() {
        lastPrinterSentTime.set(Instant.now());
    }
    
	public void abortAllJobs() {
		abortEntireBatch();
	}
    /**
     * Abort all running and queued batch jobs
     */
    public void abortEntireBatch() {
        Log.infof("Aborting ENTIRE batch - Queue size: %d, Active jobs: %d", 
                 currentBatchQueue.size(), activeBatchJobs.size());
        
        // Set abort flag FIRST
        isBatchAborted.set(true);
        
        // Cancel all jobs in queue
        currentBatchQueue.forEach(wrapper -> wrapper.cancel());
        
        // Cancel all active futures
        activeBatchJobs.forEach(future -> {
            if (!future.isDone()) {
                future.cancel(true);
            }
        });
        
        // Clear all lists
        activeBatchJobs.clear();
        currentBatchQueue.clear();
        
        // Reset batch running flag
        isBatchRunning.set(false);
        
        // Reset last sent time
        lastPrinterSentTime.set(null);
        
        // Reset abort flag after a delay
        scheduler.schedule(() -> {
            isBatchAborted.set(false);
            Log.infof("Batch abort completed and flag reset");
        }, 1, TimeUnit.SECONDS);
        
        Log.infof("Entire batch aborted - all queues cleared");
    }
    
    /**
     * Abort specific printers by name
     */
    public int abortSelectedPrinters(List<String> printerNames) {
        if (printerNames == null || printerNames.isEmpty()) {
            return 0;
        }
        
        Log.infof("Aborting selected printers: %s", printerNames);
        
        int abortedCount = 0;
        
        // Mark matching jobs as cancelled
        for (PrinterJobWrapper wrapper : currentBatchQueue) {
            if (printerNames.contains(wrapper.job.getPrinterName())) {
                if (!wrapper.isCancelled()) {
                    wrapper.cancel();
                    abortedCount++;
                    Log.infof("Cancelled job for printer: %s", wrapper.job.getPrinterName());
                }
            }
        }
        
        // Remove cancelled jobs from queue
        currentBatchQueue.removeIf(PrinterJobWrapper::isCancelled);
        
        Log.infof("Aborted %d printer(s) from batch. Remaining in queue: %d", 
                 abortedCount, currentBatchQueue.size());
        
        // If queue is now empty, stop the batch
        if (currentBatchQueue.isEmpty() && isBatchRunning.get()) {
            Log.infof("Queue is empty after abort, stopping batch");
            isBatchRunning.set(false);
            activeBatchJobs.forEach(f -> {
                if (!f.isDone()) {
                    f.complete(null);
                }
            });
        }
        
        return abortedCount;
    }
    
    /**
     * Check if current batch is aborted
     */
    public boolean isBatchAborted() {
        return isBatchAborted.get();
    }
    
    /**
     * Get count of active jobs
     */
    public int getActiveJobCount() {
        return (int) activeBatchJobs.stream().filter(f -> !f.isDone()).count();
    }
    
    /**
     * Check if a batch is currently running
     */
    public boolean isBatchRunning() {
        return isBatchRunning.get();
    }
    
    /**
     * Get count of queued jobs waiting to be processed
     */
    public int getQueuedJobCount() {
        return currentBatchQueue.size();
    }
    
    /**
     * Send print jobs to multiple printers with a configurable delay between each batch
     * If a batch is already running, jobs are added to the existing queue
     * 
     * @param printerJobs List of printer jobs to send
     * @param customDelay Custom delay duration (optional, uses config default if null)
     * @param simultaneousPrinters Number of printers to start simultaneously (optional, uses config default if null)
     * @return CompletableFuture that completes when all jobs are queued
     */
    public CompletableFuture<Void> sendBatchJobsWithDelay(
            List<PrinterJob> printerJobs, 
            Duration customDelay,
            Integer simultaneousPrinters) {
        
        if (printerJobs == null || printerJobs.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        // Wrap jobs with unique IDs
        List<PrinterJobWrapper> wrappedJobs = printerJobs.stream()
            .map(job -> new PrinterJobWrapper(jobIdCounter.incrementAndGet(), job))
            .collect(java.util.stream.Collectors.toList());
        
        // If batch is already running, add jobs to existing queue
        if (isBatchRunning.get()) {
            Log.infof("Adding %d jobs to existing batch queue", wrappedJobs.size());
            currentBatchQueue.addAll(wrappedJobs);
            return CompletableFuture.completedFuture(null);
        }
        
        configuredDelay = customDelay != null ? customDelay : delayConfig.jobDelay();
        Duration delayToUse = configuredDelay;
        int batchSize = simultaneousPrinters != null ? simultaneousPrinters : delayConfig.simultaneousPrinters();
        
        // Ensure batch size is at least 1
        if (batchSize < 1) {
            batchSize = 1;
        }
        
        // Initialize the batch queue
        currentBatchQueue.clear();
        currentBatchQueue.addAll(wrappedJobs);
        isBatchRunning.set(true);
        
        boolean useDelay = delayConfig.enableDelay() && currentBatchQueue.size() > batchSize;
        
        CompletableFuture<Void> result = new CompletableFuture<>();
        activeBatchJobs.add(result);
        result.whenComplete((v, ex) -> {
            activeBatchJobs.remove(result);
            isBatchRunning.set(false);
            currentBatchQueue.clear();
        });
        
        AtomicInteger batchIndex = new AtomicInteger(0);
        
        Log.infof("Starting batch print jobs for %d printers with %s delay and %d simultaneous printers per batch", 
                  currentBatchQueue.size(), 
                  useDelay ? delayToUse.getSeconds() + "s" : "no",
                  batchSize);
        
        // Schedule the first batch immediately
        scheduleNextBatch(batchIndex, delayToUse, batchSize, useDelay, result);
        
        return result;
    }
    
    /**
     * Convenience method using only custom delay
     */
    public CompletableFuture<Void> sendBatchJobsWithDelay(
            List<PrinterJob> printerJobs, 
            Duration customDelay) {
        return sendBatchJobsWithDelay(printerJobs, customDelay, null);
    }
    
    private void scheduleNextBatch(
            AtomicInteger batchIndex,
            Duration delay,
            int batchSize,
            boolean useDelay,
            CompletableFuture<Void> result) {
        
        // Check if batch was aborted - CRITICAL CHECK
        if (isBatchAborted.get()) {
            Log.infof("Batch aborted detected in scheduleNextBatch, stopping all processing");
            if (!result.isDone()) {
                result.completeExceptionally(new RuntimeException("Batch aborted by user"));
            }
            return;
        }
        
        // Check if we should stop (no more jobs or aborted)
        if (currentBatchQueue.isEmpty()) {
            Log.infof("Batch queue is empty, completing batch");
            if (!result.isDone()) {
                result.complete(null);
            }
            return;
        }
        
        int currentBatchIndex = batchIndex.getAndIncrement();
        int startIndex = currentBatchIndex * batchSize;
        
        // Check against current queue size (which may have grown)
        if (startIndex >= currentBatchQueue.size()) {
            // All jobs scheduled
            Log.infof("All %d jobs processed, completing batch", currentBatchQueue.size());
            if (!result.isDone()) {
                result.complete(null);
            }
            return;
        }
        
        int endIndex = Math.min(startIndex + batchSize, currentBatchQueue.size());
        long delayMillis = (currentBatchIndex == 0 || !useDelay) ? 0 : delay.toMillis();
        
        scheduler.schedule(() -> {
            // Double-check abort status before processing
            if (isBatchAborted.get()) {
                Log.infof("Batch aborted detected before processing batch %d", currentBatchIndex + 1);
                if (!result.isDone()) {
                    result.completeExceptionally(new RuntimeException("Batch aborted by user"));
                }
                return;
            }
            
            try {
                Instant batchStartTime = Instant.now();
                
                // Get jobs from current queue (skip cancelled ones)
                List<PrinterJobWrapper> jobsInThisBatch = new java.util.ArrayList<>();
                for (int i = startIndex; i < endIndex; i++) {
                    if (i < currentBatchQueue.size()) {
                        PrinterJobWrapper wrapper = currentBatchQueue.get(i);
                        if (!wrapper.isCancelled()) {
                            jobsInThisBatch.add(wrapper);
                        } else {
                            Log.infof("Skipping cancelled job for printer: %s", wrapper.job.getPrinterName());
                        }
                    }
                }
                
                if (jobsInThisBatch.isEmpty()) {
                    Log.infof("No active jobs in batch %d, moving to next", currentBatchIndex + 1);
                    scheduleNextBatch(batchIndex, delay, batchSize, useDelay, result);
                    return;
                }
                
                // Start all printers in this batch simultaneously
                List<CompletableFuture<Void>> batchFutures = new java.util.ArrayList<>();
                
                for (int i = 0; i < jobsInThisBatch.size(); i++) {
                    final PrinterJobWrapper wrapper = jobsInThisBatch.get(i);
                    final int jobIndex = startIndex + i;
                    
                    // Execute each job in this batch asynchronously
                    CompletableFuture<Void> jobFuture = CompletableFuture.runAsync(() -> {
                        // Double-check not cancelled before starting
                        if (wrapper.isCancelled() || isBatchAborted.get()) {
                            Log.infof("Skipping job for printer %s - cancelled or aborted", wrapper.job.getPrinterName());
                            return;
                        }
                        
                        try {
                            Instant jobStartTime = Instant.now();
                            Log.infof("Starting upload for printer %s (%d/%d) in batch %d", 
                                     wrapper.job.getPrinterName(), 
                                     jobIndex + 1, 
                                     currentBatchQueue.size(),
                                     currentBatchIndex + 1);
                            
                            wrapper.job.execute();
                            
                            // Mark this printer as SENT - this starts the delay timer
                            markPrinterSent();
                            
                            Instant jobEndTime = Instant.now();
                            long uploadDuration = Duration.between(jobStartTime, jobEndTime).toSeconds();
                            
                            Log.infof("Printer %s marked as SENT after %ds", 
                                     wrapper.job.getPrinterName(), 
                                     uploadDuration);
                            
                        } catch (Exception e) {
                            Log.errorf(e, "Error sending job to printer %s", wrapper.job.getPrinterName());
                        }
                    }, scheduler);
                    
                    batchFutures.add(jobFuture);
                }
                
                // Wait for all jobs in this batch to complete
                CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0]))
                    .thenRun(() -> {
                        // Check abort status after batch completion
                        if (isBatchAborted.get()) {
                            Log.infof("Batch aborted after completing batch %d", currentBatchIndex + 1);
                            if (!result.isDone()) {
                                result.completeExceptionally(new RuntimeException("Batch aborted by user"));
                            }
                            return;
                        }
                        
                        Instant batchEndTime = Instant.now();
                        long batchDuration = Duration.between(batchStartTime, batchEndTime).toSeconds();
                        
                        int printersInBatch = jobsInThisBatch.size();
                        Log.infof("Batch %d completed: %d printer%s sent in %ds (Total queue: %d)", 
                                 currentBatchIndex + 1,
                                 printersInBatch,
                                 printersInBatch > 1 ? "s" : "",
                                 batchDuration,
                                 currentBatchQueue.size());
                        
                        if (useDelay && endIndex < currentBatchQueue.size()) {
                            Log.infof("Waiting %ds before next batch", delay.toSeconds());
                        }
                        
                        // Schedule the next batch (queue may have grown)
                        scheduleNextBatch(batchIndex, delay, batchSize, useDelay, result);
                    })
                    .exceptionally(ex -> {
                        Log.errorf(ex, "Error in batch %d", currentBatchIndex + 1);
                        // Continue with next batch even if this one has errors (unless aborted)
                        if (!isBatchAborted.get()) {
                            scheduleNextBatch(batchIndex, delay, batchSize, useDelay, result);
                        }
                        return null;
                    });
                
            } catch (Exception e) {
                Log.errorf(e, "Error scheduling batch %d", currentBatchIndex + 1);
                // Continue with next batch even if this one fails (unless aborted)
                if (!isBatchAborted.get()) {
                    scheduleNextBatch(batchIndex, delay, batchSize, useDelay, result);
                }
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Represents a print job for a specific printer
     */
    public interface PrinterJob {
        String getPrinterName();
        void execute() throws Exception;
    }
    
    /**
     * Shutdown the scheduler gracefully
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
