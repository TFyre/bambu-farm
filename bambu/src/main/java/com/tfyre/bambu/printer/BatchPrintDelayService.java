package com.tfyre.bambu.printer;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
    
    @Inject
    BatchPrintDelayConfig delayConfig;
    
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
    
    /**
     * Send print jobs to multiple printers with a configurable delay between each batch
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
        
        configuredDelay = customDelay != null ? customDelay : delayConfig.jobDelay();
        Duration delayToUse = configuredDelay;
        int batchSize = simultaneousPrinters != null ? simultaneousPrinters : delayConfig.simultaneousPrinters();
        boolean useDelay = delayConfig.enableDelay() && printerJobs.size() > batchSize;
        
        // Ensure batch size is at least 1
        if (batchSize < 1) {
            batchSize = 1;
        }
        
        CompletableFuture<Void> result = new CompletableFuture<>();
        AtomicInteger batchIndex = new AtomicInteger(0);
        
        Log.infof("Starting batch print jobs for %d printers with %s delay and %d simultaneous printers per batch", 
                  printerJobs.size(), 
                  useDelay ? delayToUse.getSeconds() + "s" : "no",
                  batchSize);
        
        // Schedule the first batch immediately
        scheduleNextBatch(printerJobs, batchIndex, delayToUse, batchSize, useDelay, result);
        
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
            List<PrinterJob> printerJobs,
            AtomicInteger batchIndex,
            Duration delay,
            int batchSize,
            boolean useDelay,
            CompletableFuture<Void> result) {
        
        int currentBatchIndex = batchIndex.getAndIncrement();
        int startIndex = currentBatchIndex * batchSize;
        
        if (startIndex >= printerJobs.size()) {
            // All jobs scheduled
            result.complete(null);
            return;
        }
        
        int endIndex = Math.min(startIndex + batchSize, printerJobs.size());
        long delayMillis = (currentBatchIndex == 0 || !useDelay) ? 0 : delay.toMillis();
        
        scheduler.schedule(() -> {
            try {
                Instant batchStartTime = Instant.now();
                
                // Start all printers in this batch simultaneously
                List<CompletableFuture<Void>> batchFutures = new java.util.ArrayList<>();
                
                for (int i = startIndex; i < endIndex; i++) {
                    final int jobIndex = i;
                    final PrinterJob job = printerJobs.get(i);
                    
                    // Execute each job in this batch asynchronously
                    CompletableFuture<Void> jobFuture = CompletableFuture.runAsync(() -> {
                        try {
                            Instant jobStartTime = Instant.now();
                            Log.infof("Starting upload for printer %s (%d/%d) in batch %d", 
                                     job.getPrinterName(), 
                                     jobIndex + 1, 
                                     printerJobs.size(),
                                     currentBatchIndex + 1);
                            
                            job.execute();
                            
                            // Mark this printer as SENT - this starts the delay timer
                            markPrinterSent();
                            
                            Instant jobEndTime = Instant.now();
                            long uploadDuration = Duration.between(jobStartTime, jobEndTime).toSeconds();
                            
                            Log.infof("Printer %s marked as SENT after %ds", 
                                     job.getPrinterName(), 
                                     uploadDuration);
                            
                        } catch (Exception e) {
                            Log.errorf(e, "Error sending job to printer %s", job.getPrinterName());
                        }
                    }, scheduler);
                    
                    batchFutures.add(jobFuture);
                }
                
                // Wait for all jobs in this batch to complete
                CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0]))
                    .thenRun(() -> {
                        Instant batchEndTime = Instant.now();
                        long batchDuration = Duration.between(batchStartTime, batchEndTime).toSeconds();
                        
                        int printersInBatch = endIndex - startIndex;
                        Log.infof("Batch %d completed: %d printer%s sent in %ds", 
                                 currentBatchIndex + 1,
                                 printersInBatch,
                                 printersInBatch > 1 ? "s" : "",
                                 batchDuration);
                        
                        if (useDelay && endIndex < printerJobs.size()) {
                            Log.infof("Print button will be disabled for %ds before next batch", delay.toSeconds());
                        }
                        
                        // Schedule the next batch
                        scheduleNextBatch(printerJobs, batchIndex, delay, batchSize, useDelay, result);
                    })
                    .exceptionally(ex -> {
                        Log.errorf(ex, "Error in batch %d", currentBatchIndex + 1);
                        // Continue with next batch even if this one has errors
                        scheduleNextBatch(printerJobs, batchIndex, delay, batchSize, useDelay, result);
                        return null;
                    });
                
            } catch (Exception e) {
                Log.errorf(e, "Error scheduling batch %d", currentBatchIndex + 1);
                // Continue with next batch even if this one fails
                scheduleNextBatch(printerJobs, batchIndex, delay, batchSize, useDelay, result);
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