package com.medisphere.service;

import com.medisphere.repository.PatientRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates sequential, formatted patient IDs: P001, P002, P003 …
 *
 * On first call the generator reads all existing IDs from MongoDB to determine
 * the current maximum, then increments atomically in memory for subsequent
 * calls within the same JVM lifetime.
 *
 * Note: this is safe for single-instance deployments (Phase 1–8).
 * A distributed counter (e.g. MongoDB atomic findAndModify on a counters
 * collection) can be substituted in Phase 8 without changing callers.
 */
@Component
public class PatientIdGenerator {

    private static final Pattern ID_PATTERN = Pattern.compile("^P(\\d+)$");
    private static final String PREFIX = "P";
    private static final int PAD_WIDTH = 3;

    private final PatientRepository patientRepository;
    private final AtomicInteger counter;
    private volatile boolean initialized = false;

    public PatientIdGenerator(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
        this.counter = new AtomicInteger(0);
    }

    /**
     * Return the next patient ID (e.g. "P007") without persisting it.
     * Thread-safe; safe to call repeatedly for preview purposes.
     */
    public String preview() {
        ensureInitialized();
        return format(counter.get() + 1);
    }

    /**
     * Claim and return the next patient ID.
     * Increments the internal counter atomically.
     */
    public String next() {
        ensureInitialized();
        return format(counter.incrementAndGet());
    }

    // ── Private ──────────────────────────────────────────────────────────

    private void ensureInitialized() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    int max = patientRepository.findAllIds().stream()
                            .map(p -> p.getId())
                            .filter(id -> id != null)
                            .map(ID_PATTERN::matcher)
                            .filter(Matcher::matches)
                            .mapToInt(m -> Integer.parseInt(m.group(1)))
                            .max()
                            .orElse(0);
                    counter.set(max);
                    initialized = true;
                }
            }
        }
    }

    private String format(int n) {
        return PREFIX + String.format("%0" + PAD_WIDTH + "d", n);
    }
}
