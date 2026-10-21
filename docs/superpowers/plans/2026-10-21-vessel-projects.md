# Saved Vessel Projects Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Save installation water scenarios, compare against a baseline and share complete calculation records.
**Architecture:** Foundation value models and atomic versioned archive; SwiftUI store/list/detail/editor calling the unchanged MAGCalculator.
**Tech Stack:** Swift, Foundation, SwiftUI, existing Xcode project.
**Spec:** docs/superpowers/specs/2026-10-21-vessel-projects-design.md

## Global Constraints
- Preserve de.kamilunavo.magcalc, existing formulas and Pro gating.
- No native build/test workload outside root's serial build grants.
- No store action, credentials, transaction, push or merge in this task.
- DE/EN, responsive forms, no invented Duo support claim.

## Review Focus
- Nonfinite/negative/out-of-table input rejects saving.
- Missing baseline or duplicate IDs rejects malformed imports.
- Unknown/corrupt archive stays intact on disk.
- Failed save keeps prior file; existing bare-array schema loads.
- Baseline deletion chooses a remaining scenario; last scenario cannot disappear.

### Task 1: Worksheet domain/archive
- [x] Write Tests/main.swift assertions for defaults, unchanged known formula output, invalid inputs, duplication/baseline mutation, report contents, JSON round-trip/migration/corruption/unknown-version protection.
- [x] Root executes `bash scripts/test-projects.sh`, observe missing feature RED.
- [x] Implement MAGCalc/VesselProjects.swift: VesselScenario, VesselProject, VesselProjectArchive, VesselReport, atomic save and strict validation.
- [x] Root repeats full suite; require GREEN.

### Task 2: Native workflow
- [x] Add ProjectStore and ProjectsView list/detail/form with create/edit/duplicate/delete/baseline/share; error-safe saves and localized copy.
- [x] Register new Swift files in current pbxproj without regeneration; add home navigation.
- [ ] Root verifies native compile and UI/Duo; source handoff explicitly lists pending evidence.
- [ ] Save per-app ledger, metadata proposal and handoff; commit reviewed files without push.

### Task 3: Android one-to-one parity (renewed explicit human scope via root)
- [ ] Write VesselProjectsTest.kt for known Swift-equivalent defaults, invalid inputs, baseline/duplicate/delete, DE/EN report and JSON corruption/migration protection.
- [ ] Root executes serial Gradle testDebugUnitTest, observe missing Kotlin worksheet RED.
- [ ] Implement Kotlin VesselProjects domain/archive/report using existing unchanged MAGCalculator; JVM org.json is test-only dependency.
- [ ] Implement native Compose project/list/detail/editor with local atomic JSON, notes, baseline/delta, confirm deletion and text share; preserve original screens/IAP.
- [ ] Root executes full Gradle tests + native debug build; record results and outstanding native parity acceptance.
