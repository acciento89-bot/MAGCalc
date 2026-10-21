# Saved vessel-sizing projects

Approved human intent: make the existing published MAGCalc substantially more useful in professional installation comparisons, preserve its identity/formulas/Pro entitlement, and prepare native Duo validation. Routine design decisions are explicitly authorized while the human is away.

Architecture: add a Foundation worksheet model and versioned atomic JSON archive plus native SwiftUI project list/detail/editor. Each named installation has at least one named water scenario, baseline selected by ID, installation notes and per-scenario source notes. Existing heating calculation is the sole engine. Manual-media calculations remain behind the existing Pro gate; the new water worksheet does not duplicate that feature.

Alternatives: ephemeral history lacks coherent comparisons; a new universal fluid database risks unjustified engineering data. Choose explicit saved water worksheets using the existing water density table (10–120°C), editable pressure/volume/height values and sources. Existing calculator behavior and settings remain untouched.

Users create, rename, delete and reopen projects, duplicate/edit/delete scenarios, set the baseline, review all inputs/results and signed differences, and share a plain-text complete calculation record. Invalid, nonfinite, negative and out-of-table inputs cannot be saved as valid results. Records identify the engine and limits; no engineering certification is implied. Data is on-device only. No prior project store exists; missing file starts empty; legacy bare project arrays migrate on next save. Unknown/corrupt archives cause a visible error and are never silently overwritten. Save errors retain current UI and previous disk contents.

UI uses native lists/forms/navigation, flexible vertical metric layouts, scroll and keyboard avoidance; no screen-size or device-model assumptions. DE/EN localization includes exports. Actual Duo/native acceptance is pending root execution.
