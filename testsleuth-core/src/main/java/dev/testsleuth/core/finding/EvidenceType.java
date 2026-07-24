package dev.testsleuth.core.finding;

/** Describes how strongly a finding is grounded in runtime evidence. */
public enum EvidenceType {
    MEASURED,
    CORRELATED,
    INFERRED,
    POTENTIAL
}
