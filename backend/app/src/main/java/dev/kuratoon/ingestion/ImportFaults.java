package dev.kuratoon.ingestion;

import org.springframework.stereotype.Component;

/**
 * Named points in the commit order (docs/spec/02-ingestion.md "Commit order"). Production does
 * nothing at them; crash-safety tests replace this bean to stop the pipeline at a given point, as
 * if the process had been killed there.
 */
public interface ImportFaults {

    enum Step {
        AFTER_COPY,
        AFTER_VALIDATE,
        /** Inside the commit transaction, after all rows are written, before it commits. */
        IN_TRANSACTION,
        AFTER_COMMIT,
        /** UPDATE: after the first chapter folder was moved into place. */
        MID_MOVE,
        /** Replace: after the old chapter folder was moved to the backup, before the new one is in. */
        MID_REPLACE_SWAP,
        AFTER_MOVE,
        AFTER_THUMBNAILS,
        AFTER_SIDECAR
    }

    void checkpoint(Step step);

    @Component
    class None implements ImportFaults {
        @Override
        public void checkpoint(Step step) {}
    }
}
